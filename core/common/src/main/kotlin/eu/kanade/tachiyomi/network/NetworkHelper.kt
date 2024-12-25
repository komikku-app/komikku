package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.FlareSolverrInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.serialization.json.Json
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

/* SY --> */
open /* SY <-- */ class NetworkHelper(
    private val context: Context,
    private val preferences: NetworkPreferences,
    // KMK -->
    private val json: Json,
    // KMK <--
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
            .addNetworkInterceptor(
                FlareSolverrInterceptor(
                    preferences = preferences,
                    network = this,
                    json = json,
                ),
            )

        if (isDebugBuild) {
            val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addNetworkInterceptor(httpLoggingInterceptor)
        }

        builder.addInterceptor(
            CloudflareInterceptor(context, cookieJar, preferences, ::defaultUserAgentProvider),
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

    // KMK -->
    /**
     * Allow to download a big file with retry & resume capability because
     * normally it would get a Timeout exception.
     */
    fun downloadFileWithResume(url: String, outputFile: File, progressListener: ProgressListener) {
        val client = clientWithTimeOut(
            callTimeout = 120,
        )

        var downloadedBytes: Long

        var attempt = 0

        while (attempt < MAX_RETRY) {
            try {
                // Check how much has already been downloaded
                downloadedBytes = outputFile.length()
                // Set up request with Range header to resume from the last byte
                val request = GET(
                    url = url,
                    headers = Headers.Builder()
                        .add("Range", "bytes=$downloadedBytes-")
                        .build(),
                )

                var failed = false
                client.newCachelessCallWithProgress(request, progressListener).execute().use { response ->
                    if (response.isSuccessful || response.code == 206) { // 206 indicates partial content
                        saveResponseToFile(response, outputFile, downloadedBytes)
                        if (response.isSuccessful) {
                            return
                        }
                    } else {
                        attempt++
                        logcat(LogPriority.ERROR) { "Unexpected response code: ${response.code}. Retrying..." }
                        if (response.code == 416) {
                            // 416: Range Not Satisfiable
                            outputFile.delete()
                        }
                        failed = true
                    }
                }
                if (failed) exponentialBackoff(attempt - 1)
            } catch (e: IOException) {
                logcat(LogPriority.ERROR) { "Download interrupted: ${e.message}. Retrying..." }
                // Wait or handle as needed before retrying
                attempt++
                exponentialBackoff(attempt - 1)
            }
        }
        throw IOException("Max retry attempts reached.")
    }

    // Helper function to save data incrementally
    private fun saveResponseToFile(response: Response, outputFile: File, startPosition: Long) {
        val body = response.body

        // Use RandomAccessFile to write from specific position
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

    // Increment attempt and apply exponential backoff
    private fun exponentialBackoff(attempt: Int) {
        val backoffDelay = calculateExponentialBackoff(attempt)
        Thread.sleep(backoffDelay)
    }

    // Helper function to calculate exponential backoff with jitter
    private fun calculateExponentialBackoff(attempt: Int, baseDelay: Long = 1000L, maxDelay: Long = 32000L): Long {
        // Calculate the exponential delay
        val delay = baseDelay * 2.0.pow(attempt).toLong()
        logcat(LogPriority.ERROR) { "Exponential backoff delay: $delay ms" }
        // Apply jitter by adding a random value to avoid synchronized retries in distributed systems
        return (delay + Random.nextLong(0, 1000)).coerceAtMost(maxDelay)
    }
    // KMK <--

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default", ReplaceWith("client"))
    @Suppress("UNUSED")
    /* SY --> */
    open /* SY <-- */val cloudflareClient: OkHttpClient
        get() = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent().get().trim()

    companion object {
        // KMK -->
        private const val MAX_RETRY = 5
        // KMK <--
    }
}
