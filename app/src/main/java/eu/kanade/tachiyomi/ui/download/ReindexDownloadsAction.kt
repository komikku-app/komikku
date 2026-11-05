package eu.kanade.tachiyomi.ui.download

import android.content.Context
import eu.kanade.domain.chapter.interactor.CleanupOrphanedDuplicateChapters
import eu.kanade.domain.chapter.interactor.ReconcileChapterDownloads
import eu.kanade.domain.chapter.interactor.RestoreOrphanedChapters
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicBoolean

// One run at a time: a second tap would rescan the library concurrently and race the
// cleanup pass's disk renames.
private val isReindexRunning = AtomicBoolean(false)

/**
 * Invalidates the download cache, links existing on-disk downloads to catalog chapters via
 * [ReconcileChapterDownloads], then runs [RestoreOrphanedChapters] and
 * [CleanupOrphanedDuplicateChapters]. Reconcile runs before orphan restore so URL-drifted folder
 * names are registered without inserting `orphaned://` rows. See
 * `roadmap/downloaded manga not showing up/download_registry_implementation.md`.
 */
fun reindexDownloads(
    context: Context,
    scope: CoroutineScope,
    downloadCache: DownloadCache = Injekt.get(),
    reconcileChapterDownloads: ReconcileChapterDownloads = Injekt.get(),
    restoreOrphanedChapters: RestoreOrphanedChapters = Injekt.get(),
    cleanupOrphanedDuplicateChapters: CleanupOrphanedDuplicateChapters = Injekt.get(),
) {
    if (!isReindexRunning.compareAndSet(false, true)) {
        context.toast(MR.strings.reindex_downloads_in_progress)
        return
    }
    downloadCache.invalidateCache()
    context.toast(MR.strings.download_cache_invalidated)
    scope.launchNonCancellable {
        try {
            val linked = reconcileChapterDownloads.await()
            val restored = restoreOrphanedChapters.await()
            val removedDuplicates = cleanupOrphanedDuplicateChapters.await()
            downloadCache.invalidateCache()
            withUIContext {
                if (linked > 0) {
                    context.toast(
                        context.stringResource(
                            MR.strings.reconcile_chapter_downloads_success,
                            linked,
                        ),
                    )
                }
                if (restored > 0) {
                    context.toast(
                        context.stringResource(
                            MR.strings.restore_orphaned_chapters_success,
                            restored,
                        ),
                    )
                }
                if (removedDuplicates > 0) {
                    context.toast(
                        context.stringResource(
                            MR.strings.cleanup_duplicate_orphan_chapters_success,
                            removedDuplicates,
                        ),
                    )
                }
                context.toast(MR.strings.reindex_downloads_complete)
            }
        } finally {
            isReindexRunning.set(false)
        }
    }
}
