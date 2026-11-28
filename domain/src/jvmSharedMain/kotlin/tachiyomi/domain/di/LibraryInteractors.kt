package tachiyomi.domain.di

import exh.source.ExhPreferences
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.HideCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.interactor.UpdateCategory
import tachiyomi.domain.chapter.interactor.DeleteChapters
import tachiyomi.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChapterByUrl
import tachiyomi.domain.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.GetTotalReadDuration
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.DeleteLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.GetLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.manga.interactor.DeleteByMergeId
import tachiyomi.domain.manga.interactor.DeleteFavoriteEntries
import tachiyomi.domain.manga.interactor.DeleteMangaById
import tachiyomi.domain.manga.interactor.DeleteMergeById
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaBySource
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedMangaForDownloading
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.GetReadMangaNotInLibraryView
import tachiyomi.domain.manga.interactor.GetSearchMetadata
import tachiyomi.domain.manga.interactor.GetSearchTags
import tachiyomi.domain.manga.interactor.GetSearchTitles
import tachiyomi.domain.manga.interactor.InsertFavoriteEntries
import tachiyomi.domain.manga.interactor.InsertFavoriteEntryAlternative
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.interactor.InsertMergedReference
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.source.interactor.CountFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.CountFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceIdFeed
import tachiyomi.domain.source.interactor.GetSavedSearchGlobalFeed
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.interactor.ReorderFeed
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerManga
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.interactor.IsTrackUnfollowed
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.service.UpdatesPreferences
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * KMK: Injekt registrations of the preference classes that live in `:domain` (all of them only need
 * the `PreferenceStore`, which the caller registers).
 *
 * Shared by Android and desktop (docs/kmp-desktop/PROGRESS.md, 3.4); the Android app still registers
 * the same classes in its `PreferenceModule` / `SYPreferenceModule` (upstream-owned files).
 */
fun InjektRegistrar.registerDomainPreferences() {
    addSingletonFactory { LibraryPreferences(get()) }
    addSingletonFactory { DownloadPreferences(get()) }
    addSingletonFactory { HistoryPreferences(get()) }
    addSingletonFactory { UpdatesPreferences(get()) }
    addSingletonFactory { BackupPreferences(get()) }
    addSingletonFactory { ExhPreferences(get()) }
}

/**
 * KMK: Injekt registrations of the `:domain` interactors behind the library, manga, chapter,
 * history, track, updates and saved-search features, with the same bindings as the Android app's
 * `DomainModule` / `SYDomainModule` / `KMKDomainModule` (which still register them on their own).
 *
 * Needs the repositories (`tachiyomi.data.di.registerDatabaseRepositories` in `:data`) and
 * [registerDomainPreferences]. Interactors that need platform services (sources, extensions,
 * downloads, trackers) or live in the `app` module are registered by the platform.
 */
fun InjektRegistrar.registerLibraryInteractors() { // Category
    addFactory { CreateCategoryWithName(get(), get()) }
    addFactory { DeleteCategory(get(), get(), get()) }
    addFactory { GetCategories(get()) }
    addFactory { HideCategory(get()) }
    addFactory { RenameCategory(get()) }
    addFactory { ReorderCategory(get()) }
    addFactory { ResetCategoryFlags(get(), get()) }
    addFactory { SetDisplayMode(get()) }
    addFactory { SetMangaCategories(get()) }
    addFactory { SetSortModeForCategory(get(), get()) }
    addFactory { UpdateCategory(get()) }

    // Manga (incl. SY custom info, merged entries, metadata, favorites sync)
    addFactory { DeleteByMergeId(get()) }
    addFactory { DeleteFavoriteEntries(get()) }
    addFactory { DeleteMangaById(get()) }
    addFactory { DeleteMergeById(get()) }
    addFactory { FetchInterval(get()) }
    addFactory { GetAllManga(get()) }
    addFactory { GetCustomMangaInfo(get()) }
    addFactory { GetDuplicateLibraryManga(get()) }
    addFactory { GetExhFavoriteMangaWithMetadata(get()) }
    addFactory { GetFavoriteEntries(get()) }
    addFactory { GetFavorites(get()) }
    addFactory { GetFlatMetadataById(get()) }
    addFactory { GetIdsOfFavoriteMangaWithMetadata(get()) }
    addFactory { GetLibraryManga(get()) }
    addFactory { GetManga(get()) }
    addFactory { GetMangaBySource(get()) }
    addFactory { GetMangaByUrlAndSourceId(get()) }
    addFactory { GetMangaWithChapters(get(), get()) }
    addFactory { GetMergedManga(get()) }
    addFactory { GetMergedMangaById(get()) }
    addFactory { GetMergedMangaForDownloading(get()) }
    addFactory { GetMergedReferencesById(get()) }
    addFactory { GetReadMangaNotInLibraryView(get()) }
    addFactory { GetSearchMetadata(get()) }
    addFactory { GetSearchTags(get()) }
    addFactory { GetSearchTitles(get()) }
    addFactory { InsertFavoriteEntries(get()) }
    addFactory { InsertFavoriteEntryAlternative(get()) }
    addFactory { InsertFlatMetadata(get()) }
    addFactory { InsertMergedReference(get()) }
    addFactory { NetworkToLocalManga(get()) }
    addFactory { ResetViewerFlags(get()) }
    addFactory { SetCustomMangaInfo(get()) }
    addFactory { SetMangaChapterFlags(get()) }
    addFactory { UpdateMangaNotes(get()) }
    addFactory { UpdateMergedSettings(get()) }

    // Chapter
    addFactory { DeleteChapters(get()) }
    addFactory { GetBookmarkedChaptersByMangaId(get(), get(), get()) }
    addFactory { GetChapter(get()) }
    addFactory { GetChapterByUrl(get()) }
    addFactory { GetChapterByUrlAndMangaId(get()) }
    addFactory { GetChaptersByMangaId(get()) }
    addFactory { GetMergedChaptersByMangaId(get(), get()) }
    addFactory { SetMangaDefaultChapterFlags(get(), get(), get()) }
    addFactory { ShouldUpdateDbChapter() }
    addFactory { UpdateChapter(get()) }

    // History
    addFactory { GetHistory(get()) }
    addFactory { GetNextChapters(get(), get(), get(), get(), get()) }
    addFactory { GetTotalReadDuration(get()) }
    addFactory { RemoveHistory(get()) }
    addFactory { UpsertHistory(get()) }

    // Track (database side; trackers are app services)
    addFactory { DeleteTrack(get()) }
    addFactory { GetTracks(get()) }
    addFactory { GetTracksPerManga(get(), get()) }
    addFactory { InsertTrack(get()) }
    addFactory { IsTrackUnfollowed() }

    // Updates
    addFactory { GetUpdates(get()) }

    // Saved searches and feeds (SY); `GetRemoteManga` / `GetSourcesWithNonLibraryManga` need `SourceRepository` and are left to the caller
    addFactory { CountFeedSavedSearchBySourceId(get()) }
    addFactory { CountFeedSavedSearchGlobal(get()) }
    addFactory { DeleteFeedSavedSearchById(get()) }
    addFactory { DeleteSavedSearchById(get()) }
    addFactory { GetFeedSavedSearchBySourceId(get()) }
    addFactory { GetFeedSavedSearchGlobal(get()) }
    addFactory { GetSavedSearchById(get()) }
    addFactory { GetSavedSearchBySourceId(get()) }
    addFactory { GetSavedSearchBySourceIdFeed(get()) }
    addFactory { GetSavedSearchGlobalFeed(get()) }
    addFactory { InsertFeedSavedSearch(get()) }
    addFactory { InsertSavedSearch(get()) }
    addFactory { ReorderFeed(get()) }

    // Library update errors (KMK)
    addFactory { DeleteLibraryUpdateErrors(get()) }
    addFactory { GetLibraryUpdateErrorWithRelations(get()) }
    addFactory { GetLibraryUpdateErrors(get()) }
    addFactory { InsertLibraryUpdateErrors(get()) }
    addFactory { DeleteLibraryUpdateErrorMessages(get()) }
    addFactory { GetLibraryUpdateErrorMessages(get()) }
    addFactory { InsertLibraryUpdateErrorMessages(get()) }
}
