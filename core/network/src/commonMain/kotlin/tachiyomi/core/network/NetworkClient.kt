package tachiyomi.core.network

/**
 * Minimal multiplatform HTTP client abstraction.
 *
 * The Android and desktop targets share a single OkHttp-backed implementation (see `jvmShared`).
 * A future iOS (Kotlin/Native) target would provide its own actual, e.g. backed by Ktor's Darwin
 * engine, without changing this common API.
 */
interface NetworkClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse
}

/**
 * Platform entry point that returns the default [NetworkClient] for the current target.
 */
expect fun httpClient(): NetworkClient
