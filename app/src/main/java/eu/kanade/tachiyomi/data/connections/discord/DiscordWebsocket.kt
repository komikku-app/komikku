// AM (DISCORD) -->

// Original library from https://github.com/dead8309/KizzyRPC (Thank you)
// Thank you to the 最高 man for the refactored and simplified code
// https://github.com/saikou-app/saikou
package eu.kanade.tachiyomi.data.connections.discord

import exh.log.xLogE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

sealed interface DiscordWebSocket : CoroutineScope {
    suspend fun sendActivity(presence: Presence)
    fun close()
}

open class DiscordWebSocketImpl(
    private val token: String,
) : DiscordWebSocket {

    private val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
    }

    companion object {
        private const val TAG = "DiscordWebSocket"

        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val request = Request.Builder()
        .url("wss://gateway.discord.gg/?encoding=json&v=10")
        .build()

    private var webSocket: WebSocket? = client.newWebSocket(request, Listener())

    private var connected = false

    private val connectionState = MutableStateFlow(false)

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.IO

    private fun sendIdentify() {
        val response = Identity.Response(
            op = 2,
            d = Identity(
                token = token,
                properties = Identity.Properties(
                    os = "windows",
                    browser = "Chrome",
                    device = "disco",
                ),
                compress = false,
                intents = 0,
            ),
        )
        webSocket?.send(json.encodeToString(response))
    }

    @Suppress("MagicNumber")
    override fun close() {
        Timber.tag(TAG).i("Closing Discord WebSocket, sending offline status")
        webSocket?.send(
            json.encodeToString(
                Presence.Response(
                    op = OpCode.DISPATCH.value.toLong(),
                    d = Presence(status = "offline"),
                ),
            ),
        )
        webSocket?.close(4000, "Interrupt")
        connected = false
        connectionState.value = false
    }

    override suspend fun sendActivity(presence: Presence) {
        try {
            // Wait for connection with a 30-second timeout
            withTimeout(30_000) {
                connectionState.filter { it }.first()
            }
            Timber.tag(TAG).i("Sending ${OpCode.PRESENCE_UPDATE}")
            val response = Presence.Response(
                op = OpCode.PRESENCE_UPDATE.value.toLong(),
                d = presence,
            )
            val message = json.encodeToString(response)
            Timber.tag(TAG).d("Sending message: $message")
            val rtn = webSocket?.send(message)
            if (rtn != true) xLogE("Failed to send ${OpCode.PRESENCE_UPDATE}")
        } catch (e: TimeoutCancellationException) {
            Timber.tag(TAG).e("Timeout waiting for Discord connection - skipping activity update: ${e.message}")
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error sending Discord activity: ${e.message}")
        }
    }

    inner class Listener : WebSocketListener() {
        private var seq: Int? = null
        private var heartbeatInterval: Long? = null

        var scope = CoroutineScope(coroutineContext)

        private fun sendHeartBeat(sendIdentify: Boolean) {
            scope.cancel()
            scope = CoroutineScope(coroutineContext)
            scope.launch {
                delay(heartbeatInterval!!)
                webSocket?.send("{\"op\":1, \"d\":$seq}")
            }
            if (sendIdentify) sendIdentify()
        }

        @Suppress("MagicNumber")
        override fun onMessage(webSocket: WebSocket, text: String) {
            Timber.tag(TAG).d("Message received : $text")

            val map = json.decodeFromString<Res>(text)
            seq = map.s

            when (map.op) {
                OpCode.HELLO.value -> {
                    map.d
                    heartbeatInterval = map.d.jsonObject["heartbeat_interval"]!!.jsonPrimitive.long
                    sendHeartBeat(true)
                }
                OpCode.DISPATCH.value -> if (map.t == "READY") {
                    connected = true
                    connectionState.value = true
                }
                OpCode.HEARTBEAT.value -> {
                    if (scope.isActive) scope.cancel()
                    webSocket.send("{\"op\":1, \"d\":$seq}")
                }

                OpCode.HEARTBEAT_ACK.value -> sendHeartBeat(false)
                OpCode.RECONNECT.value -> webSocket.close(400, "Reconnect")
                OpCode.INVALID_SESSION.value -> sendHeartBeat(true)
            }
        }

        @Suppress("MagicNumber")
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.tag(TAG).i("Server Closed : $code $reason")
            if (code == 4000) {
                scope.cancel()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.tag(TAG).e("Failure : ${t.message}")
            if (t.message != "Interrupt") {
                this@DiscordWebSocketImpl.webSocket = client.newWebSocket(request, Listener())
            }
        }
    }
}
// <-- AM (DISCORD)
