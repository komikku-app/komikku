package eu.kanade.tachiyomi.ui.manga.merged

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.databinding.EditMergedSettingsHeaderBinding
import exh.log.xLogD
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.injectLazy

class EditMergedSettingsHeaderAdapter(private val state: EditMergedSettingsState, adapter: EditMergedMangaAdapter) : RecyclerView.Adapter<EditMergedSettingsHeaderAdapter.HeaderViewHolder>() {

    private val sourceManager: SourceManager by injectLazy()

    private lateinit var binding: EditMergedSettingsHeaderBinding

    val editMergedMangaItemSortingListener: SortingListener = adapter

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        binding = EditMergedSettingsHeaderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return HeaderViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val dedupeAdapter: ArrayAdapter<String> = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_item,
                listOfNotNull(
                    itemView.context.stringResource(SYMR.strings.no_dedupe),
                    itemView.context.stringResource(SYMR.strings.dedupe_priority),
                    itemView.context.stringResource(SYMR.strings.dedupe_most_chapters),
                    itemView.context.stringResource(SYMR.strings.dedupe_highest_chapter),
                ),
            )
            dedupeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.dedupeModeSpinner.adapter = dedupeAdapter
            state.mergeReference?.let {
                binding.dedupeModeSpinner.setSelection(
                    when (it.chapterSortMode) {
                        MergedMangaReference.CHAPTER_SORT_NO_DEDUPE -> 0
                        MergedMangaReference.CHAPTER_SORT_PRIORITY -> 1
                        MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS -> 2
                        MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER -> 3
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
                    state.mergeReference = state.mergeReference?.copy(
                        chapterSortMode = when (position) {
                            0 -> MergedMangaReference.CHAPTER_SORT_NO_DEDUPE
                            1 -> MergedMangaReference.CHAPTER_SORT_PRIORITY
                            2 -> MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS
                            3 -> MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER
                            else -> MergedMangaReference.CHAPTER_SORT_NO_DEDUPE
                        },
                    )
                    xLogD(state.mergeReference?.chapterSortMode)
                    editMergedMangaItemSortingListener.onSetPrioritySort(canMove())
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    state.mergeReference = state.mergeReference?.copy(
                        chapterSortMode = MergedMangaReference.CHAPTER_SORT_NO_DEDUPE,
                    )
                }
            }

            val mergedMangas = state.mergedMangas

            val mangaInfoAdapter: ArrayAdapter<String> = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_item,
                mergedMangas.map {
                    sourceManager.getOrStub(it.second.mangaSourceId).toString() + " " + it.first?.title
                },
            )
            mangaInfoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.mangaInfoSpinner.adapter = mangaInfoAdapter

            mergedMangas.indexOfFirst { it.second.isInfoManga }.let {
                if (it != -1) {
                    binding.mangaInfoSpinner.setSelection(it)
                } else {
                    binding.mangaInfoSpinner.setSelection(0)
                }
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
                        true -> MergedMangaReference.CHAPTER_SORT_NO_DEDUPE
                        false -> MergedMangaReference.CHAPTER_SORT_NONE
                    },
                )

                if (isChecked) binding.dedupeModeSpinner.setSelection(0)
            }

            binding.dedupeModeSpinner.isEnabled = binding.dedupeSwitch.isChecked
            binding.dedupeModeSpinner.alpha = when (binding.dedupeSwitch.isChecked) {
                true -> 1F
                false -> 0.5F
            }
        }
    }

    fun canMove() =
        state.mergeReference?.let { it.chapterSortMode == MergedMangaReference.CHAPTER_SORT_PRIORITY } ?: false

    interface SortingListener {
        fun onSetPrioritySort(isPriorityOrder: Boolean)
    }
}
