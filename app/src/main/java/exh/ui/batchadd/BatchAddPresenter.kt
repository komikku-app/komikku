package exh.ui.batchadd

import android.content.Context
import com.jakewharton.rxrelay.BehaviorRelay
import com.jakewharton.rxrelay.ReplayRelay
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import exh.GalleryAddEvent
import exh.GalleryAdder
import exh.util.trimOrNull
import kotlin.concurrent.thread
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BatchAddPresenter : BasePresenter<BatchAddController>() {

    private val galleryAdder by lazy { GalleryAdder() }

    val progressTotalRelay = BehaviorRelay.create(0)!!
    val progressRelay = BehaviorRelay.create(0)!!
    var eventRelay: ReplayRelay<String>? = null
    val currentlyAddingRelay = BehaviorRelay.create(STATE_IDLE)!!

    fun addGalleries(context: Context, galleries: String) {
        eventRelay = ReplayRelay.create()
        val regex =
            """[0-9]*?\.[a-z0-9]*?:""".toRegex()
        val testedGalleries: String

        testedGalleries = if (regex.containsMatchIn(galleries)) {
            regex.findAll(galleries).map { galleryKeys ->
                val LinkParts = galleryKeys.value.split(".")
                val Link = "${if (Injekt.get<PreferencesHelper>().enableExhentai().get()) {
                    "https://exhentai.org/g/"
                } else {
                    "https://e-hentai.org/g/"
                }}${LinkParts[0]}/${LinkParts[1].replace(":", "")}"
                Link
            }.joinToString(separator = "\n")
        } else {
            galleries
        }
        val splitGalleries = testedGalleries.split("\n").mapNotNull {
            it.trimOrNull()
        }

        progressRelay.call(0)
        progressTotalRelay.call(splitGalleries.size)

        currentlyAddingRelay.call(STATE_INPUT_TO_PROGRESS)

        thread {
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<String>()

            splitGalleries.forEachIndexed { i, s ->
                val result = galleryAdder.addGallery(context, s, true)
                if (result is GalleryAddEvent.Success) {
                    succeeded.add(s)
                } else {
                    failed.add(s)
                }
                progressRelay.call(i + 1)
                eventRelay?.call(
                    (
                        when (result) {
                            is GalleryAddEvent.Success -> context.getString(R.string.batch_add_ok)
                            is GalleryAddEvent.Fail -> context.getString(R.string.batch_add_error)
                        }
                        ) + " " + result.logMessage
                )
            }

            // Show report
            val summary = context.getString(R.string.batch_add_summary, succeeded.size, failed.size)
            eventRelay?.call(summary)
        }
    }

    companion object {
        const val STATE_IDLE = 0
        const val STATE_INPUT_TO_PROGRESS = 1
        const val STATE_PROGRESS_TO_INPUT = 2
    }
}
