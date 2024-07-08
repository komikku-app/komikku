package eu.kanade.translation

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.translation.translators.LanguageTranslators
import eu.kanade.translation.translators.ScanLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNow
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Translator(context: Context, private val downloadPreferences: DownloadPreferences = Injekt.get()) {
    private val chapterTranslator = ChapterTranslator(context)
    private val _queueState = MutableStateFlow<List<Translation>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _isTranslatorRunning = MutableStateFlow(false)
    private var currentTranslationJob: Job? = null
    private var currentTranslationJobID: Long = 0
    val isTranslatorRunning = _isTranslatorRunning.asStateFlow()
    val queueState = _queueState.asStateFlow()

    init {

        launchNow {
            downloadPreferences.translateToLanguage().changes().onEach {
                chapterTranslator.updateToLanguage(it)
            }.launchIn(ProcessLifecycleOwner.get().lifecycleScope)
            downloadPreferences.translateFromLanguage().changes().onEach {
                chapterTranslator.updateFromLanguage(ScanLanguage.entries[it])
            }.launchIn(ProcessLifecycleOwner.get().lifecycleScope)
            downloadPreferences.translationApiKey().changes().onEach {
                chapterTranslator.updateAPIKey(it)
            }.launchIn(ProcessLifecycleOwner.get().lifecycleScope)
            downloadPreferences.translationFont().changes().onEach {
                chapterTranslator.updateFont(context, it)
            }.launchIn(ProcessLifecycleOwner.get().lifecycleScope)
            downloadPreferences.translationEngine().changes().onEach {
                chapterTranslator.updateEngine(LanguageTranslators.entries[it])
            }.launchIn(ProcessLifecycleOwner.get().lifecycleScope)
        }
    }

    private fun startTranslations() {
        if (!_isTranslatorRunning.value) {
            _isTranslatorRunning.value = true
            startNextTranslation()
        }
    }

    private fun startNextTranslation() {
        if (!isTranslatorRunning.value) return
        // Get the next translation in the queue.
        val nextTranslation = _queueState.value.firstOrNull { it.status == Translation.State.QUEUE }
        // If there is a next translation, start translating it.
        if (nextTranslation != null) {
            nextTranslation.status = Translation.State.TRANSLATING
            currentTranslationJob = scope.launchIO {
                startTranslationNow(nextTranslation)
            }
            currentTranslationJob?.invokeOnCompletion {
                cancelTranslation(nextTranslation.chapter.id)
                startNextTranslation()
            }
            currentTranslationJobID = nextTranslation.chapter.id
        } else {
            // If there are no more translations in the queue, stop the translator.
            _isTranslatorRunning.value = false
        }
    }


    private suspend fun startTranslationNow(translation: Translation) {
        try {
            chapterTranslator.translateChapter(translation)

            translation.status = Translation.State.TRANSLATED
            logcat { "Translated chapter ${translation.chapter.id}" }
        } catch (e: Exception) {
            logcat { e.stackTraceToString() }
            logcat { "Failed to translate chapter ${translation.chapter.id}" }
            translation.status = Translation.State.ERROR
            cancelCurrent()
        }
    }

    private fun cancelCurrent() {
        currentTranslationJob?.cancel()
        currentTranslationJob = null
        currentTranslationJobID = 0
    }

    fun cancelTranslation(chapterId: Long) {
        if (chapterId == currentTranslationJobID) {
            cancelCurrent()
        }
        _queueState.update { it -> it.filter { it.chapter.id != chapterId } }

    }

    fun clearQueue() {
        // Clear the translation queue here.
        _queueState.value = emptyList()
        _isTranslatorRunning.value = false
    }

    fun getQueuedTranslationOrNull(chapterId: Long): Translation? {
        // Check if the chapter is queued for translation.
        return _queueState.value.firstOrNull { it.chapter.id == chapterId }
    }

    fun getActiveTranslationID(): Long {
        return currentTranslationJobID
    }

    suspend fun translateChapters(chapterIds: List<Long>) {
        // Update the queue state directly.
        _queueState.value += chapterIds.mapNotNull {

            val ts = Translation.fromChapterId(
                it,
            )
            if (ts != null) {
                ts.status = Translation.State.QUEUE
            }
            ts

        }
        // Start the translator if it's not already running.
        startTranslations()


    }

    suspend fun translateChapter(chapterId: Long) {
        // Update the queue state directly.
        val translation =
            Translation.fromChapterId(chapterId) ?: return
        translation.status = Translation.State.QUEUE
        _queueState.value += translation
        // Start the translator if it's not already running.
        startTranslations()
    }

}
