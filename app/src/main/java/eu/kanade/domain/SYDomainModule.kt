package eu.kanade.domain

import eu.kanade.data.manga.MangaMergeRepositoryImpl
import eu.kanade.data.manga.MangaMetadataRepositoryImpl
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.domain.manga.interactor.SetMangaFilteredScanlators
import eu.kanade.domain.manga.repository.MangaMergeRepository
import eu.kanade.domain.manga.repository.MangaMetadataRepository
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSources
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class SYDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addFactory { GetSourceCategories(get()) }
        addFactory { GetShowLatest(get()) }
        addFactory { ToggleExcludeFromDataSaver(get()) }
        addFactory { SetSourceCategories(get()) }
        addFactory { ToggleSources(get()) }
        addFactory { SetMangaFilteredScanlators(get()) }

        addSingletonFactory<MangaMetadataRepository> { MangaMetadataRepositoryImpl(get()) }
        addFactory { GetFlatMetadataById(get()) }

        addSingletonFactory<MangaMergeRepository> { MangaMergeRepositoryImpl(get()) }
        addFactory { GetMergedMangaById(get()) }
        addFactory { GetMergedReferencesById(get()) }
        addFactory { GetMergedChapterByMangaId(get()) }
    }
}
