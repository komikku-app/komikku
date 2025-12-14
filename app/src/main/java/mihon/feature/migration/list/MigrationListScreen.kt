package mihon.feature.migration.list

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.toast
import exh.util.overEq
import exh.util.underEq
import kotlinx.collections.immutable.persistentListOf
import mihon.feature.migration.config.MigrationConfigScreen
import mihon.feature.migration.config.MigrationConfigScreenSheet
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationMangaDialog
import mihon.feature.migration.list.components.MigrationProgressDialog
import mihon.feature.migration.list.models.MigratingManga
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.sy.SYMR

/**
 * Screen showing a list of pair of current-target manga entries being migrated.
 */
class MigrationListScreen(private val mangaIds: List<Long>, private val extraSearchQuery: String?) : Screen() {

    var matchOverride: Pair<Long, Long>? = null

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrationListScreenModel(mangaIds, extraSearchQuery) }
        val context = LocalContext.current
        val items by screenModel.migratingItems.collectAsState()
        val migrationComplete by screenModel.migrationDone.collectAsState()
        val finishedCount by screenModel.finishedCount.collectAsState()
        val dialog by screenModel.dialog.collectAsState()
        val migrateProgress by screenModel.migratingProgress.collectAsState()

        LaunchedEffect(matchOverride) {
            if (matchOverride != null) {
                val (oldId, newId) = matchOverride!!
                screenModel.useMangaForMigration(context, newId, oldId)
                matchOverride = null
            }
        }

        LaunchedEffect(screenModel) {
            screenModel.navigateOut.collect {
                if (items.orEmpty().size == 1 && navigator.items.any { it is MangaScreen }) {
                    val mangaId = (items.orEmpty().firstOrNull()?.searchResult?.value as? MigratingManga.SearchResult.Success)?.id
                    withUIContext {
                        if (mangaId != null) {
                            val newStack = navigator.items.filter {
                                it !is MangaScreen &&
                                    it !is MigrationListScreen &&
                                    it !is MigrationConfigScreen
                            } + MangaScreen(mangaId)
                            navigator replaceAll newStack.first()
                            navigator.push(newStack.drop(1))

                            // need to set the navigator in a pop state to dispose of everything properly
                            navigator.push(this@MigrationListScreen)
                            navigator.pop()
                        } else {
                            navigator.pop()
                        }
                    }
                } else {
                    withUIContext {
                        navigator.pop()
                    }
                }
            }
        }

        LaunchedEffect(items) {
            if (items?.isEmpty() == true) {
                val manualMigrations = screenModel.manualMigrations.value
                context.toast(
                    context.pluralStringResource(
                        SYMR.plurals.entry_migrated,
                        manualMigrations,
                        manualMigrations,
                    ),
                )
                if (!screenModel.hideUnmatched) {
                    navigator.pop()
                }
            }
        }
        MigrationListScreenContent(
            items = items ?: persistentListOf(),
            migrationComplete = migrationComplete,
            finishedCount = finishedCount,
            getManga = screenModel::getManga,
            getChapterInfo = screenModel::getChapterInfo,
            getSourceName = screenModel::getSourceName,
            onItemClick = {
                navigator.push(MangaScreen(it.id, true))
            },
            onSearchManually = { migrationItem ->
                navigator push MigrateSearchScreen(migrationItem.manga.id)
            },
            onSkip = { screenModel.removeManga(it) },
            onMigrate = { screenModel.migrateNow(it, true) },
            onCopy = { screenModel.migrateNow(it, false) },
            openMigrationDialog = screenModel::showMigrateDialog,
            // KMK -->
            onCancel = { screenModel.cancelManga(it) },
            navigateUp = { navigator.pop() },
            openOptionsDialog = screenModel::openOptionsDialog,
            // KMK <--
        )

        val onDismissRequest = { screenModel.dialog.value = null }
        when (
            @Suppress("NAME_SHADOWING")
            val dialog = dialog
        ) {
            is MigrationListScreenModel.Dialog.Migrate -> {
                MigrationMangaDialog(
                    onDismissRequest = onDismissRequest,
                    copy = dialog.copy,
                    totalCount = dialog.totalCount,
                    skippedCount = dialog.skippedCount,
                    copyManga = screenModel::copyMangas,
                    onMigrate = screenModel::migrateMangas,
                )
            }
            MigrationListScreenModel.Dialog.Exit -> {
                MigrationExitDialog(
                    onDismissRequest = onDismissRequest,
                    exitMigration = navigator::pop,
                )
            }
            // KMK -->
            MigrationListScreenModel.Dialog.Options -> {
                MigrationConfigScreenSheet(
                    preferences = screenModel.preferences,
                    onDismissRequest = onDismissRequest,
                    onStartMigration = { _ ->
                        onDismissRequest()
                        screenModel.updateOptions()
                    },
                    fullSettings = false,
                )
            }
            // KMK <--
            null -> Unit
        }

        if (!migrateProgress.isNaN() && migrateProgress overEq 0f && migrateProgress underEq 1f) {
            MigrationProgressDialog(
                progress = migrateProgress,
                exitMigration = screenModel::cancelMigrate,
            )
        }

        BackHandler(true) {
            screenModel.dialog.value = MigrationListScreenModel.Dialog.Exit
        }
    }
}
