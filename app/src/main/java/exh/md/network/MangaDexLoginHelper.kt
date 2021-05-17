package exh.md.network

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.log.xLogI
import exh.md.handlers.serializers.CheckTokenResponse
import exh.md.handlers.serializers.LoginBodyToken
import exh.md.handlers.serializers.LoginRequest
import exh.md.handlers.serializers.LoginResponse
import exh.md.handlers.serializers.RefreshTokenRequest
import exh.md.handlers.serializers.ResultResponse
import exh.md.utils.MdUtil
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class MangaDexLoginHelper(val client: OkHttpClient, val preferences: PreferencesHelper, val mdList: MdList) {
    suspend fun isAuthenticated(authHeaders: Headers): Boolean {
        val response = client.newCall(GET(MdUtil.checkTokenUrl, authHeaders, CacheControl.FORCE_NETWORK)).await()
        val body = response.parseAs<CheckTokenResponse>(MdUtil.jsonParser)
        return body.isAuthenticated
    }

    suspend fun refreshToken(authHeaders: Headers): Boolean {
        val refreshToken = MdUtil.refreshToken(preferences, mdList)
        if (refreshToken.isNullOrEmpty()) {
            return false
        }
        val result = RefreshTokenRequest(refreshToken)
        val jsonString = MdUtil.jsonParser.encodeToString(result)
        val postResult = client.newCall(
            POST(
                MdUtil.refreshTokenUrl,
                authHeaders,
                jsonString.toRequestBody("application/json".toMediaType())
            )
        ).await()
        val refresh = runCatching {
            val jsonResponse = postResult.parseAs<LoginResponse>(MdUtil.jsonParser)
            preferences.trackToken(mdList).set(MdUtil.jsonParser.encodeToString(jsonResponse.token))
        }
        return refresh.isSuccess
    }

    suspend fun login(
        username: String,
        password: String,
    ): LoginResult {
        return withIOContext {
            val loginRequest = LoginRequest(username, password)

            val jsonString = MdUtil.jsonParser.encodeToString(loginRequest)

            val postResult = runCatching {
                client.newCall(
                    POST(
                        MdUtil.loginUrl,
                        Headers.Builder().build(),
                        jsonString.toRequestBody("application/json".toMediaType())
                    )
                ).await()
            }

            val response = postResult.getOrNull() ?: return@withIOContext LoginResult.Failure(postResult.exceptionOrNull())
            // if it fails to parse then login failed
            val loginResponse = try {
                response.parseAs<LoginResponse>(MdUtil.jsonParser)
            } catch (e: SerializationException) {
                null
            }

            if (response.code == 200 && loginResponse != null && loginResponse.result == "ok") {
                LoginResult.Success(loginResponse.token)
            } else {
                LoginResult.Failure()
            }
        }
    }

    sealed class LoginResult {
        data class Failure(val e: Throwable? = null) : LoginResult()
        data class Success(val token: LoginBodyToken) : LoginResult()
    }

    suspend fun login(): LoginResult {
        val username = preferences.trackUsername(mdList)
        val password = preferences.trackPassword(mdList)
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            xLogI("No username or password stored, can't login")
            return LoginResult.Failure()
        }
        return login(username, password)
    }

    suspend fun logout(authHeaders: Headers): Boolean {
        val response = client.newCall(GET(MdUtil.logoutUrl, authHeaders, CacheControl.FORCE_NETWORK)).await()
        val body = response.parseAs<ResultResponse>(MdUtil.jsonParser)
        return body.result == "ok"
    }
}
