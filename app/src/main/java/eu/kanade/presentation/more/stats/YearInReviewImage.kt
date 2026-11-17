package eu.kanade.presentation.more.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import eu.kanade.presentation.util.toDurationString
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.readinglog.model.YearInReview
import tachiyomi.i18n.kmk.KMR
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// KMK -->
/**
 * Draws the year in review as a picture that can be shared.
 * It is drawn by hand so that it looks the same everywhere and needs nothing from the screen.
 */
object YearInReviewImage {

    private const val WIDTH = 1080
    private const val HEIGHT = 1500
    private const val MARGIN = 80f

    private const val BACKGROUND_TOP = 0xFF1B1B26.toInt()
    private const val BACKGROUND_BOTTOM = 0xFF0D0D14.toInt()
    private const val TEXT = 0xFFF2F2F7.toInt()
    private const val TEXT_SECONDARY = 0xFFA6A6B8.toInt()

    fun render(context: Context, review: YearInReview, accent: Int, hideTitles: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val background = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), BACKGROUND_TOP, BACKGROUND_BOTTOM, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        var y = 130f

        // Header
        y = drawText(canvas, text, "Komikku", MARGIN, y, 44f, accent, bold = true)
        y = drawText(
            canvas,
            text,
            context.stringResource(KMR.strings.year_in_review_title),
            MARGIN,
            y + 20f,
            64f,
            TEXT,
            bold = true,
        )
        y = drawText(canvas, text, review.year.toString(), MARGIN, y + 10f, 150f, accent, bold = true)

        // Time read
        val time = review.totalReadTimeMs.toDuration(DurationUnit.MILLISECONDS)
            .toDurationString(context, fallback = "-")
        y = drawText(canvas, text, time, MARGIN, y + 40f, 84f, TEXT, bold = true)
        y = drawText(
            canvas,
            text,
            context.stringResource(KMR.strings.year_in_review_time_read),
            MARGIN,
            y + 4f,
            38f,
            TEXT_SECONDARY,
        )

        // Numbers
        y += 60f
        val column = (WIDTH - 2 * MARGIN) / 3
        listOf(
            review.chaptersRead.toString() to context.stringResource(KMR.strings.year_in_review_chapters),
            review.daysRead.toString() to context.stringResource(KMR.strings.year_in_review_days),
            review.longestStreak.toString() to context.stringResource(KMR.strings.year_in_review_streak),
        ).forEachIndexed { index, (value, label) ->
            val x = MARGIN + column * index
            drawText(canvas, text, value, x, y, 72f, TEXT, bold = true)
            drawEllipsized(canvas, text, label, x, y + 44f, 32f, TEXT_SECONDARY, column - 20f)
        }
        y += 110f

        // Months
        y = drawMonths(canvas, text, review, accent, y)

        // Titles and genres
        if (!hideTitles && review.topTitles.isNotEmpty()) {
            y = drawText(
                canvas,
                text,
                context.stringResource(KMR.strings.year_in_review_top_titles),
                MARGIN,
                y + 30f,
                40f,
                accent,
                bold = true,
            )
            review.topTitles.forEachIndexed { index, title ->
                y = drawEllipsized(
                    canvas,
                    text,
                    "${index + 1}. ${title.title}",
                    MARGIN,
                    y + 12f,
                    42f,
                    TEXT,
                    WIDTH - 2 * MARGIN,
                )
            }
        }
        if (!hideTitles && review.topGenres.isNotEmpty()) {
            drawEllipsized(
                canvas,
                text,
                review.topGenres.joinToString("  ·  "),
                MARGIN,
                y + 36f,
                36f,
                TEXT_SECONDARY,
                WIDTH - 2 * MARGIN,
            )
        }

        return bitmap
    }

    /** One bar for each month, the height is the number of days read. Returns the new y. */
    private fun drawMonths(canvas: Canvas, text: TextPaint, review: YearInReview, accent: Int, top: Float): Float {
        val maxBarHeight = 150f
        val slot = (WIDTH - 2 * MARGIN) / 12
        val max = review.daysReadPerMonth.maxOrNull()?.coerceAtLeast(1) ?: 1
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val baseline = top + maxBarHeight
        review.daysReadPerMonth.forEachIndexed { month, days ->
            val height = (maxBarHeight * days / max).coerceAtLeast(6f)
            paint.color = if (review.busiestMonth == month + 1) accent else (accent and 0x00FFFFFF) or 0x66000000
            val left = MARGIN + slot * month + slot * 0.15f
            canvas.drawRoundRect(RectF(left, baseline - height, left + slot * 0.7f, baseline), 8f, 8f, paint)
        }
        val label = review.busiestMonth?.let { Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) }
        return if (label != null) {
            drawText(canvas, text, label, MARGIN, baseline + 52f, 36f, TEXT_SECONDARY)
        } else {
            baseline
        }
    }

    /** Draws [value] with its baseline at [y] and returns the baseline of the next line. */
    private fun drawText(
        canvas: Canvas,
        paint: TextPaint,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ): Float {
        paint.textSize = size
        paint.color = color
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        canvas.drawText(value, x, y + size, paint)
        return y + size
    }

    private fun drawEllipsized(
        canvas: Canvas,
        paint: TextPaint,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        maxWidth: Float,
    ): Float {
        paint.textSize = size
        paint.typeface = Typeface.DEFAULT
        val fitted = TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        return drawText(canvas, paint, fitted, x, y, size, color)
    }
}
// KMK <--
