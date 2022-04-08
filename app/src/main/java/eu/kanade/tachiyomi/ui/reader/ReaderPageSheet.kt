package eu.kanade.tachiyomi.ui.reader

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private val bg: Int? = null,
) : BaseBottomSheetDialog(activity) {

    private lateinit var binding: ReaderPageSheetBinding

    override fun createView(inflater: LayoutInflater): View {
        binding = ReaderPageSheetBinding.inflate(activity.layoutInflater, null, false)

        binding.setAsCover.setOnClickListener { setAsCover(page) }
        binding.share.setOnClickListener { share(page) }
        binding.save.setOnClickListener { save(page) }

        if (extraPage != null) {
            binding.setAsCover.setText(R.string.action_set_first_page_cover)
            binding.share.setText(R.string.action_share_first_page)
            binding.save.setText(R.string.action_save_first_page)

            binding.setAsCoverExtra.isVisible = true
            binding.setAsCoverExtra.setOnClickListener { setAsCover(extraPage) }
            binding.shareExtra.isVisible = true
            binding.shareExtra.setOnClickListener { share(extraPage) }
            binding.saveExtra.isVisible = true
            binding.saveExtra.setOnClickListener { save(extraPage) }

            binding.shareCombined.isVisible = true
            binding.shareCombined.setOnClickListener { shareCombined() }
            binding.saveCombined.isVisible = true
            binding.saveCombined.setOnClickListener { saveCombined() }
        }

        return binding.root
    }

    /**
     * Sets the image of this page as the cover of the manga.
     */
    private fun setAsCover(page: ReaderPage) {
        if (page.status != Page.READY) return

        MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.confirm_set_image_as_cover)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                activity.setAsCover(page)
            }
            .setNegativeButton(android.R.string.cancel, null)
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
