package eu.kanade.tachiyomi.ui.manga.merged

import android.app.Dialog
import android.os.Bundle
import android.widget.ScrollView
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.manga.interactor.DeleteMergeById
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetMergedMangaById
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EditMergedSettingsDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.toast
import exh.merged.sql.models.MergedMangaReference
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class EditMergedSettingsDialog : DialogController, EditMergedMangaAdapter.EditMergedMangaItemListener {
    private val manga: Manga

    val mergedMangas: MutableList<Pair<Manga?, MergedMangaReference>> = mutableListOf()

    var mergeReference: MergedMangaReference? = null

    lateinit var binding: EditMergedSettingsDialogBinding

    private val getMergedMangaById: GetMergedMangaById by injectLazy()
    private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
    private val deleteMergeById: DeleteMergeById by injectLazy()

    private val mangaController
        get() = targetController as MangaController

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

    private var mergedHeaderAdapter: EditMergedSettingsHeaderAdapter? = null
    private var mergedMangaAdapter: EditMergedMangaAdapter? = null

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditMergedSettingsDialogBinding.inflate(activity!!.layoutInflater)
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
        val mergedManga = runBlocking { getMergedMangaById.await(manga.id) }
        val mergedReferences = runBlocking { getMergedReferencesById.await(manga.id) }
        if (mergedReferences.isEmpty() || mergedReferences.size == 1) {
            activity?.toast(R.string.merged_references_invalid)
            router.popCurrentController()
        }
        mergedMangas += mergedReferences.filter { it.mangaSourceId != MERGED_SOURCE_ID }.map { reference -> mergedManga.firstOrNull { it.id == reference.mangaId } to reference }
        mergeReference = mergedReferences.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }

        val isPriorityOrder = mergeReference?.let { it.chapterSortMode == MergedMangaReference.CHAPTER_SORT_PRIORITY } ?: false

        mergedMangaAdapter = EditMergedMangaAdapter(this, isPriorityOrder)
        mergedHeaderAdapter = EditMergedSettingsHeaderAdapter(this, mergedMangaAdapter!!)

        binding.recycler.adapter = ConcatAdapter(mergedHeaderAdapter, mergedMangaAdapter)
        binding.recycler.layoutManager = LinearLayoutManager(activity!!)

        mergedMangaAdapter?.isHandleDragEnabled = isPriorityOrder

        mergedMangaAdapter?.updateDataSet(mergedMangas.map { it.toModel() })
    }

    private fun onPositiveButtonClick() {
        mangaController.presenter.updateMergeSettings(listOfNotNull(mergeReference) + mergedMangas.map { it.second })
    }

    override fun onItemReleased(position: Int) {
        val mergedMangaAdapter = mergedMangaAdapter ?: return
        mergedMangas.onEach { mergedManga ->
            mergedManga.second.chapterPriority = mergedMangaAdapter.currentItems.indexOfFirst {
                mergedManga.second.id == it.mergedMangaReference.id
            }
        }
    }

    override fun onDeleteClick(position: Int) {
        val mergedMangaAdapter = mergedMangaAdapter ?: return
        val mergeMangaReference = mergedMangaAdapter.currentItems.getOrNull(position)?.mergedMangaReference ?: return

        MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.delete_merged_manga)
            .setMessage(R.string.delete_merged_manga_desc)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launchIO {
                    deleteMergeById.await(mergeMangaReference.id!!)
                }
                dialog?.dismiss()
                mangaController.router.popController(mangaController)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onToggleChapterUpdatesClicked(position: Int) {
        MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.chapter_updates_merged_manga)
            .setMessage(R.string.chapter_updates_merged_manga_desc)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                toggleChapterUpdates(position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleChapterUpdates(position: Int) {
        val adapterReference = mergedMangaAdapter?.currentItems?.getOrNull(position)?.mergedMangaReference
        mergedMangas.firstOrNull { it.second.id != null && it.second.id == adapterReference?.id }?.apply {
            second.getChapterUpdates = !second.getChapterUpdates

            mergedMangaAdapter?.allBoundViewHolders?.firstOrNull { it is EditMergedMangaHolder && it.reference.id == second.id }?.let {
                if (it is EditMergedMangaHolder) {
                    it.updateChapterUpdatesIcon(second.getChapterUpdates)
                }
            } ?: activity!!.toast(R.string.merged_chapter_updates_error)
        } ?: activity!!.toast(R.string.merged_toggle_chapter_updates_find_error)
    }

    override fun onToggleChapterDownloadsClicked(position: Int) {
        MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.download_merged_manga)
            .setMessage(R.string.download_merged_manga_desc)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                toggleChapterDownloads(position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleChapterDownloads(position: Int) {
        val adapterReference = mergedMangaAdapter?.currentItems?.getOrNull(position)?.mergedMangaReference
        mergedMangas.firstOrNull { it.second.id != null && it.second.id == adapterReference?.id }?.apply {
            second.downloadChapters = !second.downloadChapters

            mergedMangaAdapter?.allBoundViewHolders?.firstOrNull { it is EditMergedMangaHolder && it.reference.id == second.id }?.let {
                if (it is EditMergedMangaHolder) {
                    it.updateDownloadChaptersIcon(second.downloadChapters)
                }
            } ?: activity!!.toast(R.string.merged_toggle_download_chapters_error)
        } ?: activity!!.toast(R.string.merged_toggle_download_chapters_find_error)
    }

    private fun Pair<Manga?, MergedMangaReference>.toModel(): EditMergedMangaItem {
        return EditMergedMangaItem(first, second)
    }

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}
