package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.view.View
import android.widget.PopupMenu
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import coil.dispose
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MigrationMangaCardBinding
import eu.kanade.tachiyomi.databinding.MigrationProcessItemBinding
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga.ChapterInfo
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga.SearchResult
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.view.loadAutoPause
import eu.kanade.tachiyomi.util.view.setVectorCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import java.text.DecimalFormat
import java.util.concurrent.CopyOnWriteArrayList

class MigrationProcessHolder(
    view: View,
    private val adapter: MigrationProcessAdapter,
) : FlexibleViewHolder(view, adapter) {
    private var item: MigrationProcessItem? = null
    private val binding = MigrationProcessItemBinding.bind(view)

    private val jobs = CopyOnWriteArrayList<Job>()

    init {
        // We need to post a Runnable to show the popup to make sure that the PopupMenu is
        // correctly positioned. The reason being that the view may change position before the
        // PopupMenu is shown.
        binding.migrationMenu.setOnClickListener { it.post { showPopupMenu(it) } }
        binding.skipManga.setOnClickListener { it.post { adapter.controller.removeManga(item?.manga?.manga?.id ?: return@post) } }
    }

    fun bind(item: MigrationProcessItem) {
        this.item = item
        jobs.removeAll { it.cancel(); true }
        jobs += adapter.controller.viewScope.launchUI {
            val migrateManga = item.manga
            val manga = migrateManga.manga

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
            binding.migrationMangaCardFrom.attachManga(manga, item.manga.sourcesString, item.manga.chapterInfo)
            jobs += binding.migrationMangaCardFrom.root.clicks()
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

            jobs += migrateManga.searchResult
                .onEach { searchResult ->
                    this@MigrationProcessHolder.logcat { (searchResult to (migrateManga.manga.id to this@MigrationProcessHolder.item?.manga?.manga?.id)).toString() }
                    if (migrateManga.manga.id != this@MigrationProcessHolder.item?.manga?.manga?.id ||
                        searchResult == SearchResult.Searching
                    ) {
                        return@onEach
                    }

                    val resultManga = withIOContext {
                        (searchResult as? SearchResult.Result)
                            ?.let { migrateManga.getManga(it) }
                    }
                    if (resultManga != null) {
                        val (sourceName, latestChapter) = withIOContext {
                            val sourceNameAsync = async { migrateManga.getSourceName(resultManga).orEmpty() }
                            val latestChapterAsync = async { migrateManga.getChapterInfo(searchResult as SearchResult.Result) }
                            sourceNameAsync.await() to latestChapterAsync.await()
                        }

                        binding.migrationMangaCardTo.attachManga(resultManga, sourceName, latestChapter)
                        jobs += binding.migrationMangaCardTo.root.clicks()
                            .onEach {
                                adapter.controller.router.pushController(
                                    MangaController(
                                        resultManga.id,
                                        true,
                                    ),
                                )
                            }
                            .launchIn(adapter.controller.viewScope)
                    } else {
                        binding.migrationMangaCardTo.progress.isVisible = false
                        binding.migrationMangaCardTo.title.text = itemView.context
                            .getString(R.string.no_alternatives_found)
                    }

                    binding.migrationMenu.isVisible = true
                    binding.skipManga.isVisible = false
                    adapter.controller.sourceFinished()
                }
                .catch {
                    this@MigrationProcessHolder.logcat(throwable = it) { "Error updating result info" }
                }
                .launchIn(adapter.controller.viewScope)
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

    private fun MigrationMangaCardBinding.attachManga(
        manga: Manga,
        sourceString: String,
        chapterInfo: ChapterInfo,
    ) {
        progress.isVisible = false
        thumbnail.loadAutoPause(manga)

        title.text = if (manga.title.isBlank()) {
            itemView.context.getString(R.string.unknown)
        } else {
            manga.ogTitle
        }

        mangaSourceLabel.text = sourceString

        // For rounded corners
        badges.leftBadges.clipToOutline = true
        badges.rightBadges.clipToOutline = true
        badges.unreadText.isVisible = true
        badges.unreadText.text = chapterInfo.chapterCount.toString()

        if (chapterInfo.latestChapter != null && chapterInfo.latestChapter > 0f) {
            mangaLastChapterLabel.text = itemView.context.getString(
                R.string.latest_,
                DecimalFormat("#.#").format(chapterInfo.latestChapter),
            )
        } else {
            mangaLastChapterLabel.text = itemView.context.getString(
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
        if (mangas.searchResult.value != SearchResult.Searching) {
            popup.menu.findItem(R.id.action_migrate_now).isVisible = true
            popup.menu.findItem(R.id.action_copy_now).isVisible = true
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            adapter.controller.onMenuItemClick(item.manga.manga.id, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }
}
