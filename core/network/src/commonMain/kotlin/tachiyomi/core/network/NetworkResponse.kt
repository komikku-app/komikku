package tachiyomi.core.network

/**
 * Platform-agnostic representation of an HTTP response.
 */
data class NetworkResponse(
    val statusCode: Int,
    val isSuccessful: Boolean,
    val body: String,
    val headers: Map<String, String>,
)
