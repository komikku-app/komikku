package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput
import exh.util.dropBlank
import exh.util.trimOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMangaDialog : DialogController {

    private lateinit var binding: EditMangaDialogBinding

    private val manga: Manga

    private val infoController
        get() = targetController as MangaController

    private val context: Context get() = binding.root.context

    constructor(target: MangaController, manga: Manga) : super(
        bundleOf(KEY_MANGA to manga.id),
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga = runBlocking { Injekt.get<GetManga>().await(bundle.getLong(KEY_MANGA))!! }
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditMangaDialogBinding.inflate(activity!!.layoutInflater)
        val view = ScrollView(activity!!).apply {
            addView(binding.root)
        }
        onViewCreated()
        return MaterialAlertDialogBuilder(activity!!)
            .setView(view)
            .setPositiveButton(R.string.action_save) { _, _ -> onPositiveButtonClick() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    fun onViewCreated() {
        loadCover()

        val isLocal = manga.source == LocalSource.ID

        val statusAdapter: ArrayAdapter<String> = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                R.string.label_default,
                R.string.ongoing,
                R.string.completed,
                R.string.licensed,
                R.string.publishing_finished,
                R.string.cancelled,
                R.string.on_hiatus,
            ).map { context.getString(it) },
        )

        binding.status.adapter = statusAdapter
        if (manga.status != manga.ogStatus) {
            binding.status.setSelection(
                when (manga.status.toInt()) {
                    SManga.UNKNOWN -> 0
                    SManga.ONGOING -> 1
                    SManga.COMPLETED -> 2
                    SManga.LICENSED -> 3
                    SManga.PUBLISHING_FINISHED, 61 -> 4
                    SManga.CANCELLED, 62 -> 5
                    SManga.ON_HIATUS, 63 -> 6
                    else -> 0
                },
            )
        }

        if (isLocal) {
            if (manga.title != manga.url) {
                binding.title.setText(manga.title)
            }

            binding.title.hint = context.getString(R.string.title_hint, manga.url)
            binding.mangaAuthor.setText(manga.author.orEmpty())
            binding.mangaArtist.setText(manga.artist.orEmpty())
            binding.mangaDescription.setText(manga.description.orEmpty())
            binding.mangaGenresTags.setChips(manga.genre.orEmpty().dropBlank())
        } else {
            if (manga.title != manga.ogTitle) {
                binding.title.append(manga.title)
            }
            if (manga.author != manga.ogAuthor) {
                binding.mangaAuthor.append(manga.author.orEmpty())
            }
            if (manga.artist != manga.ogArtist) {
                binding.mangaArtist.append(manga.artist.orEmpty())
            }
            if (manga.description != manga.ogDescription) {
                binding.mangaDescription.append(manga.description.orEmpty())
            }
            binding.mangaGenresTags.setChips(manga.genre.orEmpty().dropBlank())

            binding.title.hint = context.getString(R.string.title_hint, manga.ogTitle)
            if (manga.ogAuthor != null) {
                binding.mangaAuthor.hint = context.getString(R.string.author_hint, manga.ogAuthor)
            }
            if (manga.ogArtist != null) {
                binding.mangaArtist.hint = context.getString(R.string.artist_hint, manga.ogArtist)
            }
            if (!manga.ogDescription.isNullOrBlank()) {
                binding.mangaDescription.hint =
                    context.getString(
                        R.string.description_hint,
                        manga.ogDescription.replace("\n", " ").chop(20),
                    )
            }
        }
        binding.mangaGenresTags.clearFocus()

        binding.resetTags.clicks()
            .onEach { resetTags() }
            .launchIn(infoController.viewScope)
    }

    private fun resetTags() {
        if (manga.genre.isNullOrEmpty() || manga.source == LocalSource.ID) {
            binding.mangaGenresTags.setChips(emptyList())
        } else {
            binding.mangaGenresTags.setChips(manga.ogGenre.orEmpty())
        }
    }

    private fun loadCover() {
        val radius = context.resources.getDimension(R.dimen.card_radius)
        binding.mangaCover.load(manga) {
            transformations(RoundedCornersTransformation(radius))
        }
    }

    private fun onPositiveButtonClick() {
        infoController.presenter.updateMangaInfo(
            binding.title.text.toString(),
            binding.mangaAuthor.text.toString(),
            binding.mangaArtist.text.toString(),
            binding.mangaDescription.text.toString(),
            binding.mangaGenresTags.getTextStrings(),
            binding.status.selectedItemPosition.let {
                when (it) {
                    1 -> SManga.ONGOING
                    2 -> SManga.COMPLETED
                    3 -> SManga.LICENSED
                    4 -> SManga.PUBLISHING_FINISHED
                    5 -> SManga.CANCELLED
                    6 -> SManga.ON_HIATUS
                    else -> null
                }
            }?.toLong(),
        )
    }

    private fun ChipGroup.setChips(items: List<String>) {
        removeAllViews()

        items.asSequence().map { item ->
            Chip(context).apply {
                text = item

                isCloseIconVisible = true
                closeIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
                setOnCloseIconClickListener {
                    removeView(this)
                }
            }
        }.forEach {
            addView(it)
        }

        val addTagChip = Chip(context).apply {
            setText(R.string.add_tag)

            chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_24dp)?.apply {
                isChipIconVisible = true
                setTint(context.getResourceColor(R.attr.colorAccent))
            }

            clicks().onEach {
                var newTag: String? = null
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.add_tag)
                    .setTextInput {
                        newTag = it.trimOrNull()
                    }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (newTag != null) setChips(items + listOfNotNull(newTag))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }.launchIn(infoController.viewScope)
        }
        addView(addTagChip)
    }

    private fun ChipGroup.getTextStrings(): List<String> = children.mapNotNull {
        if (it is Chip && !it.text.toString().contains(context.getString(R.string.add_tag), ignoreCase = true)) {
            it.text.toString()
        } else null
    }.toList()

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}
