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

/* SY --> */ open /* SY <-- */ class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
    val isDebugBuild: Boolean,
) {

    /* SY --> */ open /* SY <-- */ val cookieJar = AndroidCookieJar()

    /**
     * Builds a shared OkHttpClient with a global concurrency limiter.
     * Extension-specific rate limits are intentionally ignored.
     */
    private fun clientBuilder(
        connectTimeout: Long = 30,
        readTimeout: Long = 30,
        callTimeout: Long = 120,
    ): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            // Global concurrency limiter (fork-only behavior)
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

    @Suppress("unused")
    val nonCloudflareClient by lazy { clientBuilder().build() }

    /* SY --> */ open /* SY <-- */ val client by lazy {
        clientBuilder()
            .addInterceptor(
                CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
            )
            .build()
    }

    /**
     * Client with extended timeout for large downloads.
     */
    private fun clientWithTimeOut(
        connectTimeout: Long = 30,
        readTimeout: Long = 30,
        callTimeout: Long = 120,
    ) = clientBuilder(connectTimeout, readTimeout, callTimeout)
        .addInterceptor(
            CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
        )
        .build()

    /**
     * Download a large file with resume + retry support.
     */
    fun downloadFileWithResume(
        url: String,
        outputFile: File,
        progressListener: ProgressListener,
    ) {
        val client = clientWithTimeOut(callTimeout = 120)
        var attempt = 0

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
                            attempt++
                            logcat(LogPriority.ERROR) {
                                "Unexpected response code: ${response.code}. Retrying..."
                            }
                            if (response.code == 416) {
                                outputFile.delete()
                            }
                            exponentialBackoff(attempt - 1)
                        }
                    }
            } catch (e: IOException) {
                logcat(LogPriority.ERROR) {
                    "Download interrupted: ${e.message}. Retrying..."
                }
                attempt++
                exponentialBackoff(attempt - 1)
            }
        }

        throw IOException("Max retry attempts reached.")
    }

    /**
     * Writes response body to file, starting at [startPosition].
     */
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

    /**
     * Exponential backoff between retries.
     */
    private fun exponentialBackoff(attempt: Int) {
        val backoffDelay = calculateExponentialBackoff(attempt)
        Thread.sleep(backoffDelay)
    }

    private fun calculateExponentialBackoff(
        attempt: Int,
        baseDelay: Long = 1000L,
        maxDelay: Long = 32000L,
    ): Long {
        val delay = baseDelay * 2.0.pow(attempt).toLong()
        logcat(LogPriority.ERROR) { "Exponential backoff delay: $delay ms" }
        return (delay + Random.nextLong(0, 1000)).coerceAtMost(maxDelay)
    }

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated(
        "The regular client handles Cloudflare by default",
        ReplaceWith("client"),
    )
    @Suppress("UNUSED")
    open val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider() =
        preferences.defaultUserAgent().get().trim()

    companion object {
        private const val MAX_RETRY = 5
    }
}
