package exh.ui.batchadd

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.log.xLogE
import exh.util.dropEmpty
import exh.util.trimAll
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

class BatchAddPresenter : BasePresenter<BatchAddController>() {
    private val preferences: PreferencesHelper by injectLazy()

    private val galleryAdder by lazy { GalleryAdder() }

    val progressTotalFlow = MutableStateFlow(0)
    val progressFlow = MutableStateFlow(0)
    var eventFlow: MutableSharedFlow<String>? = null
    val currentlyAddingFlow = MutableStateFlow(STATE_IDLE)

    fun addGalleries(context: Context, galleries: String) {
        eventFlow = MutableSharedFlow(1)
        val regex =
            """[0-9]*?\.[a-z0-9]*?:""".toRegex()

        val testedGalleries = if (regex.containsMatchIn(galleries)) {
            val url = if (preferences.enableExhentai().get()) {
                "https://exhentai.org/g/"
            } else {
                "https://e-hentai.org/g/"
            }
            regex.findAll(galleries).map { galleryKeys ->
                val linkParts = galleryKeys.value.split(".")
                url + linkParts[0] + "/" + linkParts[1].replace(":", "")
            }.joinToString(separator = "\n")
        } else {
            galleries
        }
        val splitGalleries = testedGalleries.split("\n")
            .trimAll()
            .dropEmpty()

        progressFlow.value = 0
        progressTotalFlow.value = splitGalleries.size

        currentlyAddingFlow.value = STATE_INPUT_TO_PROGRESS

        val handler = CoroutineExceptionHandler { _, throwable ->
            xLogE("Batch add error", throwable)
        }

        presenterScope.launch(Dispatchers.IO + handler) {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<String>()

            splitGalleries.forEachIndexed { i, s ->
                ensureActive()
                val result = withIOContext { galleryAdder.addGallery(context, s, true) }
                if (result is GalleryAddEvent.Success) {
                    succeeded.add(s)
                } else {
                    failed.add(s)
                }
                progressFlow.value = i + 1
                eventFlow?.emit(
                    (
                        when (result) {
                            is GalleryAddEvent.Success -> context.getString(R.string.batch_add_ok)
                            is GalleryAddEvent.Fail -> context.getString(R.string.batch_add_error)
                        }
                        ) + " " + result.logMessage,
                )
            }

            // Show report
            val summary = context.getString(R.string.batch_add_summary, succeeded.size, failed.size)
            eventFlow?.emit(summary)
        }
    }

    companion object {
        const val STATE_IDLE = 0
        const val STATE_INPUT_TO_PROGRESS = 1
        const val STATE_PROGRESS_TO_INPUT = 2
    }
}
