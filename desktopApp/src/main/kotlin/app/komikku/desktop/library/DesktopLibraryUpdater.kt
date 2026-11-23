package app.komikku.desktop.library

import app.komikku.shared.library.CoroutineLibraryUpdateRunner
import app.komikku.shared.library.LibraryUpdateRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import java.time.ZonedDateTime

/**
 * The desktop library update: the shared [CoroutineLibraryUpdateRunner] in an application-wide scope,
 * refreshing each entry like Android's `LibraryUpdateJob.updateManga` (`UpdateMangaFromRemote`,
 * metadata only when "automatically refresh metadata" is on, the fetch window from [FetchInterval]) and
 * storing failures in the library update error tables like the Android manga screen. Android's
 * WorkManager job and notifications are not used on desktop; scheduling is `app.komikku.desktop.scheduler` (P3).
 */
fun desktopLibraryUpdateRunner(
    getLibraryManga: GetLibraryManga,
    libraryPreferences: LibraryPreferences,
    fetchInterval: FetchInterval,
    sourceManager: SourceManager,
    updateMangaFromRemote: UpdateMangaFromRemote,
    insertErrors: InsertLibraryUpdateErrors,
    insertErrorMessages: InsertLibraryUpdateErrorMessages,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    now: () -> Long = System::currentTimeMillis,
): LibraryUpdateRunner = CoroutineLibraryUpdateRunner(
    scope = scope,
    getLibrary = { getLibraryManga.await() },
    preferences = libraryPreferences,
    fetchWindowUpperBound = { fetchInterval.getWindow(ZonedDateTime.now()).second },
    updateManga = { manga ->
        val update = updateMangaFromRemote(
            source = sourceManager.getOrStub(manga.source),
            manga = manga,
            fetchDetails = libraryPreferences.autoUpdateMetadata().get(),
            fetchChapters = true,
            fetchWindow = fetchInterval.getWindow(ZonedDateTime.now()),
        ).getOrThrow()
        if (update.manga.favorite) update.newChapters.size else 0
    },
    // Android's LibraryUpdateJob stores the start of every library (chapter) update as "last updated" (shown in the
    // updates tab, the scheduler's due time); a single entry's refresh (the manga screen) does not.
    onStarted = { target ->
        if (target !is LibraryUpdateRunner.Target.Single) libraryPreferences.lastUpdatedTimestamp().set(now())
    },
    onError = { manga, error ->
        val messageId = insertErrorMessages.insert(LibraryUpdateErrorMessage(-1L, error.message ?: error::class.simpleName.orEmpty()))
        insertErrors.upsert(LibraryUpdateError(id = -1L, mangaId = manga.id, messageId = messageId))
    },
)
