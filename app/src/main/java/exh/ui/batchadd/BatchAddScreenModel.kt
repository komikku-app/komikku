package exh.ui.batchadd

import android.content.Context
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.log.xLogE
import exh.source.ExhPreferences
import exh.util.trimOrNull
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BatchAddScreenModel(
    private val exhPreferences: ExhPreferences = Injekt.get(),
) : StateScreenModel<BatchAddState>(BatchAddState()) {
    private val galleryAdder by lazy { GalleryAdder() }

    // KMK -->
    val isHentaiEnabled by Injekt.get<ExhPreferences>().isHentaiEnabled().asState(screenModelScope)
    // KMK <--

    fun addGalleries(context: Context) {
        val galleries = state.value.galleries
        // Check text box has content
        if (galleries.isBlank()) {
            mutableState.update { it.copy(dialog = Dialog.NoGalleriesSpecified) }
            return
        }

        addGalleries(context, galleries)
    }

    private fun addGalleries(context: Context, galleries: String) {
        val splitGalleries = if (ehVisitedRegex.containsMatchIn(galleries)) {
            val url = if (exhPreferences.enableExhentai().get()) {
                "https://exhentai.org/g/"
            } else {
                "https://e-hentai.org/g/"
            }
            ehVisitedRegex.findAll(galleries).map { galleryKeys ->
                val linkParts = galleryKeys.value.split(".")
                url + linkParts[0] + "/" + linkParts[1].replace(":", "")
            }.toList()
        } else {
            galleries.split("\n")
                .mapNotNull(String::trimOrNull)
        }

        mutableState.update { state ->
            state.copy(
                progress = 0,
                progressTotal = splitGalleries.size,
                state = State.PROGRESS,
            )
        }

        val handler = CoroutineExceptionHandler { _, throwable ->
            xLogE("Batch add error", throwable)
        }

        screenModelScope.launch(Dispatchers.IO + handler) {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<String>()

            splitGalleries.forEachIndexed { i, s ->
                ensureActive()
                val result = withIOContext {
                    galleryAdder.addGallery(
                        context = context,
                        url = s,
                        fav = true,
                        retry = 2,
                    )
                }
                if (result is GalleryAddEvent.Success) {
                    succeeded.add(s)
                } else {
                    failed.add(s)
                }
                mutableState.update { state ->
                    state.copy(
                        progress = i + 1,
                        events = state.events.plus(
                            when (result) {
                                is GalleryAddEvent.Success -> context.stringResource(SYMR.strings.batch_add_ok)
                                is GalleryAddEvent.Fail -> context.stringResource(SYMR.strings.batch_add_error)
                            } + " " + result.logMessage,
                        ),
                    )
                }
            }

            // Show report
            val summary = context.stringResource(SYMR.strings.batch_add_summary, succeeded.size, failed.size)
            mutableState.update { state ->
                state.copy(
                    events = state.events + summary,
                )
            }
        }
    }

    fun finish() {
        mutableState.update { state ->
            state.copy(
                progressTotal = 0,
                progress = 0,
                galleries = "",
                state = State.INPUT,
                events = emptyList(),
            )
        }
    }

    fun updateGalleries(galleries: String) {
        mutableState.update { it.copy(galleries = galleries) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    enum class State {
        INPUT,
        PROGRESS,
    }

    sealed class Dialog {
        data object NoGalleriesSpecified : Dialog()
    }

    companion object {
        val ehVisitedRegex = """[0-9]*?\.[a-z0-9]*?:""".toRegex()
    }
}

data class BatchAddState(
    val progressTotal: Int = 0,
    val progress: Int = 0,
    val galleries: String = "",
    val state: BatchAddScreenModel.State = BatchAddScreenModel.State.INPUT,
    val events: List<String> = emptyList(),
    val dialog: BatchAddScreenModel.Dialog? = null,
)
