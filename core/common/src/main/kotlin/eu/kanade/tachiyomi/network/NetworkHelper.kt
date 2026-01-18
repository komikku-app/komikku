package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import logcat.LogPriority
import okhttp3.Cache
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import okio.IOException
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.random.Random

open class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
    val isDebugBuild: Boolean,
) {

    open val cookieJar = AndroidCookieJar()

    /**
     * Base OkHttp client builder
     */
    private fun clientBuilder(
        connectTimeout: Long = 30,
        readTimeout: Long = 30,
        callTimeout: Long = 120,
    ): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            // ✅ Global concurrency limiter (rate-limit bypass logic)
            .addInterceptor { chain ->
                GlobalRequestLimiter.acquire()
                try {
                    chain.proceed(chain.request())
                } finally {
                    GlobalRequestLimiter.release()
                }
            }
            .cookieJar(cookieJar)
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .callTimeout(callTimeout, TimeUnit.SECONDS)
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
            builder.addNetworkInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                },
            )
        }

        return when (preferences.dohProvider().get()) {
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
            else -> builder
        }
    }

    open val client by lazy {
        clientBuilder()
            .addInterceptor(
                CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
            )
            .build()
    }

    /**
     * Client with custom timeouts (used for downloads)
     */
    private fun clientWithTimeOut(
        connectTimeout: Long = 30,
        readTimeout: Long = 30,
        callTimeout: Long = 120,
    ): OkHttpClient =
        clientBuilder(connectTimeout, readTimeout, callTimeout)
            .addInterceptor(
                CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
            )
            .build()

    /**
     * Download with retry + resume support
     */
    fun downloadFileWithResume(
        url: String,
        outputFile: File,
        progressListener: ProgressListener,
    ) {
        val client = clientWithTimeOut(callTimeout = 120)
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < MAX_RETRY) {
            try {
                val downloadedBytes = outputFile.length()
                val request = GET(
                    url = url,
                    headers = Headers.Builder()
                        .add("Range", "bytes=$downloadedBytes-")
                        .build(),
                )

                client.newCachelessCallWithProgress(request, progressListener)
                    .execute()
                    .use { response ->
                        if (response.isSuccessful) {
                            val startPosition =
                                if (response.code == 206) downloadedBytes else 0L

                            if (response.code != 206) {
                                outputFile.delete()
                            }

                            saveResponseToFile(response, outputFile, startPosition)
                            return
                        } else {
                            lastError = IOException("HTTP ${response.code}")
                            logcat(LogPriority.WARN) {
                                "Unexpected response code ${response.code}, retrying..."
                            }
                            if (response.code == 416) {
                                outputFile.delete()
                            }
                            attempt++
                            exponentialBackoff(attempt - 1)
                        }
                    }
            } catch (e: IOException) {
                lastError = e
                logcat(LogPriority.WARN) {
                    "Download interrupted: ${e.message}, retrying..."
                }
                attempt++
                exponentialBackoff(attempt - 1)
            }
        }

        throw IOException(
            buildString {
                append("Max retry attempts reached.")
                lastError?.let {
                    append(" Last error: ${it.message}")
                }
            },
            lastError,
        )
    }

    private fun saveResponseToFile(
        response: Response,
        outputFile: File,
        startPosition: Long,
    ) {
        val body = response.body

        RandomAccessFile(outputFile, "rw").use { file ->
            file.seek(startPosition)
            body.byteStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    file.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun exponentialBackoff(attempt: Int) {
        val delayMs = calculateExponentialBackoff(attempt)
        Thread.sleep(delayMs)
    }

    private fun calculateExponentialBackoff(
        attempt: Int,
        baseDelay: Long = 1000L,
        maxDelay: Long = 32000L,
    ): Long {
        val delay = baseDelay * 2.0.pow(attempt).toLong()
        logcat(LogPriority.INFO) { "Retry backoff: ${delay}ms" }
        return (delay + Random.nextLong(0, 1000)).coerceAtMost(maxDelay)
    }

    /**
     * Deprecated compatibility property
     */
    @Deprecated(
        "The regular client handles Cloudflare by default",
        ReplaceWith("client"),
    )
    @Suppress("UNUSED")
    open val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider(): String =
        preferences.defaultUserAgent().get().trim()

    companion object {
        private const val MAX_RETRY = 5
    }
}
