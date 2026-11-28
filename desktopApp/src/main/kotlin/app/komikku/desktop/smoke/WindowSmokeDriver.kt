package app.komikku.desktop.smoke

import androidx.compose.runtime.Recomposer
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.io.ByteArrayInputStream
import java.io.File
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Drives the app's real window (UI_SMOKE_DESIGN.md sections 2 and 3). Keyboard input goes as AWT key events straight to
 * the listener Compose installs on the window's content, so it needs neither the system's keyboard focus nor `Robot`;
 * screenshots come from the window's Skia layer ([SkiaLayer.screenshot]: the last drawn frame, independent of the
 * render API, no screen recording permission); the text on screen is read from the window's accessibility tree.
 */
internal class WindowSmokeDriver(
    private val window: ComposeWindow,
    override val navigators: SmokeNavigators,
) : SmokeDriver {

    override suspend fun <T> onUi(block: () -> T): T = withContext(Dispatchers.Swing) { block() }

    /** The Skia layer the window's content is drawn on. */
    fun layer(): SkiaLayer = checkNotNull(window.descendants().filterIsInstance<SkiaLayer>().firstOrNull()) {
        "The window has no Skia layer"
    }

    /** The component Compose listens on for keys (the deepest one with a key listener). */
    fun keyTarget(): Component = checkNotNull(window.descendants().lastOrNull { it.keyListeners.isNotEmpty() }) {
        "No component of the window listens for keys"
    }

    override suspend fun press(key: SmokeKey) = onUi {
        val (code, char) = when (key) {
            SmokeKey.PageDown -> KeyEvent.VK_PAGE_DOWN to KeyEvent.CHAR_UNDEFINED
            SmokeKey.Escape -> KeyEvent.VK_ESCAPE to 0x1B.toChar()
            SmokeKey.Enter -> KeyEvent.VK_ENTER to '\n'
        }
        send(keyTarget(), code, char)
    }

    override suspend fun type(text: String) = onUi {
        val target = keyTarget()
        text.forEach { char -> send(target, KeyEvent.getExtendedKeyCodeForChar(char.code), char) }
    }

    private fun send(target: Component, code: Int, char: Char) {
        val now = System.currentTimeMillis()
        dispatch(target, KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, code, char, KeyEvent.KEY_LOCATION_STANDARD))
        if (char != KeyEvent.CHAR_UNDEFINED) {
            dispatch(target, KeyEvent(target, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, char, KeyEvent.KEY_LOCATION_UNKNOWN))
        }
        dispatch(target, KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, code, char, KeyEvent.KEY_LOCATION_STANDARD))
    }

    private fun dispatch(target: Component, event: KeyEvent) {
        target.keyListeners.forEach { listener ->
            when (event.id) {
                KeyEvent.KEY_PRESSED -> listener.keyPressed(event)
                KeyEvent.KEY_TYPED -> listener.keyTyped(event)
                KeyEvent.KEY_RELEASED -> listener.keyReleased(event)
            }
        }
    }

    override suspend fun texts(): List<String> = onUi {
        buildList {
            window.accessibleContext.walk { context, _ ->
                context.accessibleName?.takeIf { it.isNotBlank() }?.let(::add)
                context.accessibleDescription?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    override suspend fun awaitIdle(timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var quietPolls = 0
        while (System.nanoTime() < deadline) {
            val busy = onUi { Recomposer.runningRecomposers.value.any { it.hasPendingWork } }
            quietPolls = if (busy) 0 else quietPolls + 1
            if (quietPolls >= QUIET_POLLS) return true
            delay(IDLE_POLL_MILLIS)
        }
        return false
    }

    override suspend fun screenshot(file: File): Screenshot {
        val (bitmap, expected) = onUi {
            val layer = layer()
            runCatching { layer.renderImmediately() }
            val scale = layer.contentScale
            val bitmap = checkNotNull(layer.screenshot()) { "The window has not drawn a frame yet" }
            bitmap to ((layer.width * scale).roundToInt() to (layer.height * scale).roundToInt())
        }
        val png = checkNotNull(Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes
        bitmap.close()
        file.parentFile?.mkdirs()
        file.writeBytes(png)
        val image = ImageIO.read(ByteArrayInputStream(png))
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return Screenshot(image.width, image.height, expected.first, expected.second, Screenshot.countColours(pixels))
    }

    override suspend fun accessibilityTree(): String = onUi {
        buildString {
            window.accessibleContext.walk { context, depth ->
                append("  ".repeat(depth))
                append(context.accessibleRole?.toDisplayString(java.util.Locale.ENGLISH) ?: "?")
                context.accessibleName?.let { append(" \"").append(it).append('"') }
                context.accessibleDescription?.let { append(" (").append(it).append(')') }
                context.accessibleStateSet?.toArray()?.takeIf { it.isNotEmpty() }?.let { states ->
                    append(" [").append(states.joinToString(",") { it.toDisplayString(java.util.Locale.ENGLISH) }).append(']')
                }
                runCatching { context.accessibleComponent?.bounds }.getOrNull()?.let {
                    append(" @").append(it.x).append(',').append(it.y).append(' ').append(it.width).append('x').append(it.height)
                }
                append('\n')
            }
        }
    }

    /** Asks the window to close, like its close button: the app's normal shutdown follows. */
    suspend fun close() = onUi { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) }

    companion object {
        private const val QUIET_POLLS = 3
        private const val IDLE_POLL_MILLIS = 50L
        private const val MAX_NODES = 5_000
        private const val MAX_DEPTH = 64

        /** The app's main window once it shows, or `null` after [timeoutMillis]. */
        suspend fun awaitWindow(timeoutMillis: Long): ComposeWindow? {
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            while (System.nanoTime() < deadline) {
                val window = withContext(Dispatchers.Swing) {
                    Window.getWindows().filterIsInstance<ComposeWindow>().firstOrNull { it.isShowing }
                }
                if (window != null) return window
                delay(100)
            }
            return null
        }

        private fun Container.descendants(): Sequence<Component> = sequence {
            for (child in components) {
                yield(child)
                if (child is Container) yieldAll(child.descendants())
            }
        }

        private fun AccessibleContext.walk(visit: (AccessibleContext, Int) -> Unit) {
            var nodes = 0
            fun visitNode(context: AccessibleContext, depth: Int) {
                if (nodes++ >= MAX_NODES || depth > MAX_DEPTH) return
                visit(context, depth)
                val count = runCatching { context.accessibleChildrenCount }.getOrDefault(0)
                for (index in 0 until count) {
                    val child = runCatching { context.getAccessibleChild(index) }.getOrNull() as? Accessible ?: continue
                    child.accessibleContext?.let { visitNode(it, depth + 1) }
                }
            }
            visitNode(this, 0)
        }
    }
}
