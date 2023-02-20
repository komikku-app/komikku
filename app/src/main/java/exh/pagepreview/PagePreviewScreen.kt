package exh.pagepreview

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import exh.pagepreview.components.PagePreviewScreen

class PagePreviewScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { PagePreviewScreenModel(mangaId) }
        val context = LocalContext.current
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        PagePreviewScreen(
            state = state,
            pageDialogOpen = screenModel.pageDialogOpen,
            onPageSelected = screenModel::moveToPage,
            onOpenPage = { openPage(context, state, it) },
            onOpenPageDialog = { screenModel.pageDialogOpen = true },
            onDismissPageDialog = { screenModel.pageDialogOpen = false },
            navigateUp = navigator::pop,
        )
    }

    fun openPage(context: Context, state: PagePreviewState, page: Int) {
        if (state !is PagePreviewState.Success) return
        context.run {
            startActivity(ReaderActivity.newIntent(this, state.manga.id, state.chapter.id, page))
        }
    }
}
