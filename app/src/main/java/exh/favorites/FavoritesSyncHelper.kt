package exh.favorites

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toast
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.eh.EHentaiThrottleManager
import exh.eh.EHentaiUpdateWorker
import exh.util.ignore
import exh.util.trans
import exh.util.wifiManager
import okhttp3.FormBody
import okhttp3.Request
import rx.subjects.BehaviorSubject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

class FavoritesSyncHelper(val context: Context) {
    private val db: DatabaseHelper by injectLazy()

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

    private val logger = XLog.tag("EHFavSync").build()

    val status: BehaviorSubject<FavoritesSyncStatus> = BehaviorSubject.create<FavoritesSyncStatus>(FavoritesSyncStatus.Idle(context))

    @Synchronized
    fun runSync() {
        if (status.value !is FavoritesSyncStatus.Idle) {
            return
        }

        status.onNext(FavoritesSyncStatus.Initializing(context))

        thread { beginSync() }
    }

    private fun beginSync() {
        // Check if logged in
        if (!prefs.enableExhentai().get()) {
            status.onNext(FavoritesSyncStatus.Error(context.getString(R.string.please_login)))
            return
        }

        // Validate library state
        status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_verifying_library), context = context))
        val libraryManga = db.getLibraryMangas().executeAsBlocking()
        val seenManga = HashSet<Long>(libraryManga.size)
        libraryManga.forEach {
            if (it.source != EXH_SOURCE_ID && it.source != EH_SOURCE_ID) return@forEach

            if (it.id in seenManga) {
                val inCategories = db.getCategoriesForManga(it).executeAsBlocking()
                status.onNext(
                    FavoritesSyncStatus.BadLibraryState
                        .MangaInMultipleCategories(it, inCategories, context)
                )
                logger.w(context.getString(R.string.favorites_sync_manga_multiple_categories_error, it.id))
                return
            } else {
                seenManga += it.id!!
            }
        }

        // Download remote favorites
        val favorites = try {
            status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_downloading), context = context))
            exh.fetchFavorites()
        } catch (e: Exception) {
            status.onNext(FavoritesSyncStatus.Error(context.getString(R.string.favorites_sync_failed_to_featch)))
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
                    "teh:ExhFavoritesSyncWakelock"
                )
            }
            ignore { wifiLock?.release() }
            wifiLock = ignore {
                context.wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "teh:ExhFavoritesSyncWifi"
                )
            }

            // Do not update galleries while syncing favorites
            EHentaiUpdateWorker.cancelBackground(context)

            storage.getRealm().use { realm ->
                realm.trans {
                    db.inTransaction {
                        status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_calculating_remote_changes), context = context))
                        val remoteChanges = storage.getChangedRemoteEntries(realm, favorites.first)
                        val localChanges = if (prefs.eh_readOnlySync().get()) {
                            null // Do not build local changes if they are not going to be applied
                        } else {
                            status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_calculating_local_changes), context = context))
                            storage.getChangedDbEntries(realm)
                        }

                        // Apply remote categories
                        status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_syncing_category_names), context = context))
                        applyRemoteCategories(favorites.second)

                        // Apply change sets
                        applyChangeSetToLocal(errorList, remoteChanges)
                        if (localChanges != null) {
                            applyChangeSetToRemote(errorList, localChanges)
                        }

                        status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_cleaning_up), context = context))
                        storage.snapshotEntries(realm)
                    }
                }
            }

            val theContext = context
            launchUI {
                theContext.toast(context.getString(R.string.favorites_sync_complete))
            }
        } catch (e: IgnoredException) {
            // Do not display error as this error has already been reported
            logger.w(context.getString(R.string.favorites_sync_ignoring_exception), e)
            return
        } catch (e: Exception) {
            status.onNext(FavoritesSyncStatus.Error(context.getString(R.string.favorites_sync_unknown_error, e.message)))
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
            status.onNext(FavoritesSyncStatus.Idle(context))
        } else {
            status.onNext(FavoritesSyncStatus.CompleteWithErrors(errorList))
        }
    }

    private fun applyRemoteCategories(categories: List<String>) {
        val localCategories = db.getCategories().executeAsBlocking()

        val newLocalCategories = localCategories.toMutableList()

        var changed = false

        categories.forEachIndexed { index, remote ->
            val local = localCategories.getOrElse(index) {
                changed = true

                Category.create(remote).apply {
                    order = index

                    // Going through categories list from front to back
                    // If category does not exist, list size <= category index
                    // Thus, we can just add it here and not worry about indexing
                    newLocalCategories += this
                }
            }

            if (local.name != remote) {
                changed = true

                local.name = remote
            }
        }

        // Ensure consistent ordering
        newLocalCategories.forEachIndexed { index, category ->
            if (category.order != index) {
                changed = true

                category.order = index
            }
        }

        // Only insert categories if changed
        if (changed) {
            db.insertCategories(newLocalCategories).executeAsBlocking()
        }
    }

    private fun addGalleryRemote(errorList: MutableList<String>, gallery: FavoriteEntry) {
        val url = "${exh.baseUrl}/gallerypopups.php?gid=${gallery.gid}&t=${gallery.token}&act=addfav"

        val request = Request.Builder()
            .url(url)
            .post(
                FormBody.Builder()
                    .add("favcat", gallery.category.toString())
                    .add("favnote", "")
                    .add("apply", "Add to Favorites")
                    .add("update", "1")
                    .build()
            )
            .build()

        if (!explicitlyRetryExhRequest(10, request)) {
            val errorString = "Unable to add gallery to remote server: '${gallery.title}' (GID: ${gallery.gid})!"

            if (prefs.eh_lenientSync().get()) {
                errorList += errorString
            } else {
                status.onNext(FavoritesSyncStatus.Error(errorString))
                throw IgnoredException()
            }
        }
    }

    private fun explicitlyRetryExhRequest(retryCount: Int, request: Request): Boolean {
        var success = false

        for (i in 1..retryCount) {
            try {
                val resp = exh.client.newCall(request).execute()

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

    private fun applyChangeSetToRemote(errorList: MutableList<String>, changeSet: ChangeSet) {
        // Apply removals
        if (changeSet.removed.isNotEmpty()) {
            status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_removing_galleries, changeSet.removed.size), context = context))

            val formBody = FormBody.Builder()
                .add("ddact", "delete")
                .add("apply", "Apply")

            // Add change set to form
            changeSet.removed.forEach {
                formBody.add("modifygids[]", it.gid)
            }

            val request = Request.Builder()
                .url("https://exhentai.org/favorites.php")
                .post(formBody.build())
                .build()

            if (!explicitlyRetryExhRequest(10, request)) {
                val errorString = context.getString(R.string.favorites_sync_unable_to_delete)

                if (prefs.eh_lenientSync().get()) {
                    errorList += errorString
                } else {
                    status.onNext(FavoritesSyncStatus.Error(errorString))
                    throw IgnoredException()
                }
            }
        }

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.onNext(
                FavoritesSyncStatus.Processing(
                    context.getString(R.string.favorites_sync_adding_to_remote, index + 1, changeSet.added.size),
                    needWarnThrottle(),
                    context
                )
            )

            throttleManager.throttle()

            addGalleryRemote(errorList, it)
        }
    }

    private fun applyChangeSetToLocal(errorList: MutableList<String>, changeSet: ChangeSet) {
        val removedManga = mutableListOf<Manga>()

        // Apply removals
        changeSet.removed.forEachIndexed { index, it ->
            status.onNext(FavoritesSyncStatus.Processing(context.getString(R.string.favorites_sync_remove_from_local, index + 1, changeSet.removed.size), context = context))
            val url = it.getUrl()

            // Consider both EX and EH sources
            listOf(
                db.getManga(url, EXH_SOURCE_ID),
                db.getManga(url, EH_SOURCE_ID)
            ).forEach {
                val manga = it.executeAsBlocking()

                if (manga?.favorite == true) {
                    manga.favorite = false
                    manga.date_added = 0
                    db.updateMangaFavorite(manga).executeAsBlocking()
                    removedManga += manga
                }
            }
        }

        // Can't do too many DB OPs in one go
        removedManga.chunked(10).forEach {
            db.deleteOldMangasCategories(it).executeAsBlocking()
        }

        val insertedMangaCategories = mutableListOf<Pair<MangaCategory, Manga>>()
        val categories = db.getCategories().executeAsBlocking()

        // Apply additions
        throttleManager.resetThrottle()
        changeSet.added.forEachIndexed { index, it ->
            status.onNext(
                FavoritesSyncStatus.Processing(
                    context.getString(R.string.favorites_sync_add_to_local, index + 1, changeSet.added.size),
                    needWarnThrottle(),
                    context
                )
            )

            throttleManager.throttle()

            // Import using gallery adder
            val result = galleryAdder.addGallery(
                context,
                "${exh.baseUrl}${it.getUrl()}",
                true,
                exh,
                throttleManager::throttle
            )

            if (result is GalleryAddEvent.Fail) {
                if (result is GalleryAddEvent.Fail.NotFound) {
                    XLog.e(context.getString(R.string.favorites_sync_remote_not_exist, it.getUrl()))
                    // Skip this gallery, it no longer exists
                    return@forEachIndexed
                }

                val errorString = context.getString(R.string.favorites_sync_failed_to_add_to_local) + when (result) {
                    is GalleryAddEvent.Fail.Error -> context.getString(R.string.favorites_sync_failed_to_add_to_local_error, it.title, result.logMessage)
                    is GalleryAddEvent.Fail.UnknownType -> context.getString(R.string.favorites_sync_failed_to_add_to_local_unknown_type, it.title, result.galleryUrl)
                }

                if (prefs.eh_lenientSync().get()) {
                    errorList += errorString
                } else {
                    status.onNext(FavoritesSyncStatus.Error(errorString))
                    throw IgnoredException()
                }
            } else if (result is GalleryAddEvent.Success) {
                insertedMangaCategories += MangaCategory.create(
                    result.manga,
                    categories[it.category]
                ) to result.manga
            }
        }

        // Can't do too many DB OPs in one go
        insertedMangaCategories.chunked(10).map {
            Pair(it.map { it.first }, it.map { it.second })
        }.forEach {
            db.setMangaCategories(it.first, it.second)
        }
    }

    private fun needWarnThrottle() =
        throttleManager.throttleTime >= THROTTLE_WARN

    class IgnoredException : RuntimeException()

    companion object {
        private const val THROTTLE_WARN = 1000
    }
}

sealed class FavoritesSyncStatus(val message: String) {
    class Error(message: String) : FavoritesSyncStatus(message)
    class Idle(context: Context) : FavoritesSyncStatus(context.getString(R.string.favorites_sync_waiting_for_start))
    sealed class BadLibraryState(message: String) : FavoritesSyncStatus(message) {
        class MangaInMultipleCategories(
            val manga: Manga,
            val categories: List<Category>,
            context: Context
        ) :
            BadLibraryState(context.getString(R.string.favorites_sync_manga_in_multiple_categories, manga.title, categories.joinToString { it.name }))
    }
    class Initializing(context: Context) : FavoritesSyncStatus(context.getString(R.string.favorites_sync_initializing))
    class Processing(message: String, isThrottle: Boolean = false, context: Context) : FavoritesSyncStatus(
        if (isThrottle) {
            context.getString(R.string.favorites_sync_processing_throttle, message)
        } else {
            message
        }
    )
    class CompleteWithErrors(messages: List<String>) : FavoritesSyncStatus(messages.joinToString("\n"))
}
