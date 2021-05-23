package eu.kanade.tachiyomi.ui.reader

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderPageSheetBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog

/**
 * Sheet to show when a page is long clicked.
 */
class ReaderPageSheet(
    private val activity: ReaderActivity,
    private val page: ReaderPage,
    private val extraPage: ReaderPage? = null,
    private val isLTR: Boolean = false,
    private val bg: Int? = null
) : BaseBottomSheetDialog(activity) {

    private lateinit var binding: ReaderPageSheetBinding

    override fun createView(inflater: LayoutInflater): View {
        binding = ReaderPageSheetBinding.inflate(activity.layoutInflater, null, false)

        binding.setAsCoverLayout.setOnClickListener { setAsCover(page) }
        binding.shareLayout.setOnClickListener { share(page) }
        binding.saveLayout.setOnClickListener { save(page) }

        if (extraPage != null) {
            binding.setAsCoverItem.setText(R.string.action_set_first_page_cover)
            binding.shareItem.setText(R.string.action_share_first_page)
            binding.saveItem.setText(R.string.action_save_first_page)

            binding.setAsCoverLayoutExtra.isVisible = true
            binding.setAsCoverLayoutExtra.setOnClickListener { setAsCover(extraPage) }
            binding.shareLayoutExtra.isVisible = true
            binding.shareLayoutExtra.setOnClickListener { share(extraPage) }
            binding.saveLayoutExtra.isVisible = true
            binding.saveLayoutExtra.setOnClickListener { save(extraPage) }

            binding.shareLayoutCombined.isVisible = true
            binding.shareLayoutCombined.setOnClickListener { shareCombined() }
            binding.saveLayoutCombined.isVisible = true
            binding.saveLayoutCombined.setOnClickListener { saveCombined() }
        }

        return binding.root
    }

    /**
     * Sets the image of this page as the cover of the manga.
     */
    private fun setAsCover(page: ReaderPage) {
        if (page.status != Page.READY) return

        MaterialDialog(activity)
            .message(R.string.confirm_set_image_as_cover)
            .positiveButton(android.R.string.ok) {
                activity.setAsCover(page)
                dismiss()
            }
            .negativeButton(android.R.string.cancel)
            .show()
    }

    /**
     * Shares the image of this page with external apps.
     */
    private fun share(page: ReaderPage) {
        activity.shareImage(page)
        dismiss()
    }

    fun shareCombined() {
        activity.shareImages(page, extraPage!!, isLTR, bg!!)
        dismiss()
    }

    /**
     * Saves the image of this page on external storage.
     */
    private fun save(page: ReaderPage) {
        activity.saveImage(page)
        dismiss()
    }

    fun saveCombined() {
        activity.saveImages(page, extraPage!!, isLTR, bg!!)
        dismiss()
    }
}
