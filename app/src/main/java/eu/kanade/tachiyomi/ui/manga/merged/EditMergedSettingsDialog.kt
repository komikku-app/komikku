package eu.kanade.tachiyomi.ui.manga.merged

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.EditMergedSettingsDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.toast
import exh.merged.sql.models.MergedMangaReference
import exh.source.MERGED_SOURCE_ID
import uy.kohesive.injekt.injectLazy

class EditMergedSettingsDialog : DialogController, EditMergedMangaAdapter.EditMergedMangaItemListener {
    private val manga: Manga

    val mergedMangas: MutableList<Pair<Manga?, MergedMangaReference>> = mutableListOf()

    var mergeReference: MergedMangaReference? = null

    lateinit var binding: EditMergedSettingsDialogBinding

    private val db: DatabaseHelper by injectLazy()

    private val mangaController
        get() = targetController as MangaController

    constructor(target: MangaController, manga: Manga) : super(
        bundleOf(KEY_MANGA to manga.id!!)
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga = db.getManga(bundle.getLong(KEY_MANGA))
            .executeAsBlocking()!!
    }

    private var mergedHeaderAdapter: EditMergedSettingsHeaderAdapter? = null
    private var mergedMangaAdapter: EditMergedMangaAdapter? = null

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditMergedSettingsDialogBinding.inflate(activity!!.layoutInflater)
        val dialog = MaterialDialog(activity!!)
            .customView(view = binding.root, scrollable = true)
            .negativeButton(android.R.string.cancel)
            .positiveButton(R.string.action_save) { onPositiveButtonClick() }

        onViewCreated()
        return dialog
    }

    fun onViewCreated() {
        val mergedManga = db.getMergedMangas(manga.id!!).executeAsBlocking()
        val mergedReferences = db.getMergedMangaReferences(manga.id!!).executeAsBlocking()
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
        mangaController.presenter.updateMergeSettings(mergeReference, mergedMangas.map { it.second })
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

        MaterialDialog(activity!!)
            .title(R.string.delete_merged_manga)
            .message(R.string.delete_merged_manga_desc)
            .positiveButton(android.R.string.ok) {
                db.deleteMergedManga(mergeMangaReference).executeAsBlocking()
                dialog?.dismiss()
                mangaController.router.popController(mangaController)
            }
            .negativeButton(android.R.string.cancel)
            .show()
    }

    override fun onToggleChapterUpdatesClicked(position: Int) {
        MaterialDialog(activity!!)
            .title(R.string.chapter_updates_merged_manga)
            .message(R.string.chapter_updates_merged_manga_desc)
            .positiveButton(android.R.string.ok) {
                toggleChapterUpdates(position)
            }
            .negativeButton(android.R.string.cancel)
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
        MaterialDialog(activity!!)
            .title(R.string.download_merged_manga)
            .message(R.string.download_merged_manga_desc)
            .positiveButton(android.R.string.ok) {
                toggleChapterDownloads(position)
            }
            .negativeButton(android.R.string.cancel)
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
