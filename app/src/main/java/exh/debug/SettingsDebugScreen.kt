package exh.debug

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.util.Screen
import exh.util.capitalize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Divider
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import java.util.Locale
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions

class SettingsDebugScreen : Screen() {

    data class DebugToggle(val name: String, val pref: PreferenceMutableState<Boolean>, val default: Boolean)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            onDispose { navigator.pop() }
        }
        val functions by produceState<List<Pair<KFunction<*>, String>>?>(initialValue = null) {
            value = withContext(Dispatchers.Default) {
                DebugFunctions::class.declaredFunctions.filter {
                    it.visibility == KVisibility.PUBLIC
                }.map {
                    it to it.name.replace("(.)(\\p{Upper})".toRegex(), "$1 $2")
                        .lowercase(Locale.getDefault())
                        .capitalize(Locale.getDefault())
                }
            }
        }
        val toggles by produceState(initialValue = emptyList()) {
            value = withContext(Dispatchers.Default) {
                DebugToggles.values().map { DebugToggle(it.name, it.asPref(scope), it.default) }
            }
        }
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = "DEBUG MENU",
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            Crossfade(functions == null) {
                when (it) {
                    true -> LoadingScreen()
                    false -> FunctionList(paddingValues, functions.orEmpty(), toggles, scope)
                }
            }
        }
    }

    @Composable
    fun FunctionList(
        paddingValues: PaddingValues,
        functions: List<Pair<KFunction<*>, String>>,
        toggles: List<DebugToggle>,
        scope: CoroutineScope,
    ) {
        Box(Modifier.fillMaxSize()) {
            var running by remember { mutableStateOf(false) }
            var result by remember { mutableStateOf<Pair<String, String>?>(null) }
            ScrollbarLazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = paddingValues +
                    WindowInsets.navigationBars.only(WindowInsetsSides.Vertical).asPaddingValues() +
                    topSmallPaddingValues,
            ) {
                item {
                    Text(
                        text = "Functions",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(functions) { (func, name) ->
                    TextPreferenceWidget(
                        title = name,
                        onPreferenceClick = {
                            scope.launch(Dispatchers.Default) {
                                val text = try {
                                    running = true
                                    "Function returned result:\n\n${func.call(DebugFunctions)}"
                                } catch (e: Exception) {
                                    "Function threw exception:\n\n${Log.getStackTraceString(e)}"
                                } finally {
                                    running = false
                                }
                                result = name to text
                            }
                        },
                    )
                }
                item {
                    Divider()
                }
                item {
                    Text(
                        text = "Toggles",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(toggles) { (name, pref, default) ->
                    var state by pref
                    TextPreferenceWidget(
                        title = name.replace('_', ' ')
                            .lowercase(Locale.getDefault())
                            .capitalize(Locale.getDefault()),
                        subtitle = if (pref.value != default) {
                            AnnotatedString("MODIFIED", SpanStyle(color = Color.Red))
                        } else {
                            null
                        },
                        widget = {
                            Switch(
                                checked = state,
                                onCheckedChange = null,
                                modifier = Modifier.padding(start = TrailingWidgetBuffer),
                            )
                        },
                        onPreferenceClick = { state = !state },
                    )
                }
                item {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
            AnimatedVisibility(
                running && result == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color = Color.White.copy(alpha = 0.3F))
                        .pointerInput(running && result == null) {
                            forEachGesture {
                                awaitPointerEventScope {
                                    waitForUpOrCancellation()?.consume()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            ResultTextDialog(
                result = result,
                onDismissRequest = { result = null },
            )
        }
    }

    @Composable
    private fun ResultTextDialog(result: Pair<String, String>?, onDismissRequest: () -> Unit) {
        if (result != null) {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                title = {
                    Text(text = result.first)
                },
                confirmButton = {},
                text = {
                    SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = result.second)
                    }
                },
            )
        }
    }
}
