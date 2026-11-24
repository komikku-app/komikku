@file:OptIn(InternalComposeUiApi::class)

package app.komikku.desktop.ui.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeSceneLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import exh.log.xLogE

/*
 * P16 review fix: Compose Multiplatform 1.12 draws dialogs and popups (`Dialog`, `AlertDialog`, `Popup`,
 * `DropdownMenu`, tooltips, context menus) in layers of their own above the window's content (`compose.layers.type`,
 * default `OnSameCanvas`; `WINDOW` / `COMPONENT` make them separate windows or components). A lock screen inside the
 * window content therefore stays *under* every dialog or menu the app has open. The gate gives the app's content this
 * scene context instead of the window's: every layer the app creates is wrapped, and while the app is locked a wrapped
 * layer composes nothing, takes no focus, no keys, no clicks (inside or outside it: a click on the lock screen does not
 * dismiss a dialog), has no size and no scrim. The callers' state stays (the dialog or menu is still "open" in the
 * screen that shows it) and the layer comes back as it was on unlock; only state remembered *inside* a dialog's or
 * menu's content is reset.
 *
 * It uses Compose's internal scene API (`ComposeSceneContext`, `ComposeSceneLayer`, opt-in like `DesktopTheme`) and
 * `LocalComposeSceneContext`, which is Kotlin-internal and read through its JVM accessor ([localComposeSceneContext]).
 * Without it (a Compose update renamed it) the gate does not compose the app's content while locked (state is lost then,
 * nothing is shown): never a lock screen under a dialog. `AppLockUiTest` covers both the layers and the toasts.
 */

/** Compose's `LocalComposeSceneContext` (internal to Compose UI; read once through its JVM accessor), or null. */
internal val localComposeSceneContext: ProvidableCompositionLocal<ComposeSceneContext?>?
    get() = ComposeSceneContextAccess.local

private object ComposeSceneContextAccess {
    val local: ProvidableCompositionLocal<ComposeSceneContext?>? by lazy {
        try {
            @Suppress("UNCHECKED_CAST")
            Class.forName("androidx.compose.ui.scene.ComposeSceneContext_skikoKt")
                .getMethod("getLocalComposeSceneContext")
                .invoke(null) as ProvidableCompositionLocal<ComposeSceneContext?>
        } catch (e: Exception) {
            xLogE("App lock: Compose's scene context is not reachable, the app's content is not composed while locked", e)
            null
        }
    }
}

/** The scene context of the app's content under [AppLockGate]: layers created through it follow [locked]. */
internal class LockAwareSceneContext(
    private val delegate: ComposeSceneContext,
    private val locked: State<Boolean>,
) : ComposeSceneContext {

    override val platformContext: PlatformContext get() = delegate.platformContext

    override fun createLayer(
        density: Density,
        layoutDirection: LayoutDirection,
        focusable: Boolean,
        consumePointerInputOutside: Boolean,
    ): ComposeSceneLayer {
        val isLocked = locked.value
        return LockAwareLayer(
            delegate = delegate.createLayer(
                density,
                layoutDirection,
                focusable && !isLocked,
                consumePointerInputOutside && !isLocked,
            ),
            locked = locked,
            requestedFocusable = focusable,
            requestedConsumeOutside = consumePointerInputOutside,
        )
    }
}

/** A layer of the app's content: as requested while unlocked, empty and inert while locked. */
private class LockAwareLayer(
    private val delegate: ComposeSceneLayer,
    private val locked: State<Boolean>,
    private var requestedFocusable: Boolean,
    private var requestedConsumeOutside: Boolean,
) : ComposeSceneLayer {

    private var requestedBounds: IntRect = delegate.boundsInWindow
    private var requestedScrim: Color? = delegate.scrimColor

    /** The lock state the delegate was last set up for. */
    private var appliedLocked: Boolean = locked.value

    private fun apply(isLocked: Boolean) {
        appliedLocked = isLocked
        delegate.focusable = requestedFocusable && !isLocked
        delegate.consumePointerInputOutside = requestedConsumeOutside && !isLocked
        delegate.boundsInWindow = if (isLocked) IntRect.Zero else requestedBounds
        delegate.scrimColor = if (isLocked) null else requestedScrim
    }

    init {
        if (appliedLocked) apply(true)
    }

    override var density: Density
        get() = delegate.density
        set(value) {
            delegate.density = value
        }

    override var layoutDirection: LayoutDirection
        get() = delegate.layoutDirection
        set(value) {
            delegate.layoutDirection = value
        }

    override var boundsInWindow: IntRect
        get() = requestedBounds
        set(value) {
            requestedBounds = value
            if (!appliedLocked) delegate.boundsInWindow = value
        }

    override var compositionLocalContext: CompositionLocalContext?
        get() = delegate.compositionLocalContext
        set(value) {
            delegate.compositionLocalContext = value
        }

    override var scrimColor: Color?
        get() = requestedScrim
        set(value) {
            requestedScrim = value
            if (!appliedLocked) delegate.scrimColor = value
        }

    override var focusable: Boolean
        get() = requestedFocusable
        set(value) {
            requestedFocusable = value
            delegate.focusable = value && !appliedLocked
        }

    override var consumePointerInputOutside: Boolean
        get() = requestedConsumeOutside
        set(value) {
            requestedConsumeOutside = value
            delegate.consumePointerInputOutside = value && !appliedLocked
        }

    override fun close() = delegate.close()

    override fun setContent(parentCompositionContext: CompositionContext, content: @Composable () -> Unit) {
        delegate.setContent(parentCompositionContext) { LockAwareContent(content) }
    }

    /** Composed in the layer: read in the same recomposition as the lock screen, applied before the frame is drawn. */
    @Composable
    private fun LockAwareContent(content: @Composable () -> Unit) {
        val isLocked = locked.value
        SideEffect { if (isLocked != appliedLocked) apply(isLocked) }
        if (!isLocked) content()
    }

    override fun setKeyEventListener(
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
    ) {
        delegate.setKeyEventListener(
            onPreviewKeyEvent = onPreviewKeyEvent?.let { listener -> { event -> !locked.value && listener(event) } },
            onKeyEvent = onKeyEvent?.let { listener -> { event -> !locked.value && listener(event) } },
        )
    }

    override fun setOutsidePointerEventListener(onOutsidePointerEvent: ((PointerEventType, PointerButton?) -> Unit)?) {
        delegate.setOutsidePointerEventListener(
            onOutsidePointerEvent?.let { listener -> { type, button -> if (!locked.value) listener(type, button) } },
        )
    }

    override fun calculateLocalPosition(positionInWindow: IntOffset): IntOffset = delegate.calculateLocalPosition(positionInWindow)
}
