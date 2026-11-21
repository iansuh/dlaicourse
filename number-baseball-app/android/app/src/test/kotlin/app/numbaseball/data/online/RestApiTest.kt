package app.numbaseball.data.online

import app.numbaseball.core.protocol.ReportReason
import app.numbaseball.core.protocol.ReportRequest
import app.numbaseball.data.account.ApiResult
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestApiTest {
    private val server = MockWebServer()
    private lateinit var api: RestApi

    @Before fun setUp() {
        server.start()
        api = RestApi(OkHttpClient(), ServerEndpoints(server.url("/").toString()), clientVersion = "1.2.3")
    }

    @After fun tearDown() = server.close()

    @Test fun endpointsFollowTheVersionedPaths() {
        val endpoints = ServerEndpoints("https://nb.example.com")
        assertEquals("https://nb.example.com/api/v1", endpoints.api.toString())
        assertEquals("https://nb.example.com/ws/v1", endpoints.webSocket.toString())
    }

    @Test fun createAccountPostsTheNicknameAndParsesTheToken() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(201).body(
                """{"token":"t0k3n","account":{"id":"k2m9x4q1ab7p","nickname":"Mina","tag":"0420",""" +
                    """"stats":{"wins":0,"losses":0,"draws":0},"createdAt":1795000000000},"future":"ignored"}""",
            ).build(),
        )
        val result = api.createAccount("Mina")
        assertEquals("t0k3n", (result as ApiResult.Success).value.token)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/accounts", request.url.encodedPath)
        assertEquals("1.2.3", request.headers["X-Client-Version"])
        assertEquals("""{"nickname":"Mina"}""", request.body?.utf8())
    }

    @Test fun errorBodiesBecomeFailuresWithTheirCode() = runBlocking {
        server.enqueue(MockResponse.Builder().code(400).body("""{"code":"nickname_profanity","message":"no"}""").build())
        assertEquals(ApiResult.Failure(400, "nickname_profanity", null), api.createAccount("x"))
        server.enqueue(MockResponse.Builder().code(429).body("not json").build())
        assertEquals(ApiResult.Failure(429, "rate_limited", null), api.createAccount("x"))
    }

    @Test fun authenticatedCallsSendTheBearerToken() = runBlocking {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ApiResult.Success(Unit), api.block("t0k3n", "z8m1q0w2e3r4"))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/me/blocks/z8m1q0w2e3r4", request.url.encodedPath)
        assertEquals("Bearer t0k3n", request.headers["Authorization"])
    }

    @Test fun reportsSendTheProtocolBody() = runBlocking {
        server.enqueue(MockResponse.Builder().code(201).body("""{"reportId":7}""").build())
        val result = api.report("t", ReportRequest("z8m1q0w2e3r4", ReportReason.HARASSMENT, "r_1", 3, null))
        assertEquals(7L, (result as ApiResult.Success).value.reportId)
        val body = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(body, body.contains("\"reason\":\"harassment\"") && body.contains("\"messageId\":3"))
    }

    @Test fun unreachableServerIsANetworkError() = runBlocking {
        server.close()
        assertEquals(ApiResult.NetworkError, api.me("t"))
    }
}
