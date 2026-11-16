package app.komikku.desktop.track

import android.app.Application
import app.komikku.shared.reader.ReaderTrackerHook
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.Manga

/**
 * KMK: the desktop reader's tracker side effect (Android `ReaderViewModel.updateTrackChapterRead`): when
 * "update progress after reading" is on, the borrowed [TrackChapter] pushes the chapter number to every
 * logged-in tracker of the manga. Failures go to the persisted delayed-tracking store and are retried by the
 * desktop `DelayedTrackingUpdateJob` coroutine. Incognito reads never get here (`ReaderProgressSaver`).
 * Like Android (`launchNonCancellable`), the update runs in the background so the reader's ordered progress
 * writes do not wait for the network.
 */
class DesktopReaderTrackerHook(
    private val app: Application,
    private val trackPreferences: TrackPreferences,
    private val trackChapter: TrackChapter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ReaderTrackerHook {
    override suspend fun onChapterRead(manga: Manga, chapterNumber: Double) {
        if (!trackPreferences.autoUpdateTrack().get()) return
        scope.launch { trackChapter.await(app, manga.id, chapterNumber) }
    }
}
