package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.view.View
import android.widget.PopupMenu
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import coil.dispose
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MigrationMangaCardBinding
import eu.kanade.tachiyomi.databinding.MigrationProcessItemBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.view.loadAutoPause
import eu.kanade.tachiyomi.util.view.setVectorCompat
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat

class MigrationProcessHolder(
    private val view: View,
    private val adapter: MigrationProcessAdapter,
) : FlexibleViewHolder(view, adapter) {
    private val sourceManager: SourceManager by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getChapterByMangaId: GetChapterByMangaId by injectLazy()
    private val getMergedReferencesById: GetMergedReferencesById by injectLazy()

    private var item: MigrationProcessItem? = null
    private val binding = MigrationProcessItemBinding.bind(view)

    init {
        // We need to post a Runnable to show the popup to make sure that the PopupMenu is
        // correctly positioned. The reason being that the view may change position before the
        // PopupMenu is shown.
        binding.migrationMenu.setOnClickListener { it.post { showPopupMenu(it) } }
        binding.skipManga.setOnClickListener { it.post { adapter.removeManga(bindingAdapterPosition) } }
    }

    fun bind(item: MigrationProcessItem) {
        this.item = item
        launchUI {
            val manga = item.manga.manga()
            val source = item.manga.mangaSource()

            binding.migrationMenu.setVectorCompat(
                R.drawable.ic_more_24dp,
                R.attr.colorOnPrimary,
            )
            binding.skipManga.setVectorCompat(
                R.drawable.ic_close_24dp,
                R.attr.colorOnPrimary,
            )
            binding.migrationMenu.isInvisible = true
            binding.skipManga.isVisible = true
            binding.migrationMangaCardTo.resetManga()
            if (manga != null) {
                binding.migrationMangaCardFrom.attachManga(manga, source)
                binding.migrationMangaCardFrom.root.clicks()
                    .onEach {
                        adapter.controller.router.pushController(
                            MangaController(
                                manga.id,
                                true,
                            ),
                        )
                    }
                    .launchIn(adapter.controller.viewScope)

                /*launchUI {
                    item.manga.progress.asFlow().collect { (max, progress) ->
                        withUIContext {
                            migration_manga_card_to.search_progress.let { progressBar ->
                                progressBar.max = max
                                progressBar.progress = progress
                            }
                        }
                    }
                }*/

                val searchResult = item.manga.searchResult.get()?.let {
                    getManga.await(it)
                }
                val resultSource = searchResult?.source?.let {
                    sourceManager.get(it)
                }

                if (item.manga.mangaId != this@MigrationProcessHolder.item?.manga?.mangaId ||
                    item.manga.migrationStatus == MigrationStatus.RUNNING
                ) {
                    return@launchUI
                }
                if (searchResult != null && resultSource != null) {
                    binding.migrationMangaCardTo.attachManga(searchResult, resultSource)
                    binding.migrationMangaCardTo.root.clicks()
                        .onEach {
                            adapter.controller.router.pushController(
                                MangaController(
                                    searchResult.id,
                                    true,
                                ),
                            )
                        }
                        .launchIn(adapter.controller.viewScope)
                } else {
                    if (adapter.hideNotFound) {
                        adapter.removeManga(bindingAdapterPosition)
                    } else {
                        binding.migrationMangaCardTo.progress.isVisible = false
                        binding.migrationMangaCardTo.title.text = view.context.applicationContext
                            .getString(R.string.no_alternatives_found)
                    }
                }
                binding.migrationMenu.isVisible = true
                binding.skipManga.isVisible = false
                adapter.sourceFinished()
            }
        }
    }

    private fun MigrationMangaCardBinding.resetManga() {
        progress.isVisible = true
        thumbnail.dispose()
        thumbnail.setImageDrawable(null)
        title.text = ""
        mangaSourceLabel.text = ""
        badges.unreadText.text = ""
        badges.unreadText.isVisible = false
        mangaLastChapterLabel.text = ""
    }

    private suspend fun MigrationMangaCardBinding.attachManga(manga: Manga, source: Source) {
        progress.isVisible = false
        thumbnail.loadAutoPause(manga)

        title.text = if (manga.title.isBlank()) {
            view.context.getString(R.string.unknown)
        } else {
            manga.ogTitle
        }

        mangaSourceLabel.text = if (source.id == MERGED_SOURCE_ID) {
            getMergedReferencesById.await(manga.id).map {
                sourceManager.getOrStub(it.mangaSourceId).toString()
            }.distinct().joinToString()
        } else {
            source.toString()
        }

        val chapters = getChapterByMangaId.await(manga.id)
        // For rounded corners
        badges.leftBadges.clipToOutline = true
        badges.rightBadges.clipToOutline = true
        badges.unreadText.isVisible = true
        badges.unreadText.text = chapters.size.toString()
        val latestChapter = chapters.maxOfOrNull { it.chapterNumber } ?: -1f

        if (latestChapter > 0f) {
            mangaLastChapterLabel.text = root.context.getString(
                R.string.latest_,
                DecimalFormat("#.#").format(latestChapter),
            )
        } else {
            mangaLastChapterLabel.text = root.context.getString(
                R.string.latest_,
                root.context.getString(R.string.unknown),
            )
        }
    }

    private fun showPopupMenu(view: View) {
        val item = adapter.getItem(bindingAdapterPosition) ?: return

        // Create a PopupMenu, giving it the clicked view for an anchor
        val popup = PopupMenu(view.context, view)

        // Inflate our menu resource into the PopupMenu's Menu
        popup.menuInflater.inflate(R.menu.migration_single, popup.menu)

        val mangas = item.manga

        popup.menu.findItem(R.id.action_search_manually).isVisible = true
        // Hide download and show delete if the chapter is downloaded
        if (mangas.searchResult.content != null) {
            popup.menu.findItem(R.id.action_migrate_now).isVisible = true
            popup.menu.findItem(R.id.action_copy_now).isVisible = true
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            adapter.menuItemListener.onMenuItemClick(bindingAdapterPosition, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }
}
