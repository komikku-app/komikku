package eu.kanade.domain

import tachiyomi.data.error.LibraryUpdateErrorMessageRepositoryImpl
import tachiyomi.data.error.LibraryUpdateErrorRepositoryImpl
import tachiyomi.data.error.LibraryUpdateErrorWithRelationsRepositoryImpl
import tachiyomi.domain.error.interactor.DeleteLibraryUpdateErrorMessages
import tachiyomi.domain.error.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.error.interactor.GetLibraryUpdateErrorMessages
import tachiyomi.domain.error.interactor.GetLibraryUpdateErrorWithRelations
import tachiyomi.domain.error.interactor.GetLibraryUpdateErrors
import tachiyomi.domain.error.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.error.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.error.repository.LibraryUpdateErrorMessageRepository
import tachiyomi.domain.error.repository.LibraryUpdateErrorRepository
import tachiyomi.domain.error.repository.LibraryUpdateErrorWithRelationsRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class KMKDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<LibraryUpdateErrorWithRelationsRepository> {
            LibraryUpdateErrorWithRelationsRepositoryImpl(get())
        }
        addFactory { GetLibraryUpdateErrorWithRelations(get()) }

        addSingletonFactory<LibraryUpdateErrorMessageRepository> { LibraryUpdateErrorMessageRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrorMessages(get()) }
        addFactory { DeleteLibraryUpdateErrorMessages(get()) }
        addFactory { InsertLibraryUpdateErrorMessages(get()) }

        addSingletonFactory<LibraryUpdateErrorRepository> { LibraryUpdateErrorRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrors(get()) }
        addFactory { DeleteLibraryUpdateErrors(get()) }
        addFactory { InsertLibraryUpdateErrors(get()) }
    }
}
