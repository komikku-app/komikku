package eu.kanade.domain

import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSources
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.get

class SYDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addFactory { GetSourceCategories(get()) }
        addFactory { GetShowLatest(get()) }
        addFactory { ToggleExcludeFromDataSaver(get()) }
        addFactory { SetSourceCategories(get()) }
        addFactory { ToggleSources(get()) }
        addFactory { GetMergedChapterByMangaId(get()) }
    }
}
