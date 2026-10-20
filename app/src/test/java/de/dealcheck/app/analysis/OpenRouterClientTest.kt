package de.dealcheck.app.analysis

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenRouterClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenRouterClient

    private val validJson = """{"geraet":"Laptop","modell":"ThinkPad T480","empfehlung":"KAUFEN","risiken":[]}"""

    private fun chatResponse(content: String) = MockResponse().setBody(
        JSONObject().put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))),
        ).toString(),
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OpenRouterClient(endpoint = server.url("/api/v1/chat/completions").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun sendsImagesAndParsesResult() = runTest {
        server.enqueue(chatResponse("```json\n$validJson\n```"))
        val result = client.analyze("sk-test", "google/gemini-2.5-flash", webSearch = true, listOf("QUJD"), "")
        assertEquals(Recommendation.KAUFEN, result.analysis.empfehlung)

        val req = server.takeRequest()
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("google/gemini-2.5-flash", body.getString("model"))
        assertEquals("web", body.getJSONArray("plugins").getJSONObject(0).getString("id"))
        val content = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("data:image/jpeg;base64,QUJD", content.getJSONObject(1).getJSONObject("image_url").getString("url"))
    }

    @Test
    fun noPluginsWithoutWebSearch() = runTest {
        server.enqueue(chatResponse(validJson))
        client.analyze("sk-test", "m", webSearch = false, listOf("QUJD"), "")
        assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("plugins"))
    }

    @Test
    fun retriesOnceOnInvalidJson() = runTest {
        server.enqueue(chatResponse("Das ist leider kein JSON."))
        server.enqueue(chatResponse(validJson))
        val result = client.analyze("sk-test", "m", false, listOf("QUJD"), "")
        assertEquals("ThinkPad T480", result.analysis.modell)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val retryMessages = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals(4, retryMessages.length())
        assertEquals("assistant", retryMessages.getJSONObject(2).getString("role"))
    }

    @Test
    fun failsAfterSecondInvalidAnswer() = runTest {
        server.enqueue(chatResponse("nein"))
        server.enqueue(chatResponse("immer noch nein"))
        try {
            client.analyze("sk-test", "m", false, listOf("QUJD"), "")
            fail("Exception erwartet")
        } catch (e: DealCheckException) {
            assertTrue(e.message!!.contains("zweimal"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun mapsHttpErrorsToGermanMessages() = runTest {
        val cases = mapOf(
            401 to "API-Key ungültig",
            402 to "Guthaben ist leer",
            429 to "Zu viele Anfragen",
        )
        for ((code, expected) in cases) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"error":{"message":"x","code":$code}}"""))
            try {
                client.analyze("sk-test", "m", false, listOf("QUJD"), "")
                fail("Exception erwartet für $code")
            } catch (e: DealCheckException) {
                assertTrue("$code: ${e.message}", e.message!!.contains(expected))
            }
        }
    }

    @Test
    fun missingKeyFailsWithoutRequest() = runTest {
        try {
            client.analyze(" ", "m", false, listOf("QUJD"), "")
            fail("Exception erwartet")
        } catch (e: DealCheckException) {
            assertTrue(e.message!!.contains("Kein API-Key"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun noConnectionMessage() {
        val e = OpenRouterClient.mapIoError(java.net.UnknownHostException("openrouter.ai"))
        assertTrue(e.message!!.contains("Keine Internetverbindung"))
        val t = OpenRouterClient.mapIoError(java.net.SocketTimeoutException())
        assertTrue(t.message!!.contains("Zeitüberschreitung"))
    }
}
