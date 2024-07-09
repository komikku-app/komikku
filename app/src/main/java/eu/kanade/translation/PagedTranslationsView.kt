package eu.kanade.translation

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.core.common.util.system.logcat


class PagedTranslationsView :
    AbstractComposeView {

    private val translations: TextTranslations
    private val font: FontFamily
    private val translationOffset: TranslationOffset

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr) {
        this.translations = TextTranslations(imgWidth = 0f, imgHeight = 0f)
        this.translationOffset = TranslationOffset()
        this.font = Font(
            resId = R.font.animeace, // Resource ID of the font file
            weight = FontWeight.Bold, // Weight of the font
        ).toFontFamily()
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        translations: TextTranslations,
        font: FontFamily? = null,
        translationOffset: TranslationOffset? = null,
    ) : super(context, attrs, defStyleAttr) {
        this.translations = translations
        this.translationOffset = translationOffset ?: TranslationOffset()
        this.font = font ?: Font(
            resId = R.font.animeace, // Resource ID of the font file
            weight = FontWeight.Bold, // Weight of the font
        ).toFontFamily()
    }

    @Composable
    override fun Content() {
        TranslationsContent(translations)
    }

    val scaleState = MutableStateFlow(1f)
    val viewTLState = MutableStateFlow(PointF())

    @Composable
    fun TranslationsContent(translations: TextTranslations) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (size == IntSize.Zero) hide()
                    else show()
                },
        ) {
            if (size == IntSize.Zero) return
            val scale = scaleState.collectAsState().value
            val viewTL = viewTLState.collectAsState().value
            translations.translations.forEach { translation ->

                var width = translation.width + translation.symWidth
                var height = translation.height + translation.symHeight

                var xPx = translation.x - translation.symWidth / 2
                var yPx = translation.y - translation.symHeight / 2


                xPx += if(translationOffset.asPercentage) width*((translationOffset.x.toFloat()-(translationOffset.width.toFloat()/2))/100)else (translationOffset.x - (translationOffset.width .toFloat()/ 2))
                yPx += if(translationOffset.asPercentage) height*((translationOffset.y.toFloat()-(translationOffset.height.toFloat()/2))/100) else (translationOffset.y - (translationOffset.height .toFloat()/ 2))

                height += if(translationOffset.asPercentage)( height*(translationOffset.height.toFloat()/100)).toInt() else  translationOffset.height
                width +=  if(translationOffset.asPercentage)( width*(translationOffset.width.toFloat()/100)).toInt() else  translationOffset.width
                xPx *= scale
                yPx *= scale
                xPx += viewTL.x
                yPx += viewTL.y
                height *= scale
                width *= scale


                TextBlock(
                    translation = translation,
                    modifier = Modifier
                        .offset(pxToDp(xPx), pxToDp(yPx))
                        .requiredSize(pxToDp(width), pxToDp(height))
                        .background(Color.White, shape = RoundedCornerShape(8.dp)),
                )
            }
        }
    }

    @Composable
    fun TextBlock(translation: BlockTranslation, modifier: Modifier) {
        Box(modifier = modifier) {
            AutoSizeText(
                text = translation.translated,
                color = Color.Black,
                softWrap = true, fontFamily = font,
                lineSpacingRatio = 1.2f,
                overflow = TextOverflow.Clip,
                alignment = Alignment.Center,

                modifier = Modifier
                    .background(Color.White, shape = RoundedCornerShape(8.dp))
                    .rotate(if (translation.angle < 88) translation.angle else 0f)
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

