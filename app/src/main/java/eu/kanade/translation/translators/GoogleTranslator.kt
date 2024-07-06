package eu.kanade.translation.translators

import eu.kanade.translation.TextTranslation
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.extractor.ts.PsExtractor
import eu.kanade.tachiyomi.network.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tachiyomi.core.common.util.system.logcat
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale
import kotlin.jvm.internal.Intrinsics

class GoogleTranslator(private val langFrom: ScanLanguage, private val langTo: Locale) : LanguageTranslator {
    private val client1 = "gtx"
    private val client2 = "webapp"
    val okHttpClient = OkHttpClient()
    override suspend fun translate(pages: HashMap<String, List<TextTranslation>>) {
        try {
            pages.forEach { (k,v)->v.forEach { b->b.translated=translateText(langTo.language,b.text) }}

        } catch (e: Exception) {
            logcat { "Image Translation Error : ${e.message}" }
        }

    }
    private fun rshift(j: Long, j2: Long): Long {
        var j = j
        if (j < 0) {
            j += 4294967296L
        }
        return j shr (j2.toInt())
    }
    private fun getTranslateUrl(lang: String, text: String): String {
        try {
            val client = client1
            val calculateToken = calculateToken(text)
            val encode: String = URLEncoder.encode(text, "utf-8")
            return "https://translate.google.com/translate_a/single?client=$client&sl=auto&tl=$lang&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk=$calculateToken&q=$encode"
        } catch (unused: UnsupportedEncodingException) {
            val client2 = client1
            val calculateToken2 = calculateToken(text)
            return "https://translate.google.com/translate_a/single?client=$client2&sl=auto&tl=$lang&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk=$calculateToken2&q=$text"
        }
    }

    @OptIn(UnstableApi::class)
    private fun calculateToken(str: String): String {
        var i = 0
        val list = ArrayList<Int>()

        var i2 = 0
        while (i2 < str.length) {
            val charCodeAt = charCodeAt(str, i2)
            if (charCodeAt < 128) {
                list.add(charCodeAt)
            } else if (charCodeAt < 2048) {
                list.add(Integer.valueOf((charCodeAt shr 6) or PsExtractor.AUDIO_STREAM))
                list.add((charCodeAt and 63) or 128)
            } else if ((55296 == (charCodeAt and 64512) && (i2 + 1).also {
                    i = it
                } < str.length) && 56320 == (64512 and charCodeAt(str, i))) {
                val charCodeAt2 =
                    ((charCodeAt and AnalyticsListener.EVENT_DRM_KEYS_LOADED) shl 10) + 65536 + (charCodeAt(
                        str,
                        i,
                    ) and AnalyticsListener.EVENT_DRM_KEYS_LOADED)
                list.add(Integer.valueOf((charCodeAt2 shr 18) or PsExtractor.VIDEO_STREAM_MASK))
                list.add(((charCodeAt2 shr 12) and 63) or 128)
                list.add((charCodeAt2 and 63) or 128)
                i2 = i
            } else {
                list.add((charCodeAt shr 12) or 224)
                list.add(((charCodeAt shr 6) and 63) or 128)
                list.add((charCodeAt and 63) or 128)
            }
            i2++
        }
        val size = list.size
        var j: Long = 406644
        for (i3 in 0 until size) {
            val obj = list[i3]
            j = RL(j + (obj as Number).toLong(), "+-a^+6")
        }
        var rL = RL(j, "+-3^+b+-f") xor 3293161072L
        if (0 > rL) {
            rL = (rL and 2147483647L) + 2147483648L
        }
        val j2 = rL % (1000000L)
        return j2.toString() + "." + (406644L xor j2)
    }

    private fun RL(j: Long, str: String): Long {
        var j = j
        var i: Int
        var i2 = 0
        while (i2 < str.length - 2) {
            val charArray = str.toCharArray()
            val c = charArray[i2 + 2]
            if (Intrinsics.compare(c.code, 97) >= 0) {
                i = c.code - 'W'.code
            } else {
                val sb = StringBuilder()
                sb.append(c)
                i = sb.toString().toInt()
            }
            val j2 = i.toLong()
            val rshift2 = if (charArray[i2 + 1] == '+') rshift(j, j2) else j shl (j2.toInt())
            j = if (charArray[i2] == '+') (j + rshift2) and 4294967295L else j xor rshift2
            i2 += 3
        }
        return j
    }

    private fun charCodeAt(str: String, i: Int): Int {
        return Character.codePointAt(str, i)
    }

    suspend fun translateText(lang: String, text: String) :String{

        val access = getTranslateUrl(lang, text)
        val build: Request = Request.Builder().url(access).build()
        val newCall = okHttpClient.newCall(build)
        val response = newCall.await()
        val body = response.body
        val string = body.string()
        try {
            val jSONArray = JSONArray(string).getJSONArray(0)
            val length = jSONArray.length()
            var str = ""
            for (i in 0 until length) {
                val string2 = jSONArray.getJSONArray(i).getString(0)
                if (string2 != null && !Intrinsics.areEqual(string2 as Any, "null" as Any)) {
                    str += jSONArray.getJSONArray(i).getString(0)
                }
            }
            return str;

        } catch (e: Exception) {
            logcat { "Image Translation Error : ${e.toString()}" }
        }
        return "";
    }
}
