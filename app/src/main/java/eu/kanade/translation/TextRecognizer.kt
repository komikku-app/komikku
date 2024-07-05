package eu.kanade.translation

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import eu.kanade.translation.translators.ScanLanguage
class TextRecognizer(scanLanguage: ScanLanguage) {

    private var recognizer = TextRecognition.getClient(getTextRecognizerOptions(scanLanguage))
    suspend fun recognize(image: InputImage): Text {return recognizer.process(image).await()}
    private fun getTextRecognizerOptions(language: ScanLanguage): TextRecognizerOptionsInterface {
        return when (language) {
            ScanLanguage.Latin-> TextRecognizerOptions.DEFAULT_OPTIONS
            ScanLanguage.Chinese -> ChineseTextRecognizerOptions.Builder().build()
            ScanLanguage.Japanese -> JapaneseTextRecognizerOptions.Builder().build()
            ScanLanguage.Korean-> KoreanTextRecognizerOptions.Builder().build()
        }

    }

}
