package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConflictDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoCreateDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoDeleteDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionReposScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { ExtensionReposScreenModel() }
        val state by screenModel.state.collectAsState()

        val scope = rememberCoroutineScope()
        val sourcePreferences = Injekt.get<SourcePreferences>()
        val disabledRepos by remember { sourcePreferences.disabledRepos().asState(scope) }

        LaunchedEffect(url) {
            url?.let { screenModel.createRepo(it) }
        }

        if (state is RepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as RepoScreenState.Success

        ExtensionReposScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(RepoDialog.Create) },
            onOpenWebsite = { context.openInBrowser(it.website) },
            onClickDelete = { screenModel.showDialog(RepoDialog.Delete(it)) },
            onClickEnable = {
                screenModel.enableRepo(it)
                context.toast(MR.strings.extensions_page_need_refresh)
            },
            onClickDisable = {
                screenModel.disableRepo(it)
                context.toast(MR.strings.extensions_page_need_refresh)
            },
            disabledRepos = disabledRepos,
            onClickRefresh = { screenModel.refreshRepos() },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is RepoDialog.Create -> {
                ExtensionRepoCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(it) },
                    repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                )
            }
            is RepoDialog.Delete -> {
                ExtensionRepoDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteRepo(dialog.repo) },
                    repo = dialog.repo,
                )
            }

            is RepoDialog.Conflict -> {
                ExtensionRepoConflictDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onMigrate = { screenModel.replaceRepo(dialog.newRepo) },
                    oldRepo = dialog.oldRepo,
                    newRepo = dialog.newRepo,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
