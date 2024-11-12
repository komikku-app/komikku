package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/* SY --> */
open /* SY <-- */ class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
    // SY -->
    val isDebugBuild: Boolean,
    // SY <--
) {

    /* SY --> */
    open /* SY <-- */val cookieJar = AndroidCookieJar()

    /* SY --> */
    open /* SY <-- */val client: OkHttpClient =
        // KMK -->
        clientWithTimeOut()

    /**
     * Timeout in unit of seconds.
     */
    fun clientWithTimeOut(
        connectTimeout: Long = 30,
        readTimeout: Long = 30,
        callTimeout: Long = 120,
        // KMK <--
    ): OkHttpClient = run {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // KMK -->
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .callTimeout(callTimeout, TimeUnit.SECONDS)
            // KMK <--
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)

        if (isDebugBuild) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        builder.addInterceptor(
            CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
        )

        when (preferences.dohProvider().get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
        }

        builder.build()
    }

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default", ReplaceWith("client"))
    @Suppress("UNUSED")
    /* SY --> */
    open /* SY <-- */val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent().get().trim()
}
