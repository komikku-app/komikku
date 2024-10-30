package eu.kanade.translation.translators

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.BlockTranslation
import eu.kanade.translation.TextTranslations
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import java.util.Locale

class OpenRouterTranslator(
    private val langFrom: ScanLanguage,
    private val langTo: Locale,
    private var apiKey: String,
    private var model: String = "google/gemma-2-9b-it:free",
) : LanguageTranslator {
    val okHttpClient = OkHttpClient()
    override suspend fun translate(pages: HashMap<String, TextTranslations>) {
        try {
            val data = pages.mapValues { (k, v) -> v.translations.map { b -> b.text } }
            val json = JSONObject(data)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val jsonObject = buildJsonObject {
                put("model", model)
                putJsonObject("response_format") { put("type", "json_object") }
                put("top_p", 0.5f)
                put("top_k", 30)
                put("max_tokens", 8192)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put(
                            "content",
                            "## System Prompt for Manhwa/Manga/Manhua Translation\n" +
                                "\n" +
                                "You are a highly skilled AI tasked with translating text from scanned images of comics (manhwa, manga, manhua) while preserving the original structure and removing any watermarks or site links. \n" +
                                "\n" +
                                "**Here's how you should operate:**\n" +
                                "\n" +
                                "1. **Input:** You'll receive a JSON object where keys are image filenames (e.g., \"001.jpg\") and values are lists of text strings extracted from those images.\n" +
                                "\n" +
                                "2. **Translation:** Translate all text strings to the target language `${langTo.displayLanguage}`. Ensure the translation is natural and fluent, adapting idioms and expressions to fit the target language's cultural context.\n" +
                                "\n" +
                                "3. **Watermark/Site Link Removal:** Replace any watermarks or site links (e.g., \"colamanga.com\") with the placeholder \"RTMTH\".\n" +
                                "\n" +
                                "4. **Structure Preservation:** Maintain the exact same structure as the input JSON. The output JSON should have the same number of keys (image filenames) and the same number of text strings within each list.\n" +
                                "\n" +
                                "**Example:**\n" +
                                "\n" +
                                "**Input:**\n" +
                                "\n" +
                                "```json\n" +
                                "{\"001.jpg\":[\"chinese1\",\"chinese2\"],\"002.jpg\":[\"chinese2\",\"colamanga.com\"]}\n" +
                                "```\n" +
                                "\n" +
                                "**Output (for `${langTo.displayLanguage}` = English):**\n" +
                                "\n" +
                                "```json\n" +
                                "{\"001.jpg\":[\"eng1\",\"eng2\"],\"002.jpg\":[\"eng2\",\"RTMTH\"]}\n" +
                                "```\n" +
                                "\n" +
                                "**Key Points:**\n" +
                                "\n" +
                                "* Prioritize accurate and natural-sounding translations.\n" +
                                "* Be meticulous in removing all watermarks and site links.\n" +
                                "* Ensure the output JSON structure perfectly mirrors the input structure.",
                        )
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", "JSON $json")
                    }
                }
            }.toString()
            val body = jsonObject.toRequestBody(mediaType)
            val access = "https://openrouter.ai/api/v1/chat/completions"
            val build: Request =
                Request.Builder().url(access).header(
                    "Authorization",
                    "Bearer $apiKey",
                ).header("Content-Type", "application/json").post(body).build()
            val response = okHttpClient.newCall(build).await()
            val rBody = response.body
            val json2 = JSONObject(rBody.string())
            val resJson =
                JSONObject(json2.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))

            for ((k, v) in pages) {
                v.translations.forEachIndexed { i, b ->
                    run {
                        val res = resJson.optJSONArray(k)?.optString(i, "NULL")
                        b.translated = if (res == null || res == "NULL") b.text else res
                    }
                }
                v.translations =
                    v.translations.filterNot { it.translated.contains("RTMTH") } as ArrayList<BlockTranslation>
            }
        } catch (e: Exception) {
            logcat { "Image Translation Error : ${e.stackTraceToString()}" }
        }
    }
}
