package com.olamkatan.audio

import android.content.Context
import android.os.Build
import com.olamkatan.data.models.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class CompanionStatus { OFF, WAKING, READY }

/**
 * פּוּקִי the parrot, whose words come from the "brain" server on the family PC
 * (brain/ in this repo): Gemma writes a short line about what just happened and
 * BlueTTS voices it. Only reachable on the home network; whenever it isn't
 * (PC off, away from home, slow reply) callers fall back to the recorded lines.
 */
class Companion(
    private val context: Context,
    private val voicePlayer: VoicePlayer,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow(CompanionStatus.OFF)
    val status: StateFlow<CompanionStatus> = _status

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    var enabled = true
        set(value) {
            field = value
            if (!value) _status.value = CompanionStatus.OFF
        }
    var address = ""
    var language = Language.HEBREW

    /** What's on screen right now, so the parrot has something to talk about when tapped. */
    var scene = ""

    val isReady get() = _status.value == CompanionStatus.READY

    private var wakeJob: Job? = null

    /** Checks for the server and wakes the brain if needed. Cheap to call on every game screen. */
    fun wake() {
        if (!enabled || wakeJob?.isActive == true || isReady) return
        if (address.isBlank() && !isEmulator()) return  // not set up yet
        wakeJob = scope.launch(Dispatchers.IO) {
            val state = health() ?: run { _status.value = CompanionStatus.OFF; return@launch }
            if (state == "ready") {
                _status.value = CompanionStatus.READY
                return@launch
            }
            request("POST", "/wake", timeoutMs = 3000)
            _status.value = CompanionStatus.WAKING
            repeat(60) {
                delay(2000)
                when (health()) {
                    "ready" -> { _status.value = CompanionStatus.READY; return@launch }
                    null -> { _status.value = CompanionStatus.OFF; return@launch }
                }
            }
            _status.value = CompanionStatus.OFF
        }
    }

    /**
     * Has the parrot say something about [situation]; runs [fallback] instead if it's
     * not available or doesn't answer in time.
     */
    fun react(situation: String, fallback: () -> Unit = {}) {
        if (!isReady) {
            fallback()
            return
        }
        scope.launch { if (!say(situation)) fallback() }
    }

    /** The child tapped the parrot. */
    fun chat() = react("לחצו על התוכי כדי שידבר. $scene תגיד משהו חמוד וקצר על זה.")

    /** The child held the parrot and talked; [wav] is the recording. Whisper on the PC listens. */
    fun listen(wav: ByteArray) {
        if (!isReady || wav.size < 16000) return  // under half a second: nothing to send
        scope.launch {
            val lang = if (language == Language.ENGLISH) "en" else "he"
            val audio = withContext(Dispatchers.IO) {
                request("POST", "/listen?lang=$lang", body = wav, contentType = "audio/wav", timeoutMs = 15000)
            }
            play(audio)
        }
    }

    private suspend fun say(situation: String): Boolean {
        val audio = withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("situation", situation)
                .put("lang", if (language == Language.ENGLISH) "en" else "he")
                .toString()
            request("POST", "/say", body.toByteArray(Charsets.UTF_8), timeoutMs = 6000)
        }
        return play(audio)
    }

    private fun play(audio: ByteArray?): Boolean {
        if (audio == null) {
            // The brain may have gone to sleep after being idle; wake it for next time
            _status.value = CompanionStatus.OFF
            wake()
            return false
        }
        if (audio.isEmpty()) return false  // no safe line this time
        val file = File(context.cacheDir, "companion.ogg").apply { writeBytes(audio) }
        _speaking.value = true
        voicePlayer.playFile(file)
        // Done talking when the line ends or another voice line cuts in
        scope.launch {
            delay(200)
            while (voicePlayer.isPlaying()) delay(120)
            _speaking.value = false
        }
        return true
    }

    private fun baseUrl(): String {
        // The Android emulator reaches the PC it runs on at 10.0.2.2
        val host = if (isEmulator()) "10.0.2.2:8790" else address.trim()
        return "http://$host"
    }

    private fun isEmulator() = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")

    private fun health(): String? {
        val bytes = request("GET", "/health", timeoutMs = 1500) ?: return null
        return runCatching { JSONObject(String(bytes)).getString("brain") }.getOrNull()
    }

    /** Returns the response body (empty for 204), or null on any failure. */
    private fun request(
        method: String,
        path: String,
        body: ByteArray? = null,
        timeoutMs: Int,
        contentType: String = "application/json; charset=utf-8"
    ): ByteArray? =
        runCatching {
            val conn = URL(baseUrl() + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 1500
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
