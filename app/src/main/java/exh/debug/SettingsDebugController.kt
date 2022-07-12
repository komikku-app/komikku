package exh.debug

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.presentation.components.PreferenceRow
import eu.kanade.presentation.components.SwitchPreference
import eu.kanade.tachiyomi.ui.base.controller.BasicComposeController
import exh.util.capitalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions

class SettingsDebugController : BasicComposeController() {

    override fun getTitle(): String {
        return "DEBUG MENU"
    }

    data class DebugToggle(val name: String, val pref: PreferenceMutableState<Boolean>, val default: Boolean)

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        val functions by produceState<List<Pair<KFunction<*>, String>>?>(initialValue = null) {
            value = withContext(Dispatchers.Default) {
                DebugFunctions::class.declaredFunctions.filter {
                    it.visibility == KVisibility.PUBLIC
                }.map {
                    it to it.name.replace("(.)(\\p{Upper})".toRegex(), "$1 $2")
                        .lowercase(Locale.getDefault()).capitalize(Locale.getDefault())
                }
            }
        }
        val toggles by produceState(initialValue = emptyList()) {
            value = withContext(Dispatchers.Default) {
                DebugToggles.values().map { DebugToggle(it.name, it.asPref(viewScope), it.default) }
            }
        }
        if (functions != null) {
            val scope = rememberCoroutineScope()
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollInterop),
            ) {
                var running by remember { mutableStateOf(false) }
                var result by remember { mutableStateOf<Pair<String, String>?>(null) }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "Functions",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(functions.orEmpty()) { (func, name) ->
                        PreferenceRow(
                            title = name,
                            onClick = {
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
                        SwitchPreference(
                            preference = pref,
                            title = name.replace('_', ' ')
                                .lowercase(Locale.getDefault())
                                .capitalize(Locale.getDefault()),
                            subtitleAnnotated = if (pref.value != default) {
                                AnnotatedString("MODIFIED", SpanStyle(color = Color.Red))
                            } else null,
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
                if (result != null) {
                    AlertDialog(
                        onDismissRequest = { result = null },
                        title = {
                            Text(text = result?.first.orEmpty())
                        },
                        confirmButton = {},
                        text = {
                            SelectionContainer {
                                Text(text = result?.second.orEmpty())
                            }
                        },
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        router.popCurrentController()
    }
}
