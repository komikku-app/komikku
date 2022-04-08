package exh.ui.batchadd

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EhFragmentBatchAddBinding
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import reactivecircus.flowbinding.android.view.clicks

/**
 * Batch add screen
 */
class BatchAddController : NucleusController<EhFragmentBatchAddBinding, BatchAddPresenter>() {
    override fun getTitle() = activity!!.getString(R.string.batch_add)

    override fun createPresenter() = BatchAddPresenter()

    override fun createBinding(inflater: LayoutInflater) = EhFragmentBatchAddBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.btnAddGalleries.clicks()
            .onEach {
                addGalleries(binding.galleriesBox.text.toString())
            }
            .launchIn(viewScope)

        binding.progressDismissBtn.clicks()
            .onEach {
                presenter.currentlyAddingFlow.value = BatchAddPresenter.STATE_PROGRESS_TO_INPUT
            }
            .launchIn(viewScope)

        binding.scrollView.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        presenter.currentlyAddingFlow
            .buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
            .mapLatest { event ->
                when (event) {
                    BatchAddPresenter.STATE_INPUT_TO_PROGRESS -> coroutineScope {
                        showProgress(binding)
                        presenter.progressFlow
                            .buffer(capacity = Channel.RENDEZVOUS)
                            .combine(presenter.progressTotalFlow) { progress, total ->
                                // Show hide dismiss button
                                binding.progressDismissBtn.isVisible = progress == total
                                formatProgress(progress, total)
                            }
                            .onEach {
                                binding.progressText.text = it
                            }
                            .launchIn(this)

                        presenter.progressTotalFlow
                            .buffer(capacity = Channel.RENDEZVOUS)
                            .onEach {
                                binding.progressBar.max = it
                            }
                            .launchIn(this)

                        presenter.progressFlow
                            .buffer(capacity = Channel.RENDEZVOUS)
                            .onEach {
                                binding.progressBar.progress = it
                            }
                            .launchIn(this)

                        presenter.eventFlow
                            ?.buffer(capacity = Channel.RENDEZVOUS)
                            ?.onEach {
                                binding.progressLog.append("$it\n")
                            }
                            ?.launchIn(this)
                        Unit
                    }
                    BatchAddPresenter.STATE_PROGRESS_TO_INPUT -> {
                        hideProgress(binding)
                        presenter.currentlyAddingFlow.value = BatchAddPresenter.STATE_IDLE
                    }
                }
            }
            .launchIn(viewScope)
    }

    private val EhFragmentBatchAddBinding.progressViews
        get() = listOf(
            progressTitleView,
            progressLog,
            progressBar,
            progressText,
        )

    private val EhFragmentBatchAddBinding.inputViews
        get() = listOf(
            inputTitleView,
            galleriesBox,
            btnAddGalleries,
        )

    private var List<View>.isVisible: Boolean
        get() = throw UnsupportedOperationException()
        set(v) {
            forEach { it.isVisible = v }
        }

    private fun showProgress(target: EhFragmentBatchAddBinding = binding) {
        target.apply {
            viewScope.launch {
                inputViews.isVisible = false
                delay(250L)
                progressViews.isVisible = true
            }
        }.progressLog.text = ""
    }

    private fun hideProgress(target: EhFragmentBatchAddBinding = binding) {
        target.apply {
            viewScope.launch {
                progressViews.isVisible = false
                binding.progressDismissBtn.isVisible = false
                delay(250L)
                inputViews.isVisible = true
            }
        }.galleriesBox.setText("", TextView.BufferType.EDITABLE)
    }

    private fun formatProgress(progress: Int, total: Int) = "$progress/$total"

    private fun addGalleries(galleries: String) {
        // Check text box has content
        if (galleries.isBlank()) {
            noGalleriesSpecified()
            return
        }

        presenter.addGalleries(applicationContext!!, galleries)
    }

    private fun noGalleriesSpecified() {
        activity?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.batch_add_no_valid_galleries)
                .setMessage(R.string.batch_add_no_valid_galleries_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
