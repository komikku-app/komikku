package exh.favorites

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toast
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.eh.EHentaiThrottleManager
import exh.eh.EHentaiUpdateWorker
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
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.interactor.UpdateCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
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

    private val prefs: UnsortedPreferences by injectLazy()

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
            status.value = FavoritesSyncStatus.Error(context.stringResource(SYMR.strings.please_login))
            return
        }

        // Validate library state
        status.value =
            FavoritesSyncStatus.Processing(context.stringResource(SYMR.strings.favorites_sync_verifying_library))
        val libraryManga = getLibraryManga.await()
        val seenManga = HashSet<Long>(libraryManga.size)
        libraryManga.forEach { (manga) ->
            if (!manga.isEhBasedManga()) return@forEach

            if (manga.id in seenManga) {
                val inCategories = getCategories.await(manga.id)
                status.value = FavoritesSyncStatus.BadLibraryState
                    .MangaInMultipleCategories(manga, inCategories, context)

                logger.w(context.stringResource(SYMR.strings.favorites_sync_gallery_multiple_categories_error, manga.id))
                return
            } else {
                seenManga += manga.id
            }
        }

        // Download remote favorites
        val favorites = try {
            status.value =
                FavoritesSyncStatus.Processing(context.stringResource(SYMR.strings.favorites_sync_downloading))
            exh.fetchFavorites()
        } catch (e: Exception) {
            status.value =
                FavoritesSyncStatus.Error(context.stringResource(SYMR.strings.favorites_sync_failed_to_featch))
            logger.e(context.stringResource(SYMR.strings.favorites_sync_could_not_fetch), e)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.wifiManager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                        "teh:ExhFavoritesSyncWifi",
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.wifiManager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        "teh:ExhFavoritesSyncWifi",
                    )
                }
            }

            // Do not update galleries while syncing favorites
            EHentaiUpdateWorker.cancelBackground(context)

            status.value = FavoritesSyncStatus.Processing(
                context.stringResource(SYMR.strings.favorites_sync_calculating_remote_changes),
            )
            val remoteChanges = storage.getChangedRemoteEntries(favorites.first)
            val localChanges = if (prefs.exhReadOnlySync().get()) {
                null // Do not build local changes if they are not going to be applied
            } else {
                status.value = FavoritesSyncStatus.Processing(
                    context.stringResource(SYMR.strings.favorites_sync_calculating_local_changes),
                )
                storage.getChangedDbEntries()
            }

            // Apply remote categories
            status.value = FavoritesSyncStatus.Processing(
                context.stringResource(SYMR.strings.favorites_sync_syncing_category_names),
            )
            applyRemoteCategories(favorites.second)

            // Apply change sets
            applyChangeSetToLocal(errorList, remoteChanges)
            if (localChanges != null) {
                applyChangeSetToRemote(errorList, localChanges)
            }

            status.value =
                FavoritesSyncStatus.Processing(context.stringResource(SYMR.strings.favorites_sync_cleaning_up))
            storage.snapshotEntries()

            withUIContext {
                context.toast(SYMR.strings.favorites_sync_complete)
            }
        } catch (e: IgnoredException) {
            // Do not display error as this error has already been reported
            logger.w(context.stringResource(SYMR.strings.favorites_sync_ignoring_exception), e)
            return
        } catch (e: Exception) {
            status.value = FavoritesSyncStatus.Error(
                context.stringResource(SYMR.strings.favorites_sync_unknown_error, e.message.orEmpty()),
            )
            logger.e(context.stringResource(SYMR.strings.favorites_sync_sync_error), e)
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
            .filterNot(Category::isSystemCategory)

        categories.forEachIndexed { index, remote ->
            val local = localCategories.getOrElse(index) {
                when (val createCategoryWithNameResult = createCategoryWithName.await(remote)) {
                    is CreateCategoryWithName.Result.InternalError -> throw createCategoryWithNameResult.error
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
                throw IgnoredException(errorString)
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
                logger.w(context.stringResource(SYMR.strings.favorites_sync_network_error), e)
            }
        }

        return success
    }

    private suspend fun applyChangeSetToRemote(errorList: MutableList<String>, changeSet: ChangeSet) {
        // Apply removals
        if (changeSet.removed.isNotEmpty()) {
            status.value = FavoritesSyncStatus.Processing(
                context.stringResource(SYMR.strings.favorites_sync_removing_galleries, changeSet.removed.size),
            )

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
                val errorString = context.stringResource(SYMR.strings.favorites_sync_unable_to_delete)

                if (prefs.exhLenientSync().get()) {
                    errorList += errorString
                } else {
                    status.value = FavoritesSyncStatus.Error(errorString)
                    throw IgnoredException(errorString)
                }
            }
        }

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing(
                message = context.stringResource(SYMR.strings.favorites_sync_adding_to_remote, index + 1, changeSet.added.size),
                isThrottle = needWarnThrottle(),
                context = context,
                title = it.title,
            )

            throttleManager.throttle()

            addGalleryRemote(errorList, it)
        }
    }

    private suspend fun applyChangeSetToLocal(errorList: MutableList<String>, changeSet: ChangeSet) {
        val removedManga = mutableListOf<Manga>()

        // Apply removals
        changeSet.removed.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing(
                context.stringResource(SYMR.strings.favorites_sync_remove_from_local, index + 1, changeSet.removed.size),
                title = it.title,
            )
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
            .filterNot(Category::isSystemCategory)

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.value = FavoritesSyncStatus.Processing(
                message = context.stringResource(SYMR.strings.favorites_sync_add_to_local, index + 1, changeSet.added.size),
                isThrottle = needWarnThrottle(),
                context = context,
                title = it.title,
            )

            throttleManager.throttle()

            // Import using gallery adder
            val result = galleryAdder.addGallery(
                context = context,
                url = "${exh.baseUrl}${it.getUrl()}",
                fav = true,
                forceSource = exh,
                throttleFunc = throttleManager::throttle,
                retry = 3,
            )

            if (result is GalleryAddEvent.Fail) {
                if (result is GalleryAddEvent.Fail.NotFound) {
                    logger.e(context.stringResource(SYMR.strings.favorites_sync_remote_not_exist, it.getUrl()))
                    // Skip this gallery, it no longer exists
                    return@forEachIndexed
                }

                val errorString = context.stringResource(SYMR.strings.favorites_sync_failed_to_add_to_local) +
                    when (result) {
                        is GalleryAddEvent.Fail.Error -> context.stringResource(
                            SYMR.strings.favorites_sync_failed_to_add_to_local_error, it.title, result.logMessage,
                        )
                        is GalleryAddEvent.Fail.UnknownType -> context.stringResource(
                            SYMR.strings.favorites_sync_failed_to_add_to_local_unknown_type, it.title, result.galleryUrl,
                        )
                        is GalleryAddEvent.Fail.UnknownSource -> context.stringResource(
                            SYMR.strings.favorites_sync_failed_to_add_to_local_unknown_type, it.title, result.galleryUrl,
                        )
                    }

                if (prefs.exhLenientSync().get()) {
                    errorList += errorString
                } else {
                    status.value = FavoritesSyncStatus.Error(errorString)
                    throw IgnoredException(errorString)
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

    class IgnoredException(message: String) : RuntimeException(message)

    companion object {
        private val THROTTLE_WARN = 1.seconds
    }
}

sealed class FavoritesSyncStatus {
    abstract val message: String

    data class Error(override val message: String) : FavoritesSyncStatus()
    data class Idle(override val message: String) : FavoritesSyncStatus() {
        constructor(context: Context) : this(context.stringResource(SYMR.strings.favorites_sync_waiting_for_start))
    }
    sealed class BadLibraryState : FavoritesSyncStatus() {
        data class MangaInMultipleCategories(
            val manga: Manga,
            val categories: List<Category>,
            override val message: String,
        ) : BadLibraryState() {
            constructor(manga: Manga, categories: List<Category>, context: Context) :
                this(
                    manga = manga,
                    categories = categories,
                    message = context.stringResource(
                        SYMR.strings.favorites_sync_gallery_in_multiple_categories, manga.title,
                        categories.joinToString {
                            it.name
                        },
                    ),
                )
        }
    }
    data class Initializing(override val message: String) : FavoritesSyncStatus() {
        constructor(context: Context) : this(context.stringResource(SYMR.strings.favorites_sync_initializing))
    }
    data class Processing(
        override val message: String,
        val title: String? = null,
    ) : FavoritesSyncStatus() {
        constructor(message: String, isThrottle: Boolean, context: Context, title: String?) :
            this(
                if (isThrottle) {
                    context.stringResource(SYMR.strings.favorites_sync_processing_throttle, message)
                } else {
                    message
                },
                title,
            )

        val delayedMessage get() = if (title != null) this.message + "\n\n" + title else null
    }
    data class CompleteWithErrors(val messages: List<String>) : FavoritesSyncStatus() {
        override val message: String = messages.joinToString("\n")
    }
}
