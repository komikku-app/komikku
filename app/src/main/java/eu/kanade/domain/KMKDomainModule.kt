package eu.kanade.domain

import tachiyomi.data.chapterTag.ChapterTagRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorWithRelationsRepositoryImpl
import tachiyomi.data.libraryUpdateErrorMessage.LibraryUpdateErrorMessageRepositoryImpl
import tachiyomi.domain.chapterTag.interactor.CreateChapterTagWithName
import tachiyomi.domain.chapterTag.interactor.DeleteChapterTag
import tachiyomi.domain.chapterTag.interactor.GetChapterTagFilter
import tachiyomi.domain.chapterTag.interactor.GetChapterTags
import tachiyomi.domain.chapterTag.interactor.GetChapterTagsPerManga
import tachiyomi.domain.chapterTag.interactor.RenameChapterTag
import tachiyomi.domain.chapterTag.interactor.SetChapterTagFilter
import tachiyomi.domain.chapterTag.interactor.SetChapterTags
import tachiyomi.domain.chapterTag.repository.ChapterTagRepository
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.DeleteLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.GetLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository
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

        addSingletonFactory<ChapterTagRepository> { ChapterTagRepositoryImpl(get()) }
        addFactory { GetChapterTags(get()) }
        addFactory { GetChapterTagsPerManga(get()) }
        addFactory { CreateChapterTagWithName(get()) }
        addFactory { RenameChapterTag(get()) }
        addFactory { DeleteChapterTag(get(), get()) }
        addFactory { SetChapterTags(get()) }
        addFactory { GetChapterTagFilter(get()) }
        addFactory { SetChapterTagFilter(get()) }
    }
}
