package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

/**
 * An alternative ExtensionsScreen used when want to open [extensionsTab] directly
 */
class ExtensionsScreen(private val searchSource: String? = null) : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current

        val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
        val extensionsState by extensionsScreenModel.state.collectAsState()
        val extensionsTab = extensionsTab(extensionsScreenModel)

        val searchQuery = extensionsState.searchQuery
        val onChangeSearchQuery = extensionsScreenModel::search

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = {
                val searchEnabled = extensionsTab.searchEnabled
                SearchToolbar(
                    navigateUp = { navigator?.pop() },
                    titleContent = { AppBarTitle(stringResource(MR.strings.label_extensions)) },
                    searchEnabled = searchEnabled,
                    searchQuery = if (searchEnabled) searchQuery else null,
                    onChangeSearchQuery = onChangeSearchQuery,
                    actions = { AppBarActions(extensionsTab.actions) },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            extensionsTab.content(paddingValues, snackbarHostState)
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true

            /*
             * This will redo the searching for [searchSource] every times the screen is launched, for example when
             * back from the [ExtensionFilterScreen] or from the [ExtensionReposScreen].
             * Not really desired but let's accept it.
             */
            if (!searchSource.isNullOrBlank()) onChangeSearchQuery(searchSource)
        }
    }
}
