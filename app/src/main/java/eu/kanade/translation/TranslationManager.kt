package eu.kanade.translation

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TranslationManager(
    context: Context,
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(), private val getManga: GetManga = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {
    val translator = Translator(context)
    val queueState
        get() = translator.queueState

    fun statusFlow(): Flow<Translation> = queueState.flatMapLatest { translations ->
        translations.map { translation ->
            translation.statusFlow.map { translation }
        }.merge()
    }.onStart {
        emitAll(
            queueState.value.filter { translation -> translation.status == Translation.State.TRANSLATING }.asFlow(),
        )
    }

    suspend fun deleteTranslation(chapterId: Long) {
        try {
            val chapter = getChapter.await(chapterId)!!
            val manga = getManga.await(chapter.mangaId)!!
            downloadProvider.findChapterDir(
                chapter.name,
                chapter.scanlator,
                manga.title,
                sourceManager.getOrStub(manga.source),
            )?.findFile("translations.json")?.delete()

            logcat { "Deleted translation for ${chapter.name}" }
        } catch (e: Exception) {
            logcat { "Failed to delete translation for ${chapterId}: ${e.message}" }
        }
    }

    fun cancelTranslation(chapterID: Long) {
        translator.cancelTranslation(chapterID)
    }

    fun getChapterTranslation(
        chapterName: String,
        scanlator: String?,
        title: String,
        source: Source,
    ): Map<String, List<TextTranslation>>? {
        try {
            val dir = downloadProvider.findChapterDir(
                chapterName,
                scanlator,
                title,
                source,
            ) ?: return null;
            return dir.findFile("translations.json")?.let { getChapterTranslation(it) }
        } catch (e: Exception) {
        }
        return null

    }

    fun getChapterTranslation(
        file: UniFile,
    ): Map<String, List<TextTranslation>>? {
        try {
            return Json.decodeFromStream<Map<String, List<TextTranslation>>>(file.openInputStream())
        } catch (e: Exception) {
            file.delete()
        }
        return null
    }

    fun translateChapter(chapterID: Long) {
        translator.translateChapter(chapterID)
    }

    fun getChapterTranslationStatus(
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        title: String,
        source: Source,
    ): Translation.State {
        if (translator.getActiveTranslationID() == chapterId) return Translation.State.TRANSLATING
        if (translator.getQueuedTranslationOrNull(chapterId) != null) return Translation.State.QUEUE

        val dir = downloadProvider.findChapterDir(
            chapterName,
            scanlator,
            title,
            source,
        ) ?: return Translation.State.NOT_TRANSLATED
        if (dir.findFile("translations.json")?.exists() == true) return Translation.State.TRANSLATED
        return Translation.State.NOT_TRANSLATED;
    }

}

