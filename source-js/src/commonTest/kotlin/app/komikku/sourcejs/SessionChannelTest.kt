package app.komikku.sourcejs

import app.komikku.sourcejs.engine.JsEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The host side of the bindings, driven without an engine. */
class SessionChannelTest {

    /** Records the functions [JsSession.install] defines. */
    private class CapturingEngine : JsEngine {
        val sync = mutableMapOf<String, (List<String?>) -> String?>()

        override suspend fun defineFunction(name: String, function: (args: List<String?>) -> String?) {
            sync[name] = function
        }

        override suspend fun defineSuspendFunction(name: String, function: suspend (args: List<String?>) -> String?) = Unit
        override suspend fun compile(script: String, fileName: String): ByteArray = ByteArray(0)
        override suspend fun evaluate(script: String, fileName: String) = Unit
        override suspend fun evaluate(bytecode: ByteArray) = Unit
        override suspend fun memoryUsedBytes(): Long? = null
        override suspend fun close() = Unit
    }

    @Test
    fun anUnknownChannelIsAHostFailure() = runTest {
        val test = TestHost()
        val session = JsSession(testSource, test.services, JsHostLimits())
        val engine = CapturingEngine()
        session.install(engine)
        val reply = engine.sync.getValue(JsSession.SYNC_FUNCTION)(listOf("no_such_channel", "[]")).orEmpty()
        assertTrue(reply.startsWith("e"), reply)
        val failure = assertIs<HostFunctionFailure.Bridge>(session.failure(1))
        assertEquals("no_such_channel", failure.channel)
        assertEquals("No channel \"no_such_channel\" registered", failure.message)
    }
}
