package androidx.compose.ui.platform

import android.app.Application
import android.content.Context
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * compose-compat: `androidx.compose.ui.platform.LocalContext` for desktop.
 *
 * The desktop app provides its compat [Application] (the one registered in DI) at the root of every
 * window. Without a provider (previews, UI tests) the value is a lazily created [Application] over
 * the default desktop data dir, so `stringResource(...)` & co. keep working.
 */
val LocalContext: ProvidableCompositionLocal<Context> = staticCompositionLocalOf { defaultContext }

private val defaultContext: Context by lazy { Application() }
