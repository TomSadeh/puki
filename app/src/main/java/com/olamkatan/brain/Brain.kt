package com.olamkatan.brain

import java.net.HttpURLConnection
import java.net.URL

/**
 * Where Puki's thinking happens: the brain server on the family PC (brain/brain.py), or this
 * device itself ([LocalBrain]). Both answer the same requests, so the rest of the game doesn't
 * care which one it talks to:
 *
 *  - GET  /health           {"brain": "ready" | "waking" | "asleep"}
 *  - POST /wake             start loading the models
 *  - POST /say              {"situation", "lang"} -> a line about it, spoken (audio)
 *  - POST /speak            {"text", "lang", "speed"} -> that text, spoken (audio)
 *  - POST /look?lang=       a PNG drawing -> a line about it, spoken (audio)
 *  - POST /listen?lang=&context=   a WAV of the child -> a spoken answer (audio)
 *  - POST /repeat?lang=&target=&alt=   a WAV -> {"heard", "match"} (Say it with Puki)
 *  - POST /story            {"played", "icons", "lang"} -> {"sentences": [{"text", "icon"}]}
 *
 * Every POST also carries ?name=&gender= (the child, from the app's settings).
 */
interface Brain {
    /** The response body (audio or JSON), empty for "nothing to say", or null if it failed. */
    fun request(method: String, path: String, body: ByteArray?, timeoutMs: Int, contentType: String): ByteArray?
}

/** The brain on the family PC, over the home network, with the pairing code. */
class HttpBrain(private val baseUrl: () -> String, private val code: () -> String) : Brain {
    override fun request(method: String, path: String, body: ByteArray?, timeoutMs: Int, contentType: String): ByteArray? =
        runCatching {
            val conn = URL(baseUrl() + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 1500
            conn.setRequestProperty("X-Puki-Code", code().trim())
            conn.readTimeout = timeoutMs
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body) }
            }
            try {
                when (conn.responseCode) {
                    200 -> conn.inputStream.use { it.readBytes() }
                    204 -> ByteArray(0)
                    else -> null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
}
