package exh.favorites

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import eu.kanade.domain.category.interactor.CreateCategoryWithName
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.interactor.UpdateCategory
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toast
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.eh.EHentaiThrottleManager
import exh.eh.EHentaiUpdateWorker
import exh.favorites.sql.models.FavoriteEntry
import exh.log.xLog
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.source.isEhBasedManga
import exh.util.ignore
import exh.util.wifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.seconds

// TODO only apply database changes after sync
class FavoritesSyncHelper(val context: Context) {
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val setMangaCategories: SetMangaCategories by injectLazy()
    private val createCategoryWithName: CreateCategoryWithName by injectLazy()
    private val updateCategory: UpdateCategory by injectLazy()

    private val prefs: PreferencesHelper by injectLazy()

    private val exh by lazy {
        Injekt.get<SourceManager>().get(EXH_SOURCE_ID) as? EHentai
            ?: EHentai(0, true, context)
    }

    private val storage = LocalFavoritesStorage()

    private val galleryAdder = GalleryAdder()

    private val throttleManager = EHentaiThrottleManager()

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val logger = xLog()

    val status: MutableStateFlow<FavoritesSyncStatus> = MutableStateFlow(FavoritesSyncStatus.Idle(context))

    @Synchronized
    fun runSync(scope: CoroutineScope) {
        if (status.value !is FavoritesSyncStatus.Idle) {
            return
        }

        status.value = FavoritesSyncStatus.Initializing(context)

        scope.launch(Dispatchers.IO) { beginSync() }
    }

    private suspend fun beginSync() {
        // Check if logged in
        if (!prefs.enableExhentai().get()) {
            status.value = FavoritesSyncStatus.Error(context.getString(R.string.please_login))
            return
        }

        // Validate library state
        status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_verifying_library), context = context)
        val libraryManga = getLibraryManga.await()
        val seenManga = HashSet<Long>(libraryManga.size)
        libraryManga.forEach {
            if (!it.isEhBasedManga()) return@forEach

            if (it.id in seenManga) {
                val inCategories = getCategories.await(it.id!!)
                status.value = FavoritesSyncStatus.BadLibraryState.MangaInMultipleCategories(it.toDomainManga()!!, inCategories, context)

                logger.w(context.getString(R.string.favorites_sync_manga_multiple_categories_error, it.id))
                return
            } else {
                seenManga += it.id!!
            }
        }

        // Download remote favorites
        val favorites = try {
            status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_downloading), context = context)
            exh.fetchFavorites()
        } catch (e: Exception) {
            status.value = FavoritesSyncStatus.Error(context.getString(R.string.favorites_sync_failed_to_featch))
            logger.e(context.getString(R.string.favorites_sync_could_not_fetch), e)
            return
        }

        val errorList = mutableListOf<String>()

        try {
            // Take wake + wifi locks
            ignore { wakeLock?.release() }
            wakeLock = ignore {
                context.powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "teh:ExhFavoritesSyncWakelock",
                )
            }
            ignore { wifiLock?.release() }
            wifiLock = ignore {
                context.wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "teh:ExhFavoritesSyncWifi",
                )
            }

            // Do not update galleries while syncing favorites
            EHentaiUpdateWorker.cancelBackground(context)

            status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_calculating_remote_changes), context = context)
            val remoteChanges = storage.getChangedRemoteEntries(favorites.first)
            val localChanges = if (prefs.exhReadOnlySync().get()) {
                null // Do not build local changes if they are not going to be applied
            } else {
                status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_calculating_local_changes), context = context)
                storage.getChangedDbEntries()
            }

            // Apply remote categories
            status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_syncing_category_names), context = context)
            applyRemoteCategories(favorites.second)

            // Apply change sets
            applyChangeSetToLocal(errorList, remoteChanges)
            if (localChanges != null) {
                applyChangeSetToRemote(errorList, localChanges)
            }

            status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_cleaning_up), context = context)
            storage.snapshotEntries()

            withUIContext {
                context.toast(R.string.favorites_sync_complete)
            }
        } catch (e: IgnoredException) {
            // Do not display error as this error has already been reported
            logger.w(context.getString(R.string.favorites_sync_ignoring_exception), e)
            return
        } catch (e: Exception) {
            status.value = FavoritesSyncStatus.Error(context.getString(R.string.favorites_sync_unknown_error, e.message))
            logger.e(context.getString(R.string.favorites_sync_sync_error), e)
            return
        } finally {
            // Release wake + wifi locks
            ignore {
                wakeLock?.release()
                wakeLock = null
            }
            ignore {
                wifiLock?.release()
                wifiLock = null
            }

            // Update galleries again!
            EHentaiUpdateWorker.scheduleBackground(context)
        }

        if (errorList.isEmpty()) {
            status.value = FavoritesSyncStatus.Idle(context)
        } else {
            status.value = FavoritesSyncStatus.CompleteWithErrors(errorList)
        }
    }

    private suspend fun applyRemoteCategories(categories: List<String>) {
        val localCategories = getCategories.await()

        categories.forEachIndexed { index, remote ->
            val local = localCategories.getOrElse(index) {
                when (val createCategoryWithNameResult = createCategoryWithName.await(remote)) {
                    is CreateCategoryWithName.Result.InternalError -> throw createCategoryWithNameResult.error
                    CreateCategoryWithName.Result.NameAlreadyExistsError -> throw IllegalStateException("Category $remote already exists")
                    is CreateCategoryWithName.Result.Success -> createCategoryWithNameResult.category
                }
            }

            // Ensure consistent ordering and naming
            if (local.name != remote || local.order != index.toLong()) {
                val result = updateCategory.await(
                    CategoryUpdate(
                        id = local.id,
                        order = index.toLong().takeIf { it != local.order },
                        name = remote.takeIf { it != local.name },
                    ),
                )
                if (result is UpdateCategory.Result.Error) {
                    throw result.error
                }
            }
        }
    }

    private suspend fun addGalleryRemote(errorList: MutableList<String>, gallery: FavoriteEntry) {
        val url = "${exh.baseUrl}/gallerypopups.php?gid=${gallery.gid}&t=${gallery.token}&act=addfav"

        val request = POST(
            url = url,
            body = FormBody.Builder()
                .add("favcat", gallery.category.toString())
                .add("favnote", "")
                .add("apply", "Add to Favorites")
                .add("update", "1")
                .build(),
        )

        if (!explicitlyRetryExhRequest(10, request)) {
            val errorString = "Unable to add gallery to remote server: '${gallery.title}' (GID: ${gallery.gid})!"

            if (prefs.exhLenientSync().get()) {
                errorList += errorString
            } else {
                status.value = FavoritesSyncStatus.Error(errorString)
                throw IgnoredException()
            }
        }
    }

    private suspend fun explicitlyRetryExhRequest(retryCount: Int, request: Request): Boolean {
        var success = false

        for (i in 1..retryCount) {
            try {
                val resp = withIOContext { exh.client.newCall(request).await() }

                if (resp.isSuccessful) {
                    success = true
                    break
                }
            } catch (e: Exception) {
                logger.w(context.getString(R.string.favorites_sync_network_error), e)
            }
        }

        return success
    }

    private suspend fun applyChangeSetToRemote(errorList: MutableList<String>, changeSet: ChangeSet) {
        // Apply removals
        if (changeSet.removed.isNotEmpty()) {
            status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_removing_galleries, changeSet.removed.size), context = context)

            val formBody = FormBody.Builder()
                .add("ddact", "delete")
                .add("apply", "Apply")

            // Add change set to form
            changeSet.removed.forEach {
                formBody.add("modifygids[]", it.gid)
            }

            val request = POST(
                url = "https://exhentai.org/favorites.php",
                body = formBody.build(),
            )

            if (!explicitlyRetryExhRequest(10, request)) {
                val errorString = context.getString(R.string.favorites_sync_unable_to_delete)

                if (prefs.exhLenientSync().get()) {
                    errorList += errorString
                } else {
                    status.value = FavoritesSyncStatus.Error(errorString)
                    throw IgnoredException()
                }
            }
        }

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing(
                context.getString(R.string.favorites_sync_adding_to_remote, index + 1, changeSet.added.size),
                needWarnThrottle(),
                context,
            )

            throttleManager.throttle()

            addGalleryRemote(errorList, it)
        }
    }

    private suspend fun applyChangeSetToLocal(errorList: MutableList<String>, changeSet: ChangeSet) {
        val removedManga = mutableListOf<Manga>()

        // Apply removals
        changeSet.removed.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_remove_from_local, index + 1, changeSet.removed.size), context = context)
            val url = it.getUrl()

            // Consider both EX and EH sources
            listOf(
                EXH_SOURCE_ID,
                EH_SOURCE_ID,
            ).forEach {
                val manga = getManga.await(url, it)

                if (manga?.favorite == true) {
                    updateManga.awaitUpdateFavorite(manga.id, false)
                    removedManga += manga
                }
            }
        }

        // Can't do too many DB OPs in one go
        removedManga.forEach {
            setMangaCategories.await(it.id, emptyList())
        }

        val insertedMangaCategories = mutableListOf<Pair<Long, Manga>>()
        val categories = getCategories.await()

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing(
                context.getString(R.string.favorites_sync_add_to_local, index + 1, changeSet.added.size),
                needWarnThrottle(),
                context,
                it.title,
            )

            throttleManager.throttle()

            // Import using gallery adder
            val result = galleryAdder.addGallery(
                context,
                "${exh.baseUrl}${it.getUrl()}",
                true,
                exh,
                throttleManager::throttle,
            )

            if (result is GalleryAddEvent.Fail) {
                if (result is GalleryAddEvent.Fail.NotFound) {
                    logger.e(context.getString(R.string.favorites_sync_remote_not_exist, it.getUrl()))
                    // Skip this gallery, it no longer exists
                    return@forEachIndexed
                }

                val errorString = context.getString(R.string.favorites_sync_failed_to_add_to_local) + when (result) {
                    is GalleryAddEvent.Fail.Error -> context.getString(R.string.favorites_sync_failed_to_add_to_local_error, it.title, result.logMessage)
                    is GalleryAddEvent.Fail.UnknownType -> context.getString(R.string.favorites_sync_failed_to_add_to_local_unknown_type, it.title, result.galleryUrl)
                    is GalleryAddEvent.Fail.UnknownSource -> context.getString(R.string.favorites_sync_failed_to_add_to_local_unknown_type, it.title, result.galleryUrl)
                }

                if (prefs.exhLenientSync().get()) {
                    errorList += errorString
                } else {
                    status.value = FavoritesSyncStatus.Error(errorString)
                    throw IgnoredException()
                }
            } else if (result is GalleryAddEvent.Success) {
                insertedMangaCategories += categories[it.category].id to result.manga
            }
        }

        // Can't do too many DB OPs in one go
        insertedMangaCategories.forEach { (category, manga) ->
            setMangaCategories.await(manga.id, listOf(category))
        }
    }

    private fun needWarnThrottle() =
        throttleManager.throttleTime >= THROTTLE_WARN

    class IgnoredException : RuntimeException()

    companion object {
        private val THROTTLE_WARN = 1.seconds
    }
}

sealed class FavoritesSyncStatus(val message: String) {
    class Error(message: String) : FavoritesSyncStatus(message)
    class Idle(context: Context) : FavoritesSyncStatus(context.getString(R.string.favorites_sync_waiting_for_start))
    sealed class BadLibraryState(message: String) : FavoritesSyncStatus(message) {
        class MangaInMultipleCategories(
            val manga: Manga,
            val categories: List<Category>,
            context: Context,
        ) :
            BadLibraryState(context.getString(R.string.favorites_sync_manga_in_multiple_categories, manga.title, categories.joinToString { it.name }))
    }
    class Initializing(context: Context) : FavoritesSyncStatus(context.getString(R.string.favorites_sync_initializing))
    class Processing(message: String, isThrottle: Boolean = false, context: Context, val title: String? = null) : FavoritesSyncStatus(
        if (isThrottle) {
            context.getString(R.string.favorites_sync_processing_throttle, message)
        } else {
            message
        },
    ) {
        val delayedMessage get() = if (title != null) this.message + "\n\n" + title else null
    }
    class CompleteWithErrors(messages: List<String>) : FavoritesSyncStatus(messages.joinToString("\n"))
}
