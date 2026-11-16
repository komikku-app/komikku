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
 * WorkManager job, notifications and scheduling are not used on desktop (scheduling is Phase 8).
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
    onError = { manga, error ->
        val messageId = insertErrorMessages.insert(LibraryUpdateErrorMessage(-1L, error.message ?: error::class.simpleName.orEmpty()))
        insertErrors.upsert(LibraryUpdateError(id = -1L, mangaId = manga.id, messageId = messageId))
    },
)
