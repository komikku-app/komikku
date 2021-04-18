package exh.ui.batchadd

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EhFragmentBatchAddBinding
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.util.lang.combineLatest
import eu.kanade.tachiyomi.util.lang.plusAssign
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription

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
                presenter.currentlyAddingRelay.call(BatchAddPresenter.STATE_PROGRESS_TO_INPUT)
            }
            .launchIn(viewScope)

        val progressSubscriptions = CompositeSubscription()

        presenter.currentlyAddingRelay
            .onBackpressureBuffer()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeUntilDestroy {
                progressSubscriptions.clear()
                if (it == BatchAddPresenter.STATE_INPUT_TO_PROGRESS) {
                    showProgress(binding)
                    progressSubscriptions += presenter.progressRelay
                        .onBackpressureBuffer()
                        .observeOn(AndroidSchedulers.mainThread())
                        .combineLatest(presenter.progressTotalRelay) { progress, total ->
                            // Show hide dismiss button
                            binding.progressDismissBtn.visibility =
                                if (progress == total) {
                                    View.VISIBLE
                                } else {
                                    View.GONE
                                }

                            formatProgress(progress, total)
                        }.subscribeUntilDestroy {
                            binding.progressText.text = it
                        }

                    progressSubscriptions += presenter.progressTotalRelay
                        .onBackpressureBuffer()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeUntilDestroy {
                            binding.progressBar.max = it
                        }

                    progressSubscriptions += presenter.progressRelay
                        .onBackpressureBuffer()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeUntilDestroy {
                            binding.progressBar.progress = it
                        }

                    presenter.eventRelay
                        ?.onBackpressureBuffer()
                        ?.observeOn(AndroidSchedulers.mainThread())
                        ?.subscribeUntilDestroy {
                            binding.progressLog.append("$it\n")
                        }?.let {
                            progressSubscriptions += it
                        }
                } else if (it == BatchAddPresenter.STATE_PROGRESS_TO_INPUT) {
                    hideProgress(binding)
                    presenter.currentlyAddingRelay.call(BatchAddPresenter.STATE_IDLE)
                }
            }
    }

    private val EhFragmentBatchAddBinding.progressViews
        get() = listOf(
            progressTitleView,
            progressLogWrapper,
            progressBar,
            progressText,
            progressDismissBtn
        )

    private val EhFragmentBatchAddBinding.inputViews
        get() = listOf(
            inputTitleView,
            galleriesBox,
            btnAddGalleries
        )

    private var List<View>.visibility: Int
        get() = throw UnsupportedOperationException()
        set(v) { forEach { it.visibility = v } }

    private fun showProgress(target: EhFragmentBatchAddBinding = binding) {
        target.apply {
            progressViews.visibility = View.VISIBLE
            inputViews.visibility = View.GONE
        }.progressLog.text = ""
    }

    private fun hideProgress(target: EhFragmentBatchAddBinding = binding) {
        target.apply {
            progressViews.visibility = View.GONE
            inputViews.visibility = View.VISIBLE
        }.galleriesBox.setText("", TextView.BufferType.EDITABLE)
    }

    private fun formatProgress(progress: Int, total: Int) = "$progress/$total"

    private fun addGalleries(galleries: String) {
        // Check text box has content
        if (galleries.isBlank()) {
            noGalleriesSpecified()
            return
        }

        presenter.addGalleries(activity!!, galleries)
    }

    private fun noGalleriesSpecified() {
        activity?.let {
            MaterialDialog(it)
                .title(R.string.batch_add_no_valid_galleries)
                .message(R.string.batch_add_no_valid_galleries_message)
                .positiveButton(android.R.string.ok) { materialDialog -> materialDialog.dismiss() }
                .cancelable(true)
                .cancelOnTouchOutside(true)
                .show()
        }
    }
}
