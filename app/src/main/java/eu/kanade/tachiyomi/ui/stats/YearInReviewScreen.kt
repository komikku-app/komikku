package eu.kanade.tachiyomi.ui.stats

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.stats.YearInReviewContent
import eu.kanade.presentation.more.stats.YearInReviewImage
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.readinglog.interactor.GetYearInReview
import tachiyomi.domain.readinglog.model.YearInReview
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.LocalDate

// KMK -->
class YearInReviewScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { YearInReviewScreenModel() }
        val state by screenModel.state.collectAsState()
        val accent = MaterialTheme.colorScheme.primary.toArgb()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.year_in_review_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        val review = state.review
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_share),
                                    icon = Icons.Outlined.Share,
                                    enabled = review != null && review.hasData,
                                    onClick = {
                                        review ?: return@Action
                                        scope.launch { share(context, review, accent, state.hideTitles) }
                                    },
                                ),
                            ),
                        )
                    },
                )
            },
        ) { paddingValues ->
            if (state.isLoading) {
                LoadingScreen()
                return@Scaffold
            }
            YearInReviewContent(
                year = state.year,
                review = state.review,
                canGoBack = screenModel.canGoBack(),
                canGoForward = screenModel.canGoForward(),
                onPreviousYear = { screenModel.moveYear(-1) },
                onNextYear = { screenModel.moveYear(1) },
                hideTitles = state.hideTitles,
                onHideTitlesChange = screenModel::setHideTitles,
                paddingValues = paddingValues,
            )
        }
    }

    private suspend fun share(context: Context, review: YearInReview, accent: Int, hideTitles: Boolean) {
        val uri = withContext(Dispatchers.IO) {
            val bitmap = YearInReviewImage.render(context, review, accent, hideTitles)
            val directory = File(context.cacheDir, "year_in_review").apply { mkdirs() }
            val file = File(directory, "year_in_review_${review.year}.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
        context.startActivity(uri.toShareIntent(context, "image/png"))
    }
}

class YearInReviewScreenModel(
    private val getYearInReview: GetYearInReview = Injekt.get(),
) : StateScreenModel<YearInReviewScreenModel.State>(State()) {

    private var years: List<Int> = emptyList()

    init {
        screenModelScope.launch {
            // The latest years first, the current year is shown even when nothing was read yet
            val current = LocalDate.now().year
            years = (getYearInReview.years() + current).distinct().sortedDescending()
            load(current)
        }
    }

    fun canGoBack() = state.value.year > (years.lastOrNull() ?: state.value.year)

    fun canGoForward() = state.value.year < (years.firstOrNull() ?: state.value.year)

    fun moveYear(step: Int) {
        val target = state.value.year + step
        if (target < (years.lastOrNull() ?: target) || target > (years.firstOrNull() ?: target)) return
        screenModelScope.launch { load(target) }
    }

    fun setHideTitles(hide: Boolean) {
        mutableState.update { it.copy(hideTitles = hide) }
    }

    private suspend fun load(year: Int) {
        mutableState.update { it.copy(isLoading = true, year = year) }
        val review = getYearInReview.await(year)
        mutableState.update { it.copy(isLoading = false, review = review) }
    }

    data class State(
        val isLoading: Boolean = true,
        val year: Int = LocalDate.now().year,
        val review: YearInReview? = null,
        val hideTitles: Boolean = false,
    )
}
// KMK <--
