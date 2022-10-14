package exh.md.follows

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.os.bundleOf
import eu.kanade.presentation.browse.BrowseMangadexFollowsScreen
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.DuplicateMangaDialog
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.launchIO

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class MangaDexFollowsController(bundle: Bundle) : BrowseSourceController(bundle) {

    constructor(source: CatalogueSource) : this(
        bundleOf(
            SOURCE_ID_KEY to source.id,
        ),
    )

    override fun createPresenter(): BrowseSourcePresenter {
        return MangaDexFollowsPresenter(args.getLong(SOURCE_ID_KEY))
    }

    @Composable
    override fun ComposeContent() {
        val scope = rememberCoroutineScope()

        BrowseMangadexFollowsScreen(
            presenter = presenter,
            navigateUp = { router.popCurrentController() },
            onDisplayModeChange = { presenter.displayMode = (it) },
            onMangaClick = {
                router.pushController(MangaController(it.id, true))
            },
            onMangaLongClick = { manga ->
                scope.launchIO {
                    val duplicateManga = presenter.getDuplicateLibraryManga(manga)
                    when {
                        manga.favorite -> presenter.dialog = BrowseSourcePresenter.Dialog.RemoveManga(manga)
                        duplicateManga != null -> presenter.dialog = BrowseSourcePresenter.Dialog.AddDuplicateManga(manga, duplicateManga)
                        else -> presenter.addFavorite(manga)
                    }
                }
            },
        )

        val onDismissRequest = { presenter.dialog = null }
        when (val dialog = presenter.dialog) {
            is BrowseSourcePresenter.Dialog.AddDuplicateManga -> {
                DuplicateMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { presenter.addFavorite(dialog.manga) },
                    onOpenManga = { router.pushController(MangaController(dialog.duplicate.id)) },
                    duplicateFrom = presenter.getSourceOrStub(dialog.duplicate),
                )
            }
            is BrowseSourcePresenter.Dialog.RemoveManga -> {
                RemoveMangaDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        presenter.changeMangaFavorite(dialog.manga)
                    },
                    mangaToRemove = dialog.manga,
                )
            }
            is BrowseSourcePresenter.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        router.pushController(CategoryController())
                    },
                    onConfirm = { include, _ ->
                        presenter.changeMangaFavorite(dialog.manga)
                        presenter.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            null -> {}
        }
    }

    override fun initFilterSheet() {
        // No-op: we don't allow filtering in mangadex follows
    }
}
