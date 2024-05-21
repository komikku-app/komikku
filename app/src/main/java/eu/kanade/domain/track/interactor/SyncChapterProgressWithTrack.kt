package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.Tracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track

class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ) {
        // KKM -->
        // if (tracker !is EnhancedTracker) {
        //     return
        // }
        // <-- KKM

        // Current chapters in database
        val sortedChapters = getChaptersByMangaId.await(mangaId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        // Chapters to update to follow tracker
        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteTrack.lastChapterRead && !chapter.read }
            .map { it.copy(read = true).toChapterUpdate() }

        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        // Tracker will update to latest read chapter
        val updatedTrack = remoteTrack.copy(lastChapterRead = localLastRead.toDouble())

        try {
            // Update Tracker to localLastRead if needed
            if (updatedTrack.lastChapterRead > remoteTrack.lastChapterRead) {
                tracker.update(updatedTrack.toDbTrack())
                // update Track in database
                insertTrack.await(updatedTrack)
            }
            // Update local chapters following Tracker
            updateChapter.awaitAll(chapterUpdates)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
