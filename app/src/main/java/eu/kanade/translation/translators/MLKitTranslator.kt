package eu.kanade.translation.translators


import eu.kanade.translation.TextTranslation
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import tachiyomi.core.common.util.system.logcat
import java.util.Locale

class MLKitTranslator(private val langFrom: ScanLanguage, private val langTo: Locale) : LanguageTranslator {

    private var translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(langFrom.code).setTargetLanguage(TranslateLanguage.fromLanguageTag(langTo.language)?:TranslateLanguage.ENGLISH)
            .build(),
    )
    private var conditions = DownloadConditions.Builder().build()
    override suspend fun translate(pages: HashMap<String, List<TextTranslation>>) {
        try {

            translator.downloadModelIfNeeded(conditions).await()
//             translator.downloadModelIfNeeded(conditions).await()
            pages.mapValues { (k,v)->v.map {  b->b.translated=translator.translate(b.text).await() }}

        } catch (e: Exception) {
            logcat { "Image Translation Error : ${e.message}" }
        }

    }
}
