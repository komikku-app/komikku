package eu.kanade.translation

import android.content.Context
import android.util.AttributeSet
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R


class TextTranslationsComposeView :
    AbstractComposeView {

    private val translations: List<TextTranslation>
    val font = Font(
        resId = R.font.animeace, // Resource ID of the font file
        weight = FontWeight.Bold, // Weight of the font
    ).toFontFamily()

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr) {
        this.translations = emptyList()
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        translations: List<TextTranslation> = emptyList(),
    ) : super(context, attrs, defStyleAttr) {
        this.translations = translations
    }

    @Composable
    override fun Content() {
        TranslationsContent(translations)
    }

    @Composable
    fun TranslationsContent(translations: List<TextTranslation>) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (size == IntSize.Zero) hide()
                    if (size == IntSize.Zero) show()
                },
        ) {
            if (size == IntSize.Zero) return
            val imgWidth = size.width
            val imgHeight = size.height
            translations.forEach { translation ->
                val xPx = ((translation.x -translation.symWidth/2) * imgWidth)
                val yPx = ((translation.y - translation.symHeight/2) * imgHeight)
                val width = ((translation.width +translation.symWidth) * imgWidth)
                val height = ((translation.height+  translation.symHeight) * imgHeight)
                TextBlock(
                    translation = translation,
                    modifier = Modifier
                        .absoluteOffset(pxToDp(xPx), pxToDp(yPx))
                        .rotate(if (translation.angle < 88) translation.angle else 0f)
                        .size(pxToDp(width), pxToDp(height)),
                )
            }
        }
    }

    @Composable
    fun TextBlock(translation: TextTranslation, modifier: Modifier) {
        Box(modifier = modifier) {
            AutoSizeText(
                text = translation.translated,
                color = Color.Black,
                softWrap = true, fontFamily = font,
                lineSpacingRatio = 1.2f,
                overflow = TextOverflow.Clip,
                alignment = Alignment.Center,
                modifier = Modifier
                    .background(Color.Red.copy(alpha = 0.4f))
//                    .background(Color.White)
                    .padding(1.dp),

                )
        }
    }

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }

    private fun pxToDp(px: Float): Dp {
        return Dp(px / (context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT))
    }
}
