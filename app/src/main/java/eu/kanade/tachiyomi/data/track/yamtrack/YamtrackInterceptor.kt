package eu.kanade.tachiyomi.data.track.yamtrack

import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class YamtrackInterceptor(private val yamtrack: Yamtrack) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = yamtrack.getApiToken()
        if (token.isBlank()) {
            throw IOException("Not authenticated with Yamtrack")
        }

        val authRequest = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .header("User-Agent", "Komikku v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .header("Accept", "application/json")
            .build()

        return chain.proceed(authRequest)
    }
}
