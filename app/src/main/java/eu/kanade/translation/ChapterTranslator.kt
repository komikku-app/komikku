package eu.kanade.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.Text.TextBlock
import eu.kanade.tachiyomi.R
import eu.kanade.translation.translators.GeminiTranslator
import eu.kanade.translation.translators.GoogleTranslator
import eu.kanade.translation.translators.LanguageTranslator
import eu.kanade.translation.translators.LanguageTranslators
import eu.kanade.translation.translators.MLKitTranslator
import eu.kanade.translation.translators.OpenRouterTranslator
import eu.kanade.translation.translators.ScanLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import tachiyomi.core.common.util.system.logcat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class ChapterTranslator(
    private val context: Context,
    private var scanLanguage: ScanLanguage = ScanLanguage.Chinese,
    private var translateLanguage: Locale = Locale.ENGLISH,
    private var translationEngine: LanguageTranslators = LanguageTranslators.MLKIT,
    private var apiKey: String = "", var font: Int = 0,
) {
    private var debug = true

    private var recognizer = TextRecognizer(scanLanguage)
    private var textTranslator: LanguageTranslator =
        getTranslator(translationEngine, scanLanguage, translateLanguage, apiKey)
    private val textPaint = TextPaint()
    private val fonts = arrayOf(R.font.animeace, R.font.manga_master_bb, R.font.comic_book)
    private val widthMAX = 800

    init {
        textPaint.color = Color.BLACK
        textPaint.typeface = ResourcesCompat.getFont(context, fonts[font])
    }

    suspend fun translateChapter(translation: Translation) {
        val files = translation.dir.listFiles()!!.filter { "image" in it.type.orEmpty() }
        val pages = HashMap<String, TextTranslations>()
        for (file in files) {
            try {
                var result: Text
                var image: InputImage
                val bitmap = BitmapFactory.decodeStream(file.openInputStream())
                var width = bitmap.width
                var height = bitmap.height
                var resized = bitmap
                var scale = 1f
                if (width > widthMAX) {
                    scale = width.toFloat() / widthMAX
                    height = (height * widthMAX / width).toInt()
                    width = widthMAX
                    resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
                }
                image = InputImage.fromBitmap(resized, 0)
                result = recognizer.recognize(image)
                val blocks = result.textBlocks.filter { it.boundingBox != null && it.text.length > 1 }
                val resultant = toTextTranslation(blocks, width.toFloat(), height.toFloat(), scale)
                if (resultant.translations.isNotEmpty()) file.name?.let { pages.put(it, resultant) }
            } catch (e: Exception) {
                logcat { "ERROR : ${e.stackTraceToString()}" }
            }
        }
        //Translate All Pages

        textTranslator.translate(pages)
        Json.encodeToStream(pages, translation.saveFile.openOutputStream())
    }

    private fun toTextTranslation(
        blocks: List<TextBlock>,
        width: Float,
        height: Float,
        scale: Float,
    ): TextTranslations {
        val resultant = TextTranslations(imgHeight = height * scale, imgWidth = width * scale)
        for (block in blocks) {
            try {
                var passed = true
                val bounds = block.boundingBox!!
                val symbolBound = block.lines.first().elements.first().symbols.first().boundingBox!!
                val bWidth = bounds.width().toFloat()
                val bHeight = bounds.height().toFloat()
                val bX = symbolBound.left.toFloat()
                val bY = symbolBound.top.toFloat()
//                val thresholdX=20f/width
//                val thresholdY=20f/height
//                logcat { "BLOCK : ${block.text} | ${bY} | ${bounds.bottom} | ${bX}" }
                val translations = resultant.translations
                for (i in translations.lastIndex downTo (translations.lastIndex - 5).coerceAtLeast(0)) {
                    val b2 = translations[i]
                    val bottom = b2.height + b2.y
//                    logcat { "RES : ${b2.text} | ${bottom} | ${b2.x - bX} | ${(bY - bottom)} | ${thresholdX} | ${thresholdY}" }
                    if (bY - bottom < symbolBound.height() * 2 && abs(b2.x - bX) < 20) {
//                        logcat { "ADDED : ${block.text.replace("\n", " ")}" }
                        passed = false
                        b2.text += " " + block.text.replace("\n", " ")
                        b2.height += bHeight + 16
                        b2.width = max(bWidth, b2.width)
                        b2.x = min(bX, b2.x)
                        break
                    }
                }
                if (passed) {
                    translations.add(
                        BlockTranslation(
                            text = block.text.replace("\n", " "),
                            x = bX,
                            y = bY,
                            width = bounds.width().toFloat(),
                            height = bounds.height().toFloat(),
                            symHeight = symbolBound.height().toFloat(),
                            symWidth = symbolBound.width().toFloat(),
                            angle = block.lines.first().angle,
                        ),
                    )
                }
            } catch (e: Exception) {
                logcat { "ERROR : ${e.stackTraceToString()}" }
            }
        }
        resultant.translations.forEach { tt ->
            run {
                tt.x *= scale
                tt.y *= scale
                tt.width *= scale
                tt.height *= scale
                tt.symWidth *= scale
                tt.symHeight *= scale

            }
        }

        return resultant
    }

    private fun getTranslator(
        engine: LanguageTranslators,
        langFrom: ScanLanguage,
        langTo: Locale,
        key: String,
    ): LanguageTranslator {
        return when (engine) {
            LanguageTranslators.MLKIT -> MLKitTranslator(langFrom, langTo)
            LanguageTranslators.GOOGLE -> GoogleTranslator(langFrom, langTo)
            LanguageTranslators.OPENROUTER -> OpenRouterTranslator(langFrom, langTo, key)
            LanguageTranslators.GEMINI -> GeminiTranslator(langFrom, langTo, key)
        }
    }

    fun updateFromLanguage(language: ScanLanguage) {
        this.scanLanguage = language
        this.recognizer = TextRecognizer(scanLanguage)
        this.textTranslator = getTranslator(translationEngine, scanLanguage, translateLanguage, apiKey)
    }

    fun updateToLanguage(language: String) {
        try {
            this.translateLanguage = Locale.getAvailableLocales().first { it.language == language }
            this.textTranslator = getTranslator(translationEngine, scanLanguage, translateLanguage, apiKey)
        } catch (e: Exception) {
        }
    }


    fun updateEngine(engine: LanguageTranslators) {
        this.translationEngine = engine
        this.textTranslator = getTranslator(translationEngine, scanLanguage, translateLanguage, apiKey)
    }

    fun updateAPIKey(key: String) {
        this.apiKey = key
        this.textTranslator = getTranslator(translationEngine, scanLanguage, translateLanguage, apiKey)
    }

    fun updateFont(context: Context, fontIndex: Int) {
        this.font = fontIndex
        this.textPaint.typeface = ResourcesCompat.getFont(context, fonts[font])
    }

}

