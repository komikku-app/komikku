package tachiyomi.data.di

import android.content.Context
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorWithRelationsRepositoryImpl
import tachiyomi.data.libraryUpdateErrorMessage.LibraryUpdateErrorMessageRepositoryImpl
import tachiyomi.data.manga.CustomMangaRepositoryImpl
import tachiyomi.data.manga.FavoritesEntryRepositoryImpl
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.data.manga.MangaMetadataRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.source.FeedSavedSearchRepositoryImpl
import tachiyomi.data.source.SavedSearchRepositoryImpl
import tachiyomi.data.source.StubSourceRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.data.updates.UpdatesRepositoryImpl
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.manga.repository.FavoritesEntryRepository
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaMetadataRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.repository.FeedSavedSearchRepository
import tachiyomi.domain.source.repository.SavedSearchRepository
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.updates.repository.UpdatesRepository
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * KMK: Injekt registrations of the repository implementations that need nothing but the database
 * ([DatabaseHandler], registered by the caller) and, for user edits, a [Context].
 *
 * Shared by Android and desktop (docs/kmp-desktop/PROGRESS.md, 3.4). The same bindings as the
 * Android app's `DomainModule` / `SYDomainModule` / `KMKDomainModule`, which still register them
 * on their own (upstream-owned files). Repositories that need platform services
 * (`SourceRepository` → `SourceManager`, extension stores → network service) are not included.
 */
fun InjektRegistrar.registerDatabaseRepositories(context: Context) {
    addSingletonFactory<CategoryRepository> { CategoryRepositoryImpl(get()) }
    addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get()) }
    addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
    addSingletonFactory<HistoryRepository> { HistoryRepositoryImpl(get()) }
    addSingletonFactory<TrackRepository> { TrackRepositoryImpl(get()) }
    addSingletonFactory<UpdatesRepository> { UpdatesRepositoryImpl(get()) }
    addSingletonFactory<StubSourceRepository> { StubSourceRepositoryImpl(get()) }

    // SY
    addSingletonFactory<CustomMangaRepository> { CustomMangaRepositoryImpl(context) }
    addSingletonFactory<MangaMergeRepository> { MangaMergeRepositoryImpl(get()) }
    addSingletonFactory<MangaMetadataRepository> { MangaMetadataRepositoryImpl(get()) }
    addSingletonFactory<FavoritesEntryRepository> { FavoritesEntryRepositoryImpl(get()) }
    addSingletonFactory<SavedSearchRepository> { SavedSearchRepositoryImpl(get()) }
    addSingletonFactory<FeedSavedSearchRepository> { FeedSavedSearchRepositoryImpl(get()) }

    // KMK
    addSingletonFactory<LibraryUpdateErrorRepository> { LibraryUpdateErrorRepositoryImpl(get()) }
    addSingletonFactory<LibraryUpdateErrorWithRelationsRepository> {
        LibraryUpdateErrorWithRelationsRepositoryImpl(get())
    }
    addSingletonFactory<LibraryUpdateErrorMessageRepository> { LibraryUpdateErrorMessageRepositoryImpl(get()) }
}
