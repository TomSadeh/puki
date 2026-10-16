package io.github.tomsadeh.puki.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

/** HTTPS to a stand-in PC (a test-only certificate, see resources/tls): only the pinned one is talked to. */
class PinnedTlsTest {
    private lateinit var server: SSLServerSocket
    private var requests = 0
    private val fingerprint = "0d971d2f8f21401bb172cd02c2c689b109e50911b889596b0eb825c25f5a0cf5"

    private fun resource(name: String) = javaClass.getResourceAsStream("/tls/$name")!!.readBytes()

    @Before
    fun startPc() {
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(resource("test_cert.pem").inputStream()) as X509Certificate
        val pem = String(resource("test_key.pem")).lines().filter { !it.startsWith("-----") }.joinToString("")
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
        val store = KeyStore.getInstance("PKCS12").apply { load(null); setKeyEntry("pc", key, CharArray(0), arrayOf(cert)) }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
        val ctx = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        server = ctx.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        s.use {
                            val reader = it.getInputStream().bufferedReader()
                            while (reader.readLine()?.isNotEmpty() == true) Unit  // the request's head
                            requests++
                            it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        }
                    }
                }
            }
        }
    }

    @After
    fun stopPc() = server.close()

    private fun url() = "https://127.0.0.1:${server.localPort}/health"

    @Test
    fun thePinnedPcAnswers() {
        val conn = PinnedTls.open(url(), fingerprint)
        assertEquals(200, conn.responseCode)
        assertEquals("ok", conn.inputStream.bufferedReader().readText())
    }

    @Test
    fun anotherCertificateIsRefusedBeforeAnythingIsSent() {
        try {
            PinnedTls.open(url(), "00".repeat(32)).responseCode
            fail("connected to a PC with another certificate")
        } catch (e: Exception) {
            assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it is PinnedTls.WrongPc })
        }
        assertEquals(0, requests)
    }

    @Test
    fun firstUseReportsTheCertificate() {
        var seen = ""
        assertEquals(200, PinnedTls.open(url(), "") { seen = it }.responseCode)
        assertEquals(fingerprint, seen)
    }
}
