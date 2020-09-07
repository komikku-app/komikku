package eu.kanade.tachiyomi.ui.manga.merged

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.toast
import exh.MERGED_SOURCE_ID
import exh.merged.sql.models.MergedMangaReference
import kotlinx.android.synthetic.main.edit_merged_settings_dialog.view.recycler
import uy.kohesive.injekt.injectLazy

class EditMergedSettingsDialog : DialogController, EditMergedMangaAdapter.EditMergedMangaItemListener {

    private var dialogView: View? = null

    private val manga: Manga

    val mergedMangas: MutableList<Pair<Manga?, MergedMangaReference>> = mutableListOf()

    var mergeReference: MergedMangaReference? = null

    private val db: DatabaseHelper by injectLazy()

    private val mangaController
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
        manga = db.getManga(bundle.getLong(KEY_MANGA))
            .executeAsBlocking()!!
    }

    private var mergedHeaderAdapter: EditMergedSettingsHeaderAdapter? = null
    private var mergedMangaAdapter: EditMergedMangaAdapter? = null

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = MaterialDialog(activity!!).apply {
            customView(viewRes = R.layout.edit_merged_settings_dialog, scrollable = true)
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

        view.recycler.adapter = ConcatAdapter(mergedHeaderAdapter, mergedMangaAdapter)
        view.recycler.layoutManager = LinearLayoutManager(view.context)

        mergedMangaAdapter?.isHandleDragEnabled = isPriorityOrder

        mergedMangaAdapter?.updateDataSet(mergedMangas.map { it.toModel() })
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        dialogView = null
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

        MaterialDialog(dialogView!!.context)
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
        MaterialDialog(dialogView!!.context)
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
        MaterialDialog(dialogView!!.context)
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
