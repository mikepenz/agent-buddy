package com.mikepenz.agentapprover.risk

import com.mikepenz.agentapprover.model.HookInput
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OllamaRiskAnalyzerTest {

    private lateinit var server: HttpServer
    private val port: Int get() = server.address.port

    private val tagsBody = """
        {"models":[{"name":"llama3.2"},{"name":"qwen2.5"}]}
    """.trimIndent()

    private val chatBody = """
        {"model":"llama3.2","message":{"role":"assistant","content":"{\"level\":2,\"label\":\"Low\",\"explanation\":\"Read-only command\"}"},"done":true}
    """.trimIndent()

    private fun hookInput() = HookInput(
        sessionId = "s",
        toolName = "Bash",
        toolInput = mapOf("command" to JsonPrimitive("ls")),
        cwd = "/tmp",
        agentType = null,
    )

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = null
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun handle(path: String, body: String, status: Int = 200, latch: CountDownLatch? = null, overlap: AtomicInteger? = null, max: AtomicInteger? = null) {
        server.createContext(path, HttpHandler { exchange: HttpExchange ->
            overlap?.let {
                val cur = it.incrementAndGet()
                max?.updateAndGet { m -> if (cur > m) cur else m }
            }
            latch?.await(2, TimeUnit.SECONDS)
            try {
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                overlap?.decrementAndGet()
            }
        })
    }

    @Test
    fun startSucceedsWhenTagsReturnsModels() = runBlocking {
        handle("/api/tags", tagsBody)
        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$port", model = "llama3.2")
        try {
            analyzer.start()
            val models = analyzer.listModels().getOrThrow()
            assertEquals(listOf("llama3.2", "qwen2.5"), models)
        } finally {
            analyzer.shutdown()
        }
    }

    @Test
    fun analyzeParsesStructuredResponse() = runBlocking {
        handle("/api/tags", tagsBody)
        handle("/api/chat", chatBody)
        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$port", model = "llama3.2")
        try {
            val result = analyzer.analyze(hookInput())
            val analysis = result.getOrThrow()
            assertEquals(2, analysis.risk)
            assertEquals("Low", analysis.label)
            assertEquals("ollama", analysis.source)
        } finally {
            analyzer.shutdown()
        }
    }

    @Test
    fun analyzeFailsWithReachabilityErrorWhenServerDown() = runBlocking {
        // Pick a port and immediately stop the server so connections refuse.
        val deadPort = port
        server.stop(0)
        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$deadPort", model = "llama3.2")
        try {
            val result = analyzer.analyze(hookInput())
            assertTrue(result.isFailure)
            val msg = result.exceptionOrNull()?.message.orEmpty()
            assertTrue(msg.contains("not reachable", ignoreCase = true), "got: $msg")
        } finally {
            analyzer.shutdown()
            // Re-create so AfterTest stop() is a no-op
            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        }
    }

    @Test
    fun concurrentAnalyzeCallsAreSerialised() = runBlocking {
        handle("/api/tags", tagsBody)
        val gate = CountDownLatch(1)
        val overlap = AtomicInteger(0)
        val maxOverlap = AtomicInteger(0)
        handle("/api/chat", chatBody, latch = gate, overlap = overlap, max = maxOverlap)

        val analyzer = OllamaRiskAnalyzer(baseUrl = "http://127.0.0.1:$port", model = "llama3.2")
        try {
            val a = async { analyzer.analyze(hookInput()) }
            val b = async { analyzer.analyze(hookInput()) }
            // Give the first request a moment to enter the handler before releasing.
            Thread.sleep(200)
            gate.countDown()
            a.await().getOrThrow()
            b.await().getOrThrow()
            assertEquals(1, maxOverlap.get(), "expected mutex to serialise calls")
        } finally {
            analyzer.shutdown()
        }
    }
}
