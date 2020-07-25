package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.view.children
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.toMangaThumbnail
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.toast
import exh.util.trimOrNull
import kotlinx.android.synthetic.main.edit_manga_dialog.view.cover_layout
import kotlinx.android.synthetic.main.edit_manga_dialog.view.manga_artist
import kotlinx.android.synthetic.main.edit_manga_dialog.view.manga_author
import kotlinx.android.synthetic.main.edit_manga_dialog.view.manga_cover
import kotlinx.android.synthetic.main.edit_manga_dialog.view.manga_description
import kotlinx.android.synthetic.main.edit_manga_dialog.view.manga_genres_tags
import kotlinx.android.synthetic.main.edit_manga_dialog.view.reset_cover
import kotlinx.android.synthetic.main.edit_manga_dialog.view.reset_tags
import kotlinx.android.synthetic.main.edit_manga_dialog.view.title
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMangaDialog : DialogController {

    private var dialogView: View? = null

    private val manga: Manga

    private var customCoverUri: Uri? = null

    private var willResetCover = false

    private val infoController
        get() = targetController as MangaController

    constructor(target: MangaController, manga: Manga) : super(
        Bundle()
            .apply {
                putLong(KEY_MANGA, manga.id!!)
            }
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga = Injekt.get<DatabaseHelper>().getManga(bundle.getLong(KEY_MANGA))
            .executeAsBlocking()!!
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = MaterialDialog(activity!!).apply {
            customView(viewRes = R.layout.edit_manga_dialog, scrollable = true)
            negativeButton(android.R.string.cancel)
            positiveButton(R.string.action_save) { onPositiveButtonClick() }
        }
        dialogView = dialog.view
        onViewCreated(dialog.view)
        dialog.setOnShowListener {
            val dView = (it as? MaterialDialog)?.view
            dView?.contentLayout?.scrollView?.scrollTo(0, 0)
        }
        return dialog
    }

    fun onViewCreated(view: View) {
        val mangaThumbnail = manga.toMangaThumbnail()

        GlideApp.with(view.context)
            .load(mangaThumbnail)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(view.manga_cover)

        val isLocal = manga.source == LocalSource.ID

        if (isLocal) {
            if (manga.title != manga.url) {
                view.title.append(manga.title)
            }
            view.title.hint = "${resources?.getString(R.string.title)}: ${manga.url}"
            view.manga_author.append(manga.author ?: "")
            view.manga_artist.append(manga.artist ?: "")
            view.manga_description.append(manga.description ?: "")
            view.manga_genres_tags.setChips(manga.genre?.split(",")?.map { it.trim() } ?: emptyList())
        } else {
            if (manga.title != manga.originalTitle) {
                view.title.append(manga.title)
            }
            if (manga.author != manga.originalAuthor) {
                view.manga_author.append(manga.author ?: "")
            }
            if (manga.artist != manga.originalArtist) {
                view.manga_artist.append(manga.artist ?: "")
            }
            if (manga.description != manga.originalDescription) {
                view.manga_description.append(manga.description ?: "")
            }
            view.manga_genres_tags.setChips(manga.genre?.split(",")?.map { it.trim() } ?: emptyList())

            view.title.hint = "${resources?.getString(R.string.title)}: ${manga.originalTitle}"
            if (manga.originalAuthor != null) {
                view.manga_author.hint = "Author: ${manga.originalAuthor}"
            }
            if (manga.originalArtist != null) {
                view.manga_artist.hint = "Artist: ${manga.originalArtist}"
            }
            if (manga.originalDescription != null) {
                view.manga_description.hint =
                    "${resources?.getString(R.string.description)}: ${manga.originalDescription?.replace(
                        "\n", " "
                    )?.chop(20)}"
            }
        }
        view.manga_genres_tags.clearFocus()
        view.cover_layout.setOnClickListener {
            infoController.changeCover()
        }
        view.reset_tags.setOnClickListener { resetTags() }
        view.reset_cover.isVisible = !isLocal
        view.reset_cover.setOnClickListener {
            view.context.toast(R.string.cover_reset_toast)
            customCoverUri = null
            willResetCover = true
        }
    }

    private fun resetTags() {
        if (manga.genre.isNullOrBlank() || manga.source == LocalSource.ID) dialogView?.manga_genres_tags?.setChips(
            emptyList()
        )
        else dialogView?.manga_genres_tags?.setChips(manga.originalGenre?.split(", "))
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        GlideApp.with(dialogView!!.context)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(dialogView!!.manga_cover)
        customCoverUri = uri
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        dialogView = null
    }

    private fun onPositiveButtonClick() {
        infoController.presenter.updateMangaInfo(
            dialogView?.title?.text.toString(),
            dialogView?.manga_author?.text.toString(), dialogView?.manga_artist?.text.toString(),
            dialogView?.manga_description?.text.toString(), dialogView?.manga_genres_tags?.getTextStrings(),
            customCoverUri, willResetCover
        )
    }

    private fun ChipGroup.setChips(items: List<String>?) {
        removeAllViews()

        items?.forEach { item ->
            val chip = Chip(context).apply {
                text = item

                isCloseIconVisible = true
                closeIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
                setOnCloseIconClickListener {
                    removeView(this)
                }
            }

            addView(chip)
        }

        val addTagChip = Chip(context).apply {
            setText(R.string.add_tag)

            chipIcon = context.getDrawable(R.drawable.ic_add_24dp)
            chipIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
            textStartPadding = 0F

            setOnClickListener {
                MaterialDialog(context)
                    .title(R.string.add_tag)
                    .input(inputType = InputType.TYPE_CLASS_TEXT)
                    .positiveButton(android.R.string.ok) {
                        val newTag = it.getInputField().text.toString().trimOrNull()

                        if (items != null && newTag != null) setChips(items + listOf(newTag))
                        else if (newTag != null) setChips(listOf(newTag))
                    }
                    .negativeButton(android.R.string.cancel)
                    .show()
            }
        }
        addView(addTagChip)
    }

    private fun ChipGroup.getTextStrings(): List<String>? = children.mapNotNull {
        if (it is Chip && !it.text.toString().contains(context.getString(R.string.add_tag), ignoreCase = true)) {
            it.text.toString()
        } else null
    }.toList()

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}
