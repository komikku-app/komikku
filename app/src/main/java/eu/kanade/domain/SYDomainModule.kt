package eu.kanade.domain

import android.app.Application
import eu.kanade.domain.manga.interactor.CreateSortTag
import eu.kanade.domain.manga.interactor.DeleteSortTag
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.interactor.GetSortTag
import eu.kanade.domain.manga.interactor.ReorderSortTag
import eu.kanade.domain.manga.interactor.SmartSearchMerge
import eu.kanade.domain.source.interactor.CreateSourceCategory
import eu.kanade.domain.source.interactor.DeleteSourceCategory
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.RenameSourceCategory
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.search.SearchEngine
import tachiyomi.data.manga.CustomMangaRepositoryImpl
import tachiyomi.data.manga.FavoritesEntryRepositoryImpl
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.data.manga.MangaMetadataRepositoryImpl
import tachiyomi.data.source.FeedSavedSearchRepositoryImpl
import tachiyomi.data.source.SavedSearchRepositoryImpl
import tachiyomi.domain.chapter.interactor.DeleteChapters
import tachiyomi.domain.chapter.interactor.GetChapterByUrl
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.manga.interactor.DeleteByMergeId
import tachiyomi.domain.manga.interactor.DeleteFavoriteEntries
import tachiyomi.domain.manga.interactor.DeleteMangaById
import tachiyomi.domain.manga.interactor.DeleteMergeById
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFavoriteEntries
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaBySource
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
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.manga.repository.FavoritesEntryRepository
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaMetadataRepository
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
import tachiyomi.domain.source.repository.FeedSavedSearchRepository
import tachiyomi.domain.source.repository.SavedSearchRepository
import tachiyomi.domain.track.interactor.IsTrackUnfollowed
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer

class SYDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addFactory { GetShowLatest(get()) }
        addFactory { ToggleExcludeFromDataSaver(get()) }
        addFactory { SetSourceCategories(get()) }
        addFactory { GetAllManga(get()) }
        addFactory { GetMangaBySource(get()) }
        addFactory { DeleteChapters(get()) }
        addFactory { DeleteMangaById(get()) }
        addFactory { FilterSerializer() }
        addFactory { GetChapterByUrl(get()) }
        addFactory { GetSourceCategories(get()) }
        addFactory { CreateSourceCategory(get()) }
        addFactory { RenameSourceCategory(get(), get()) }
        addFactory { DeleteSourceCategory(get()) }
        addFactory { GetSortTag(get()) }
        addFactory { CreateSortTag(get(), get()) }
        addFactory { DeleteSortTag(get(), get()) }
        addFactory { ReorderSortTag(get(), get()) }
        addFactory { GetPagePreviews(get(), get()) }
        addFactory { SearchEngine() }
        addFactory { IsTrackUnfollowed() }
        addFactory { GetReadMangaNotInLibraryView(get()) }

        // Required for [MetadataSource]
        addFactory<MetadataSource.GetMangaId> { GetManga(get()) }
        addFactory<MetadataSource.GetFlatMetadataById> { GetFlatMetadataById(get()) }
        addFactory<MetadataSource.InsertFlatMetadata> { InsertFlatMetadata(get()) }

        addSingletonFactory<MangaMetadataRepository> { MangaMetadataRepositoryImpl(get()) }
        addFactory { GetFlatMetadataById(get()) }
        addFactory { InsertFlatMetadata(get()) }
        addFactory { GetExhFavoriteMangaWithMetadata(get()) }
        addFactory { GetSearchMetadata(get()) }
        addFactory { GetSearchTags(get()) }
        addFactory { GetSearchTitles(get()) }
        addFactory { GetIdsOfFavoriteMangaWithMetadata(get()) }

        addSingletonFactory<MangaMergeRepository> { MangaMergeRepositoryImpl(get()) }
        addFactory { GetMergedManga(get()) }
        addFactory { GetMergedMangaById(get()) }
        addFactory { GetMergedReferencesById(get()) }
        addFactory { GetMergedChaptersByMangaId(get(), get()) }
        addFactory { InsertMergedReference(get()) }
        addFactory { UpdateMergedSettings(get()) }
        addFactory { DeleteByMergeId(get()) }
        addFactory { DeleteMergeById(get()) }
        addFactory { GetMergedMangaForDownloading(get()) }
        // KMK -->
        addFactory { SmartSearchMerge(get()) }
        // KMK <--

        addSingletonFactory<FavoritesEntryRepository> { FavoritesEntryRepositoryImpl(get()) }
        addFactory { GetFavoriteEntries(get()) }
        addFactory { InsertFavoriteEntries(get()) }
        addFactory { DeleteFavoriteEntries(get()) }
        addFactory { InsertFavoriteEntryAlternative(get()) }

        addSingletonFactory<SavedSearchRepository> { SavedSearchRepositoryImpl(get()) }
        addFactory { GetSavedSearchById(get()) }
        addFactory { GetSavedSearchBySourceId(get()) }
        addFactory { DeleteSavedSearchById(get()) }
        addFactory { InsertSavedSearch(get()) }
        addFactory { GetExhSavedSearch(get(), get(), get()) }

        addSingletonFactory<FeedSavedSearchRepository> { FeedSavedSearchRepositoryImpl(get()) }
        addFactory { InsertFeedSavedSearch(get()) }
        addFactory { DeleteFeedSavedSearchById(get()) }
        addFactory { GetFeedSavedSearchGlobal(get()) }
        addFactory { GetFeedSavedSearchBySourceId(get()) }
        addFactory { CountFeedSavedSearchGlobal(get()) }
        addFactory { CountFeedSavedSearchBySourceId(get()) }
        addFactory { GetSavedSearchGlobalFeed(get()) }
        addFactory { GetSavedSearchBySourceIdFeed(get()) }
        // KMK -->
        addFactory { ReorderFeed(get()) }
        // KMK <--

        addSingletonFactory<CustomMangaRepository> { CustomMangaRepositoryImpl(get<Application>()) }
        addFactory { GetCustomMangaInfo(get()) }
        addFactory { SetCustomMangaInfo(get()) }
    }
}
