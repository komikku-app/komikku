package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.util.system.dpToPx
import exh.log.xLogD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.milliseconds

private sealed interface UpscaleBadgeState {
    data object Hidden : UpscaleBadgeState
    data object InProgress : UpscaleBadgeState
    data object Success : UpscaleBadgeState
    data object Failed : UpscaleBadgeState
    data object Active : UpscaleBadgeState
}

/**
 * Compact badge at bottom right to report AI upscaling in progress or just
 * completed.
 */
class UpscaleStatusIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    @ColorInt private val seedColor: Int? = null,
    private val debugTag: String = "?",
    private val alpha: Float = 0.85f,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    init {
        layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = 12.dpToPx
            bottomMargin = 12.dpToPx
        }
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    private var state by mutableStateOf<UpscaleBadgeState>(UpscaleBadgeState.Hidden)
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var autoDismissJob: Job? = null

    private fun setState(newState: UpscaleBadgeState, caller: String) {
        xLogD("[$debugTag] state: $state -> $newState (called by $caller)")
        state = newState
    }

    @Composable
    override fun Content() {
        val uiPreferences = Injekt.get<UiPreferences>()
        val themeCoverBased = uiPreferences.themeCoverBased().get()

        TachiyomiTheme(
            seedColor = seedColor?.let { Color(it) }.takeIf { themeCoverBased },
        ) {
            AnimatedVisibility(
                visible = state != UpscaleBadgeState.Hidden,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    when (state) {
                        UpscaleBadgeState.InProgress -> {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                        UpscaleBadgeState.Success -> {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        UpscaleBadgeState.Failed -> {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        }
                        UpscaleBadgeState.Active -> {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        UpscaleBadgeState.Hidden -> Unit
                    }
                    if (state != UpscaleBadgeState.Hidden && state != UpscaleBadgeState.Active) {
                        Text(
                            text = stringResource(
                                when (state) {
                                    UpscaleBadgeState.InProgress -> KMR.strings.upscale_badge_in_progress
                                    UpscaleBadgeState.Success -> KMR.strings.upscale_badge_done
                                    UpscaleBadgeState.Failed -> KMR.strings.upscale_badge_failed
                                    else -> KMR.strings.upscale_badge_in_progress
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }

    fun showInProgress() {
        autoDismissJob?.cancel()
        setState(UpscaleBadgeState.InProgress, "showInProgress")
    }

    fun showSuccess(autoDismissMillis: Long = 1500) {
        autoDismissJob?.cancel()
        setState(UpscaleBadgeState.Success, "showSuccess")
        autoDismissJob = scope.launch {
            delay(autoDismissMillis.milliseconds)
            setState(UpscaleBadgeState.Active, "showSuccess/autoDismiss")
        }
    }

    fun showFailed(autoDismissMillis: Long = 2000) {
        autoDismissJob?.cancel()
        setState(UpscaleBadgeState.Failed, "showFailed")
        autoDismissJob = scope.launch {
            delay(autoDismissMillis.milliseconds)
            setState(UpscaleBadgeState.Hidden, "showFailed/autoDismiss")
        }
    }

    fun showActive() {
        autoDismissJob?.cancel()
        setState(UpscaleBadgeState.Active, "showActive")
    }

    fun hide() {
        autoDismissJob?.cancel()
        setState(UpscaleBadgeState.Hidden, "hide")
    }

    fun destroy() {
        autoDismissJob?.cancel()
        scope.cancel()
    }
}
