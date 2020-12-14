package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import androidx.core.content.ContextCompat
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
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.toast
import exh.util.trimOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMangaDialog : DialogController {

    private lateinit var binding: EditMangaDialogBinding

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
        binding = EditMangaDialogBinding.inflate(activity!!.layoutInflater)
        val dialog = MaterialDialog(activity!!).apply {
            customView(view = binding.root, scrollable = true)
            negativeButton(android.R.string.cancel)
            positiveButton(R.string.action_save) { onPositiveButtonClick() }
        }
        onViewCreated()
        return dialog
    }

    fun onViewCreated() {
        val mangaThumbnail = manga.toMangaThumbnail()

        GlideApp.with(binding.root.context)
            .load(mangaThumbnail)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(binding.mangaCover)

        val isLocal = manga.source == LocalSource.ID

        if (isLocal) {
            if (manga.title != manga.url) {
                binding.title.append(manga.title)
            }
            binding.title.hint = "${resources?.getString(R.string.title)}: ${manga.url}"
            binding.mangaAuthor.append(manga.author.orEmpty())
            binding.mangaArtist.append(manga.artist.orEmpty())
            binding.mangaDescription.append(manga.description.orEmpty())
            binding.mangaGenresTags.setChips(manga.getGenres())
        } else {
            if (manga.title != manga.originalTitle) {
                binding.title.append(manga.title)
            }
            if (manga.author != manga.originalAuthor) {
                binding.mangaAuthor.append(manga.author.orEmpty())
            }
            if (manga.artist != manga.originalArtist) {
                binding.mangaArtist.append(manga.artist.orEmpty())
            }
            if (manga.description != manga.originalDescription) {
                binding.mangaDescription.append(manga.description.orEmpty())
            }
            binding.mangaGenresTags.setChips(manga.getGenres())

            binding.title.hint = "${resources?.getString(R.string.title)}: ${manga.originalTitle}"
            if (manga.originalAuthor != null) {
                binding.mangaAuthor.hint = "Author: ${manga.originalAuthor}"
            }
            if (manga.originalArtist != null) {
                binding.mangaArtist.hint = "Artist: ${manga.originalArtist}"
            }
            if (manga.originalDescription != null) {
                binding.mangaDescription.hint =
                    "${resources?.getString(R.string.description)}: ${manga.originalDescription?.replace(
                        "\n",
                        " "
                    )?.chop(20)}"
            }
        }
        binding.mangaGenresTags.clearFocus()
        binding.coverLayout.clicks()
            .onEach { infoController.changeCover() }
            .launchIn(infoController.scope)
        binding.resetTags.clicks()
            .onEach { resetTags() }
            .launchIn(infoController.scope)
        binding.resetCover.isVisible = !isLocal
        binding.resetCover.clicks()
            .onEach {
                binding.root.context.toast(R.string.cover_reset_toast)
                customCoverUri = null
                willResetCover = true
            }.launchIn(infoController.scope)
    }

    private fun resetTags() {
        if (manga.genre.isNullOrBlank() || manga.source == LocalSource.ID) binding.mangaGenresTags.setChips(
            emptyList()
        )
        else binding.mangaGenresTags.setChips(manga.getOriginalGenres())
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        GlideApp.with(binding.root.context)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .centerCrop()
            .into(binding.mangaCover)
        customCoverUri = uri
    }

    private fun onPositiveButtonClick() {
        infoController.presenter.updateMangaInfo(
            binding.title.text.toString(),
            binding.mangaAuthor.text.toString(),
            binding.mangaArtist.text.toString(),
            binding.mangaDescription.text.toString(),
            binding.mangaGenresTags.getTextStrings(),
            customCoverUri,
            willResetCover
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

            chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_24dp)
            chipIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
            textStartPadding = 0F

            clicks().onEach {
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
            }.launchIn(infoController.scope)
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
