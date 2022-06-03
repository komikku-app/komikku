package exh.patch

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

typealias EHInterceptor = (request: Request, response: Response, sourceId: Long) -> Response

fun OkHttpClient.Builder.injectPatches(sourceIdProducer: () -> Long): OkHttpClient.Builder {
    return addInterceptor { chain ->
        val req = chain.request()
        val response = chain.proceed(req)
        val sourceId = sourceIdProducer()
        findAndApplyPatches(sourceId)(req, response, sourceId)
    }
}

fun findAndApplyPatches(sourceId: Long): EHInterceptor {
    // TODO make it so captcha doesnt auto open in manga eden while applying universal interceptors
    return if (Injekt.get<PreferencesHelper>().autoSolveCaptcha().get()) (EH_INTERCEPTORS[sourceId].orEmpty() + EH_INTERCEPTORS[EH_UNIVERSAL_INTERCEPTOR].orEmpty()).merge()
    else EH_INTERCEPTORS[sourceId].orEmpty().merge()
}

fun List<EHInterceptor>.merge(): EHInterceptor {
    return { request, response, sourceId ->
        fold(response) { acc, int ->
            int(request, acc, sourceId)
        }
    }
}

private const val EH_UNIVERSAL_INTERCEPTOR = -1L
private val EH_INTERCEPTORS: Map<Long, List<EHInterceptor>> = mapOf(
    EH_UNIVERSAL_INTERCEPTOR to listOf(
        CAPTCHA_DETECTION_PATCH, // Auto captcha detection
    ),
)
