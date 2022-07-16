package eu.kanade.domain

import eu.kanade.data.manga.FavoritesEntryRepositoryImpl
import eu.kanade.data.manga.MangaMergeRepositoryImpl
import eu.kanade.data.manga.MangaMetadataRepositoryImpl
import eu.kanade.data.source.FeedSavedSearchRepositoryImpl
import eu.kanade.data.source.SavedSearchRepositoryImpl
import eu.kanade.domain.chapter.interactor.DeleteChapters
import eu.kanade.domain.chapter.interactor.GetChapterByUrl
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.history.interactor.GetHistoryByMangaId
import eu.kanade.domain.manga.interactor.CreateSortTag
import eu.kanade.domain.manga.interactor.DeleteByMergeId
import eu.kanade.domain.manga.interactor.DeleteFavoriteEntries
import eu.kanade.domain.manga.interactor.DeleteMangaById
import eu.kanade.domain.manga.interactor.DeleteMergeById
import eu.kanade.domain.manga.interactor.DeleteSortTag
import eu.kanade.domain.manga.interactor.GetAllManga
import eu.kanade.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import eu.kanade.domain.manga.interactor.GetFavoriteEntries
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetIdsOfFavoriteMangaWithMetadata
import eu.kanade.domain.manga.interactor.GetMangaBySource
import eu.kanade.domain.manga.interactor.GetMergedManga
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.GetMergedMangaForDownloading
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.domain.manga.interactor.GetPagePreviews
import eu.kanade.domain.manga.interactor.GetSearchMetadata
import eu.kanade.domain.manga.interactor.GetSearchTags
import eu.kanade.domain.manga.interactor.GetSearchTitles
import eu.kanade.domain.manga.interactor.GetSortTag
import eu.kanade.domain.manga.interactor.InsertFavoriteEntries
import eu.kanade.domain.manga.interactor.InsertFlatMetadata
import eu.kanade.domain.manga.interactor.InsertMergedReference
import eu.kanade.domain.manga.interactor.ReorderSortTag
import eu.kanade.domain.manga.interactor.SetMangaFilteredScanlators
import eu.kanade.domain.manga.interactor.UpdateMergedSettings
import eu.kanade.domain.manga.repository.FavoritesEntryRepository
import eu.kanade.domain.manga.repository.MangaMergeRepository
import eu.kanade.domain.manga.repository.MangaMetadataRepository
import eu.kanade.domain.source.interactor.CountFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.CountFeedSavedSearchGlobal
import eu.kanade.domain.source.interactor.CreateSourceCategory
import eu.kanade.domain.source.interactor.CreateSourceRepo
import eu.kanade.domain.source.interactor.DeleteFeedSavedSearchById
import eu.kanade.domain.source.interactor.DeleteSavedSearchById
import eu.kanade.domain.source.interactor.DeleteSourceCategory
import eu.kanade.domain.source.interactor.DeleteSourceRepos
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.GetFeedSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetFeedSavedSearchGlobal
import eu.kanade.domain.source.interactor.GetSavedSearchById
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceId
import eu.kanade.domain.source.interactor.GetSavedSearchBySourceIdFeed
import eu.kanade.domain.source.interactor.GetSavedSearchGlobalFeed
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.GetSourceRepos
import eu.kanade.domain.source.interactor.InsertFeedSavedSearch
import eu.kanade.domain.source.interactor.InsertSavedSearch
import eu.kanade.domain.source.interactor.RenameSourceCategory
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSources
import eu.kanade.domain.source.repository.FeedSavedSearchRepository
import eu.kanade.domain.source.repository.SavedSearchRepository
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
        addFactory { ToggleSources(get()) }
        addFactory { SetMangaFilteredScanlators(get()) }
        addFactory { GetAllManga(get()) }
        addFactory { GetMangaBySource(get()) }
        addFactory { DeleteChapters(get()) }
        addFactory { DeleteMangaById(get()) }
        addFactory { FilterSerializer() }
        addFactory { GetHistoryByMangaId(get()) }
        addFactory { GetChapterByUrl(get()) }
        addFactory { CreateSourceRepo(get()) }
        addFactory { DeleteSourceRepos(get()) }
        addFactory { GetSourceRepos(get()) }
        addFactory { GetSourceCategories(get()) }
        addFactory { CreateSourceCategory(get()) }
        addFactory { RenameSourceCategory(get(), get()) }
        addFactory { DeleteSourceCategory(get()) }
        addFactory { GetSortTag(get()) }
        addFactory { CreateSortTag(get(), get()) }
        addFactory { DeleteSortTag(get(), get()) }
        addFactory { ReorderSortTag(get(), get()) }
        addFactory { GetPagePreviews(get()) }

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
        addFactory { GetMergedChapterByMangaId(get()) }
        addFactory { InsertMergedReference(get()) }
        addFactory { UpdateMergedSettings(get()) }
        addFactory { DeleteByMergeId(get()) }
        addFactory { DeleteMergeById(get()) }
        addFactory { GetMergedMangaForDownloading(get()) }

        addSingletonFactory<FavoritesEntryRepository> { FavoritesEntryRepositoryImpl(get()) }
        addFactory { GetFavoriteEntries(get()) }
        addFactory { InsertFavoriteEntries(get()) }
        addFactory { DeleteFavoriteEntries(get()) }

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
    }
}
