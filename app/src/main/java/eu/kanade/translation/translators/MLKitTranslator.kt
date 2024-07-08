package eu.kanade.translation.translators


import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.translation.TextTranslations
import kotlinx.coroutines.tasks.await
import tachiyomi.core.common.util.system.logcat
import java.util.Locale

class MLKitTranslator(langFrom: ScanLanguage, langTo: Locale) : LanguageTranslator {

    private var translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(langFrom.code)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(langTo.language) ?: TranslateLanguage.ENGLISH)
            .build(),
    )
    private var conditions = DownloadConditions.Builder().build()
    override suspend fun translate(pages: HashMap<String, TextTranslations>) {
        try {

            translator.downloadModelIfNeeded(conditions).await()
//             translator.downloadModelIfNeeded(conditions).await()
            pages.mapValues { (_, v) ->
                v.translations.map { b ->
                    b.translated = translator.translate(b.text).await()
                }
            }

        } catch (e: Exception) {
            logcat { "Image Translation Error : ${e.message}" }
        }

    }
}
