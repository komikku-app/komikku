package exh.log

import android.content.Context
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.time.Duration.Companion.nanoseconds

@Composable
fun DebugModeOverlay() {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .only(WindowInsetsSides.Bottom.plus(WindowInsetsSides.Start)),
                )
                .align(Alignment.BottomStart)
                .background(Color(0x7F000000))
                .padding(4.dp),
        ) {
            FpsDebugModeOverlay()
            EHDebugModeOverlay()
        }
    }
}

@Composable
private fun FpsDebugModeOverlay() {
    val fps by remember { FpsState(FpsState.DEFAULT_INTERVAL) }
    val format = remember {
        DecimalFormat(
            "'fps:' 0.0",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH),
        )
    }

    Text(
        text = remember(fps) {
            format.format(fps)
        },
        color = Color.White,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun EHDebugModeOverlay() {
    val scope = rememberCoroutineScope()
    val enableSourceBlacklist by remember {
        Injekt.get<SourcePreferences>().enableSourceBlacklist().asState(scope)
    }
    val context = LocalContext.current
    Text(
        text = remember(enableSourceBlacklist) {
            buildInfo(context, enableSourceBlacklist)
        },
        color = Color.White,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.1f.sp,
    )
}

private fun buildInfo(context: Context, sourceBlacklist: Boolean) = buildAnnotatedString {
    withStyle(SpanStyle(color = Color.Green)) {
        append("===[ ")
        append(context.stringResource(MR.strings.app_name))
        append(" ]===")
    }
    append('\n')
    appendItem("Build type:", BuildConfig.BUILD_TYPE)
    appendItem("Debug mode:", isDebugBuildType.asEnabledString())
    appendItem("Version code:", BuildConfig.VERSION_CODE.toString())
    appendItem("Commit SHA:", BuildConfig.COMMIT_SHA)
    appendItem("Log level:", EHLogLevel.currentLogLevel.name.lowercase(Locale.getDefault()))
    appendItem("Source blacklist:", sourceBlacklist.asEnabledString(), newLine = false)
}

fun AnnotatedString.Builder.appendItem(title: String, item: String, newLine: Boolean = true) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(title)
    }
    append(' ')
    append(item)
    if (newLine) {
        append('\n')
    }
}

private fun Boolean.asEnabledString() = if (this) "enabled" else "disabled"

private class FpsState(private val interval: Int) :
    Choreographer.FrameCallback,
    RememberObserver,
    MutableState<Double> by mutableDoubleStateOf(0.0) {
    private val choreographer = Choreographer.getInstance()
    private var startFrameTimeMillis: Long = 0
    private var numFramesRendered = 0

    override fun onRemembered() {
        choreographer.postFrameCallback(this)
    }

    override fun onAbandoned() {
        choreographer.removeFrameCallback(this)
    }

    override fun onForgotten() {
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        val currentFrameTimeMillis = frameTimeNanos.nanoseconds.inWholeMilliseconds
        if (startFrameTimeMillis > 0) {
            val duration = currentFrameTimeMillis - startFrameTimeMillis
            numFramesRendered++
            if (duration > interval) {
                value = (numFramesRendered * 1000f / duration).toDouble()
                startFrameTimeMillis = currentFrameTimeMillis
                numFramesRendered = 0
            }
        } else {
            startFrameTimeMillis = currentFrameTimeMillis
        }
        choreographer.postFrameCallback(this)
    }

    companion object {
        const val DEFAULT_INTERVAL = 1000
    }
}
