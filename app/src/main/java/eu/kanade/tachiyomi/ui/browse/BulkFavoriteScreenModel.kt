package eu.kanade.tachiyomi.ui.browse

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.browse.components.RemoveMangaDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.manga.DuplicateMangasDialog
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class BulkFavoriteScreenModel(
    initialState: State = State(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
) : StateScreenModel<BulkFavoriteScreenModel.State>(initialState) {

    fun backHandler() {
        toggleSelectionMode()
    }

    fun toggleSelectionMode() {
        if (state.value.selectionMode) {
            clearSelection()
        }
        mutableState.update { it.copy(selectionMode = !it.selectionMode) }
    }

    private fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun select(manga: Manga) {
        toggleSelection(manga, toSelectedState = true)
    }

    /**
     * @param toSelectedState set to true to only Select, set to false to only Unselect
     */
    fun toggleSelection(manga: Manga, toSelectedState: Boolean? = null) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (toSelectedState != true && list.fastAny { it.id == manga.id }) {
                    list.removeAll { it.id == manga.id }
                } else if (toSelectedState != false && list.none { it.id == manga.id }) {
                    list.add(manga)
                }
            }
            state.copy(
                selection = newSelection,
                selectionMode = newSelection.isNotEmpty(),
            )
        }
    }

    fun reverseSelection(mangas: List<Manga>) {
        mutableState.update { state ->
            val newSelection = mangas.filterNot { manga ->
                state.selection.contains(manga)
            }.toPersistentList()
            state.copy(
                selection = newSelection,
                selectionMode = newSelection.isNotEmpty(),
            )
        }
    }

    /**
     * Called when user click on [BulkSelectionToolbar]'s `Favorite` button.
     * It will then look for any duplicated mangas.
     * - If there is any, it will show the [DuplicateMangasDialog].
     * - If not then it will call the [addFavoriteDuplicate].
     */
    fun addFavorite(startIdx: Int = 0) {
        screenModelScope.launch {
            startRunning()
            val mangaWithDup = getDuplicateLibraryManga(startIdx)
            if (mangaWithDup != null) {
                setDialog(Dialog.AllowDuplicate(mangaWithDup))
            } else {
                addFavoriteDuplicate()
            }
        }
    }

    /**
     * Add mangas to library if there is default category or no category exists.
     * If not, it shows the categories list.
     */
    fun addFavoriteDuplicate(skipAllDuplicates: Boolean = false) {
        screenModelScope.launch {
            val mangaList = if (skipAllDuplicates) getNotDuplicateLibraryMangas() else state.value.selection
            if (mangaList.isEmpty()) {
                stopRunning()
                toggleSelectionMode()
                return@launch
            }
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    stopRunning()
                    setMangasCategories(mangaList, listOf(defaultCategory.id), emptyList())
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    stopRunning()
                    // Automatic 'Default' or no categories
                    setMangasCategories(mangaList, emptyList(), emptyList())
                }

                else -> {
                    // Get indexes of the common categories to preselect.
                    val common = getCommonCategories(mangaList)
                    // Get indexes of the mix categories to preselect.
                    val mix = getMixCategories(mangaList)
                    val preselected = categories
                        .map {
                            when (it) {
                                in common -> CheckboxState.State.Checked(it)
                                in mix -> CheckboxState.TriState.Exclude(it)
                                else -> CheckboxState.State.None(it)
                            }
                        }
                        .toImmutableList()
                    stopRunning()
                    setDialog(Dialog.ChangeMangasCategory(mangaList, preselected))
                }
            }
        }
    }

    private suspend fun getNotDuplicateLibraryMangas(): List<Manga> {
        return state.value.selection.filterNot { manga ->
            getDuplicateLibraryManga.await(manga).isNotEmpty()
        }
    }

    private suspend fun getDuplicateLibraryManga(startIdx: Int = 0): Pair<Int, Manga>? {
        val mangas = state.value.selection
        mangas.fastForEachIndexed { index, manga ->
            if (index < startIdx) return@fastForEachIndexed
            val dup = getDuplicateLibraryManga.await(manga)
            if (dup.isEmpty()) return@fastForEachIndexed
            return Pair(index, dup.first())
        }
        return null
    }

    fun removeDuplicateSelectedManga(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                list.removeAt(index)
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangasCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        screenModelScope.launchNonCancellable {
            startRunning()
            mangaList.fastForEach { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                moveMangaToCategoriesAndAddToLibrary(manga, categoryIds)
            }
            stopRunning()
        }
        toggleSelectionMode()
    }

    private fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            .orEmpty()
    }

    private suspend fun getDuplicateLibraryManga(manga: Manga): Manga? {
        return getDuplicateLibraryManga.await(manga).getOrNull(0)
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        val source = sourceManager.getOrStub(manga.source)

        screenModelScope.launch {
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )
            // TODO: also allow deleting chapters when remove favorite (just like in [MangaScreenModel])
            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)
            }

            updateManga.await(new.toMangaUpdate())
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)
                    changeMangaFavorite(manga)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)
                    changeMangaFavorite(manga)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(manga.id).map { it.id }
                    setDialog(
                        Dialog.ChangeMangaCategory(
                            manga,
                            categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            }
        }
    }

    fun addRemoveManga(manga: Manga, haptic: HapticFeedback? = null) {
        screenModelScope.launchIO {
            val duplicateManga = getDuplicateLibraryManga(manga)
            when {
                manga.favorite -> setDialog(
                    Dialog.RemoveManga(manga),
                )
                duplicateManga != null -> setDialog(
                    Dialog.AddDuplicateManga(
                        manga,
                        duplicateManga,
                    ),
                )
                else -> addFavorite(manga)
            }
            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun setDialog(dialog: Dialog?) {
        mutableState.update {
            it.copy(dialog = dialog)
        }
    }

    fun dismissDialog() {
        mutableState.update {
            it.copy(dialog = null)
        }
    }

    private fun startRunning() {
        mutableState.update {
            it.copy(isRunning = true)
        }
    }

    fun stopRunning() {
        mutableState.update {
            it.copy(isRunning = false)
        }
    }

    interface Dialog {
        data class RemoveManga(val manga: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicate: Manga) : Dialog
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState.State<Category>>,
        ) : Dialog
        data class ChangeMangasCategory(
            val mangas: List<Manga>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class AllowDuplicate(val duplicatedManga: Pair<Int, Manga>) : Dialog
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val selection: PersistentList<Manga> = persistentListOf(),
        val selectionMode: Boolean = false,
        val isRunning: Boolean = false,
    )
}

@Composable
fun AddDuplicateMangaDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as BulkFavoriteScreenModel.Dialog.AddDuplicateManga

    DuplicateMangaDialog(
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onConfirm = { bulkFavoriteScreenModel.addFavorite(dialog.manga) },
        onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
        onMigrate = {
            PreMigrationScreen.navigateToMigration(
                Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                navigator,
                dialog.duplicate.id,
                dialog.manga.id,
            )
        },
        duplicate = dialog.duplicate,
    )
}

@Composable
fun RemoveMangaDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as BulkFavoriteScreenModel.Dialog.RemoveManga

    RemoveMangaDialog(
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onConfirm = {
            bulkFavoriteScreenModel.changeMangaFavorite(dialog.manga)
        },
        mangaToRemove = dialog.manga,
    )
}

@Composable
fun ChangeMangaCategoryDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as BulkFavoriteScreenModel.Dialog.ChangeMangaCategory

    ChangeCategoryDialog(
        initialSelection = dialog.initialSelection,
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onEditCategories = { navigator.push(CategoryScreen()) },
        onConfirm = { include, _ ->
            bulkFavoriteScreenModel.changeMangaFavorite(dialog.manga)
            bulkFavoriteScreenModel.moveMangaToCategories(dialog.manga, include)
        },
    )
}

@Composable
fun ChangeMangasCategoryDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as BulkFavoriteScreenModel.Dialog.ChangeMangasCategory

    ChangeCategoryDialog(
        initialSelection = dialog.initialSelection,
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onEditCategories = { navigator.push(CategoryScreen()) },
        onConfirm = { include, exclude ->
            bulkFavoriteScreenModel.setMangasCategories(dialog.mangas, include, exclude)
        },
    )
}

@Composable
fun AllowDuplicateDialog(bulkFavoriteScreenModel: BulkFavoriteScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val dialog = bulkFavoriteState.dialog as BulkFavoriteScreenModel.Dialog.AllowDuplicate

    DuplicateMangasDialog(
        onDismissRequest = bulkFavoriteScreenModel::dismissDialog,
        onAllowAllDuplicate = bulkFavoriteScreenModel::addFavoriteDuplicate,
        onSkipAllDuplicate = {
            bulkFavoriteScreenModel.addFavoriteDuplicate(skipAllDuplicates = true)
        },
        onOpenManga = {
            navigator.push(MangaScreen(dialog.duplicatedManga.second.id))
        },
        onAllowDuplicate = {
            bulkFavoriteScreenModel.addFavorite(startIdx = dialog.duplicatedManga.first + 1)
        },
        onSkipDuplicate = {
            bulkFavoriteScreenModel.removeDuplicateSelectedManga(index = dialog.duplicatedManga.first)
            bulkFavoriteScreenModel.addFavorite(startIdx = dialog.duplicatedManga.first)
        },
        mangaName = dialog.duplicatedManga.second.title,
        stopRunning = bulkFavoriteScreenModel::stopRunning,
        duplicate = dialog.duplicatedManga.second,
    )
}

@Composable
fun bulkSelectionButton(
    isRunning: Boolean,
    toggleSelectionMode: () -> Unit,
) = AppBar.Action(
    title = stringResource(KMR.strings.action_bulk_select),
    icon = Icons.Outlined.Checklist,
    iconTint = MaterialTheme.colorScheme.primary.takeIf { isRunning },
    onClick = toggleSelectionMode,
)
