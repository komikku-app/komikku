package eu.kanade.tachiyomi.network.interceptor

import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.NetworkPreferences
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

class FlareSolverrInterceptor(private val preferences: NetworkPreferences) : Interceptor {
    private val cookieManager = CookieManager.getInstance()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // FlareSolverr is disabled, so just proceed with the request.
        if (!preferences.enableFlareSolverr().get()) {
            return chain.proceed(originalRequest)
        }

        Log.d("FlareSolverrInterceptor", "Intercepting request: ${originalRequest.url}")

        // First, ensure cf_clearance for subdomains if needed.
        ensureCfClearanceForSubdomain(originalRequest.url.toString())

        var cookiesString = ""

        // Check if the cf_clearance cookie exists and is valid for the base domain or the current subdomain.
        if (!isCfClearanceCookieValid(originalRequest.url.toString())) {
            // If the cf_clearance cookie is missing or not valid, get the cookies string from FlareSolverr.
            cookiesString = resolveWithFlareSolverr(originalRequest)

            // If cookies were found, parse and add them to the cookie jar.
            cookiesString.takeIf { it.isNotBlank() }?.let { cookies ->
                val url = originalRequest.url.toString()
                cookies.split("; ").forEach { cookie ->
                    Log.d("FlareSolverrInterceptor", "Adding cookie: $cookie, to $url")
                    cookieManager.setCookie(url, cookie)
                }
            }
        } else {
            // Here, the cf_clearance cookie is valid; we need to ensure it's included in the request for subdomains.
            val cfClearanceValue = getCookieValueForDomain(originalRequest.url.toString(), CF_COOKIE_NAME)
            if (cfClearanceValue != null) {
                cookiesString = "cf_clearance=$cfClearanceValue"
            }
        }

        // Create a new request with the cookies, whether they are newly obtained or retrieved from the cookie jar.
        val newRequest = if (cookiesString.isNotBlank()) {
            originalRequest.newBuilder()
                .header("Cookie", cookiesString.trimEnd(';'))
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }

    private fun resolveWithFlareSolverr(originalRequest: Request, addAllCookies: Boolean = true): String {
        try {
            val client = OkHttpClient()

            val flareSolverUrl = preferences.flareSolverrUrl().get().trim()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val data = JSONObject()
                .put("cmd", "request.get")
                .put("url", originalRequest.url.toString())
                .put("maxTimeout", 60000)
                .put("returnOnlyCookies", true)
                .toString()
            val body = data.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(flareSolverUrl)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            Log.d("FlareSolverrRequest", "Sending request to FlareSolverr: $flareSolverUrl with payload: $data")

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("HttpError", "Request failed with status code: ${response.code}")
                throw CloudflareBypassException("Failed with status code: ${response.code}")
            }

            val responseBody = response.body.string()
            Log.d("HttpResponse", responseBody)

            val jsonResponse = JSONObject(responseBody)
            val status = jsonResponse.optString("status")
            if (status == "ok") {
                val solution = jsonResponse.optJSONObject("solution")
                val cookiesArray = solution?.optJSONArray("cookies")

                val cookieStringBuilder = StringBuilder()
                cookiesArray?.let {
                    for (i in 0 until it.length()) {
                        val cookieObj = it.getJSONObject(i)
                        // Check if we should add all cookies or just cf_clearance
                        if (addAllCookies || cookieObj.getString("name") == "cf_clearance") {
                            val cookieString = "${cookieObj.getString("name")}=${cookieObj.getString("value")};"
                            cookieStringBuilder.append(cookieString)
                            if (i < cookiesArray.length() - 1) {
                                cookieStringBuilder.append(" ")
                            }
                        }
                    }
                }
                return cookieStringBuilder.toString().trimEnd()
            } else {
                throw CloudflareBypassException("Failed to solve challenge: $status")
            }
        } catch (e: Exception) {
            Log.e("HttpError", "Failed to resolve with FlareSolverr: ${e.message}", e)
            if (e is CloudflareBypassException) throw e
            throw CloudflareBypassException("Error resolving with FlareSolverr", e)
        }
    }

    /**
     * Checks if the `cf_clearance` cookie is present and valid for the base domain of the provided URL.
     * This is a critical check when accessing Cloudflare-protected sites, as the presence of this cookie
     * suggests that Cloudflare's challenge has already been solved for this domain. The method extends
     * this check to cover both the base domain and any subdomains, ensuring comprehensive coverage.
     *
     * @param url The URL as a [String] for which to verify the presence of the `cf_clearance` cookie.
     * @return [Boolean] `true` if the `cf_clearance` cookie is found for the base domain of the URL,
     * indicating that Cloudflare's challenge page might not be triggered. Returns `false` if the cookie
     * is not found or considered invalid, suggesting that access may be blocked by Cloudflare.
     */
    private fun isCfClearanceCookieValid(url: String): Boolean {
        // Checks if the cf_clearance cookie is valid. This function has been updated to check for the cookie's presence across both the base domain and any subdomains.
        val baseDomain = getBaseDomain(url)
        val checkUrl = "https://www.$baseDomain/"
        val cookiesStringForBaseDomain = cookieManager.getCookie(checkUrl)
        val hasCfClearanceCookie = cookiesStringForBaseDomain?.contains("cf_clearance=") ?: false
        Log.d("CookieManager", "cf_clearance cookie for $checkUrl: $hasCfClearanceCookie")
        return hasCfClearanceCookie
    }

    /**
     * Ensures that if a valid `cf_clearance` cookie exists for the base domain, it is also set for
     * any subdomains accessed. This method is useful for scenarios where a Cloudflare-protected
     * site's base domain has passed Cloudflare's challenge, and subsequent requests to its subdomains
     * should inherit this clearance without needing to solve the challenge again.
     *
     * The method checks for the `cf_clearance` cookie's presence and validity for the base domain.
     * If found and valid, it sets the same `cf_clearance` cookie for the subdomain of the URL provided.
     *
     * @param url The URL as a [String] indicating the subdomain for which the `cf_clearance` cookie
     * should be set, assuming it's valid for the base domain.
     */
    private fun ensureCfClearanceForSubdomain(url: String) {
        val uri = Uri.parse(url)
        val host = uri.host ?: return
        val baseDomain = getBaseDomain(url)
        if (host != "www.$baseDomain" && isCfClearanceCookieValid("https://www.$baseDomain/")) {
            val cfClearanceValue = getCookieValueForDomain("https://www.$baseDomain/", CF_COOKIE_NAME)
            if (cfClearanceValue != null) {
                val subdomainUrl = "https://$host/"
                cookieManager.setCookie(subdomainUrl, "cf_clearance=$cfClearanceValue")
                Log.d("CookieManager", "Set cf_clearance for $subdomainUrl: cf_clearance=$cfClearanceValue")
            }
        }
    }

    private fun getBaseDomain(url: String): String {
        val uri = Uri.parse(url)
        val host = uri.host ?: return ""
        val parts = host.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    private fun getCookieValueForDomain(url: String, cookieName: String): String? {
        val cookiesString = cookieManager.getCookie(url)
        return cookiesString?.split("; ")?.firstOrNull { it.startsWith("$cookieName=") }
            ?.substringAfter("$cookieName=")
    }
}

private const val CF_COOKIE_NAME = "cf_clearance"
