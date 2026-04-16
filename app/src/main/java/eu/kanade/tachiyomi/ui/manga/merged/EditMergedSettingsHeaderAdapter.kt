package eu.kanade.tachiyomi.ui.manga.merged

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.presentation.components.SpinnerAdapter
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.databinding.EditMergedSettingsHeaderBinding
import exh.log.xLogD
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.injectLazy

class EditMergedSettingsHeaderAdapter(
    private val state: EditMergedSettingsState,
    adapter: EditMergedMangaAdapter,
    // KMK -->
    private val colorScheme: AndroidViewColorScheme,
    // KMK <--
) : RecyclerView.Adapter<EditMergedSettingsHeaderAdapter.HeaderViewHolder>() {

    private val sourceManager: SourceManager by injectLazy()

    val editMergedMangaItemSortingListener: SortingListener = adapter

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = EditMergedSettingsHeaderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return HeaderViewHolder(binding)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    inner class HeaderViewHolder(private val binding: EditMergedSettingsHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            val view = itemView
            // KMK -->
            // val dedupeAdapter: ArrayAdapter<String> = ArrayAdapter(
            val dedupeAdapter = SpinnerAdapter(
                view.context,
                android.R.layout.simple_spinner_dropdown_item,
                // KMK <--
                listOfNotNull(
                    itemView.context.stringResource(SYMR.strings.dedupe_priority),
                    itemView.context.stringResource(SYMR.strings.dedupe_most_chapters),
                    itemView.context.stringResource(SYMR.strings.dedupe_highest_chapter),
                ),
                // KMK -->
                colorScheme,
                // KMK <--
            )

            // Clear listeners before setting values to prevent feedback loops during re-bind
            binding.dedupeModeSpinner.onItemSelectedListener = null
            binding.dedupeSwitch.setOnCheckedChangeListener(null)
            binding.mangaInfoSpinner.onItemSelectedListener = null

            binding.dedupeModeSpinner.adapter = dedupeAdapter
            state.mergeReference?.let {
                binding.dedupeModeSpinner.setSelection(
                    when (it.chapterSortMode) {
                        MergedMangaReference.CHAPTER_SORT_PRIORITY -> 0
                        MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS -> 1
                        MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER -> 2
                        else -> 0
                    },
                )
            }

            binding.dedupeModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    // Only update state if deduplication is actually enabled
                    if (!binding.dedupeSwitch.isChecked) return

                    val newMode = when (position) {
                        0 -> MergedMangaReference.CHAPTER_SORT_PRIORITY
                        1 -> MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS
                        2 -> MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER
                        else -> MergedMangaReference.CHAPTER_SORT_NONE
                    }

                    if (state.mergeReference?.chapterSortMode == newMode) return

                    state.mergeReference = state.mergeReference?.copy(
                        chapterSortMode = newMode,
                    )
                    xLogD(state.mergeReference?.chapterSortMode)

                    // Set Spinner's selected item's background color to transparent
                    if (view != null) (view as TextView).setBackgroundColor(Color.TRANSPARENT)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    if (!binding.dedupeSwitch.isChecked) return
                    state.mergeReference = state.mergeReference?.copy(
                        chapterSortMode = MergedMangaReference.CHAPTER_SORT_NONE,
                    )
                }
            }

            val mergedMangas = state.mergedMangas

            // KMK -->
            // val mangaInfoAdapter: ArrayAdapter<String> = ArrayAdapter(
            val mangaInfoAdapter = SpinnerAdapter(
                view.context,
                android.R.layout.simple_spinner_dropdown_item,
                // KMK <--
                mergedMangas.map {
                    sourceManager.getOrStub(it.second.mangaSourceId).toString() + " " + it.first?.title
                },
                // KMK -->
                colorScheme,
                // KMK <--
            )
            binding.mangaInfoSpinner.adapter = mangaInfoAdapter

            mergedMangas.indexOfFirst { it.second.isInfoManga }.let {
                val selection = if (it != -1) it else 0
                binding.mangaInfoSpinner.setSelection(selection)
            }

            binding.mangaInfoSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    state.mergedMangas = state.mergedMangas.map { (manga, reference) ->
                        manga to reference.copy(
                            isInfoManga = reference.id == mergedMangas.getOrNull(position)?.second?.id,
                        )
                    }

                    // Set Spinner's selected item's background color to transparent
                    if (view != null) (view as TextView).setBackgroundColor(Color.TRANSPARENT)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    mergedMangas.find { it.second.isInfoManga }?.second?.let { newInfoManga ->
                        state.mergedMangas = state.mergedMangas.map { (manga, reference) ->
                            manga to reference.copy(
                                isInfoManga = reference.id == newInfoManga.id,
                            )
                        }
                    }
                }
            }

            binding.dedupeSwitch.isChecked = state.mergeReference?.let {
                it.chapterSortMode != MergedMangaReference.CHAPTER_SORT_NONE
            } ?: false
            binding.dedupeSwitch.setOnCheckedChangeListener { _, isChecked ->
                binding.dedupeModeSpinner.isEnabled = isChecked
                binding.dedupeModeSpinner.alpha = when (isChecked) {
                    true -> 1F
                    false -> 0.5F
                }
                state.mergeReference = state.mergeReference?.copy(
                    chapterSortMode = when (isChecked) {
                        true -> {
                            when (binding.dedupeModeSpinner.selectedItemPosition) {
                                1 -> MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS
                                2 -> MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER
                                else -> MergedMangaReference.CHAPTER_SORT_PRIORITY
                            }
                        }
                        false -> MergedMangaReference.CHAPTER_SORT_NONE
                    },
                )
            }

            binding.dedupeModeSpinner.isEnabled = binding.dedupeSwitch.isChecked
            binding.dedupeModeSpinner.alpha = when (binding.dedupeSwitch.isChecked) {
                true -> 1F
                false -> 0.5F
            }

            // Always enable priority sorting independently of deduplication settings
            editMergedMangaItemSortingListener.onSetPrioritySort(canMove())

            // KMK -->
            binding.dedupeSwitchLabel.setTextColor(colorScheme.textColor)
            binding.dedupeModeLabel.setTextColor(colorScheme.primary)
            binding.mangaInfoLabel.setTextColor(colorScheme.primary)

            binding.dedupeSwitch.trackTintList = colorScheme.trackTintList
            binding.dedupeSwitch.thumbTintList = colorScheme.thumbTintList

            // Set Spinner's dropdown caret color
            binding.dedupeModeSpinner.backgroundTintList = ColorStateList.valueOf(colorScheme.iconColor)
            binding.mangaInfoSpinner.backgroundTintList = ColorStateList.valueOf(colorScheme.iconColor)
            // KMK <--
        }
    }

    fun canMove() = true

    interface SortingListener {
        fun onSetPrioritySort(isPriorityOrder: Boolean)
    }
}
