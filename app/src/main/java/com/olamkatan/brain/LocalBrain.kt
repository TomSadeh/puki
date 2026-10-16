package com.olamkatan.brain

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import com.olamkatan.brain.voice.BlueVoice
import com.olamkatan.brain.voice.Renikud
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Puki's brain on this device: answers the same requests as the PC brain (see [Brain]) with
 * Gemma ([Mind]), the BlueTTS voice ([BlueVoice]) and the same personality ([Persona]).
 * It hears with ivrit.ai's Whisper ([Ears], loaded the first time she talks, to save memory
 * until then). For now it speaks Hebrew only: English lines fall back to the recorded ones.
 */
class LocalBrain(private val models: DeviceModels) : Brain {
    private val persona = Persona()
    private var mind: Mind? = null
    private var voice: BlueVoice? = null
    private var ears: Ears? = null
    @Volatile private var state = "asleep"

    /** Loads the models (in the background; the game polls /health until "ready"). */
    @Synchronized
    fun wake() {
        if (state != "asleep" || !models.ready()) return
        state = "waking"
        thread(name = "puki-brain") {
            try {
                val env = OrtEnvironment.getEnvironment()
                val dir = models.dir
                voice = BlueVoice(env, models.file("voice"), models.file("voice/noa.json"), Renikud(env, models.file("voice/renikud_int8.onnx")))
                voice!!.speak("שלום")  // warms up the voice before the first real line
                models.cacheDir.mkdirs()
                mind = Mind(java.io.File(dir, "gemma.litertlm"), models.cacheDir).also { it.load() }
                state = "ready"
            } catch (e: Throwable) {
                Log.e(TAG, "couldn't load the device brain", e)
                close()
            }
        }
    }

    @Synchronized
    fun close() {
        runCatching { mind?.close() }
        runCatching { voice?.close() }
        runCatching { ears?.close() }
        mind = null
        voice = null
        ears = null
        state = "asleep"
    }

    override fun request(method: String, path: String, body: ByteArray?, timeoutMs: Int, contentType: String): ByteArray? {
        val route = path.substringBefore('?')
        val query = parseQuery(path.substringAfter('?', ""))
        if (method == "GET" && route == "/health") return JSONObject().put("brain", state).toString().toByteArray()
        if (route == "/wake") { wake(); return ByteArray(0) }
        val mind = mind ?: return null
        val voice = voice ?: return null
        return try {
            synchronized(this) {
                persona.setChild(query["name"]?.firstOrNull() ?: "", query["gender"]?.firstOrNull() ?: "")
                when (route) {
                    "/say" -> {
                        val req = JSONObject(String(body ?: ByteArray(0)))
                        val lang = if (req.optString("lang") == "en") "en" else "he"
                        reply(line(mind, req.optString("situation").take(300), lang), lang, voice)
                    }
                    "/speak" -> {
                        val req = JSONObject(String(body ?: ByteArray(0)))
                        val lang = if (req.optString("lang") == "en") "en" else "he"
                        val text = req.optString("text").take(200).trim()
                        if (text.isEmpty()) ByteArray(0) else reply(text, lang, voice, req.optDouble("speed", 0.92).toFloat())
                    }
                    "/look" -> {
                        val lang = if (query["lang"]?.firstOrNull() == "en") "en" else "he"
                        var text: String? = null
                        repeat(3) {
                            if (text == null) text = persona.clean(mind.look(persona.systemPrompt(lang), persona.lookPrompt(lang), body ?: ByteArray(0)), lang)
                        }
                        reply(text, lang, voice)
                    }
                    "/story" -> story(mind, JSONObject(String(body ?: ByteArray(0))))
                    "/repeat" -> {
                        // Say it with Puki: did she say ?target= (or a related ?alt=)? Just listening, no Gemma
                        val lang = if (query["lang"]?.firstOrNull() == "en") "en" else "he"
                        val target = query["target"]?.firstOrNull()?.take(40) ?: ""
                        val alts = (query["alt"] ?: emptyList()).filter { it.isNotEmpty() }.map { it.take(40) }.take(4)
                        val heard = ears().transcribe(body ?: ByteArray(0), lang, (listOf(target) + alts).joinToString(" "))
                        val match = persona.wordMatch(heard, target, alts)
                        Log.i(TAG, "say '$target': heard '$heard' -> $match")
                        JSONObject().put("heard", heard).put("match", match).toString().toByteArray()
                    }
                    "/listen" -> {
                        // She talked to Puki (optionally answering ?context=, what he just asked)
                        val lang = if (query["lang"]?.firstOrNull() == "en") "en" else "he"
                        val heard = ears().transcribe(body ?: ByteArray(0), lang, if (lang == "he") persona.hotwords() else "")
                        var situation = persona.listenSituation(heard, lang)
                        val context = query["context"]?.firstOrNull()?.take(200) ?: ""
                        if (context.isNotEmpty()) situation = "$context $situation"
                        Log.i(TAG, "heard '$heard'")
                        reply(line(mind, situation, lang), lang, voice)
                    }
                    else -> null
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "$route failed", e)
            null
        }
    }

    private fun ears(): Ears = ears ?: Ears(models.whisperModel()).also { ears = it }

    /** A cleaned line from Gemma about [situation], or null (three tries, like the PC). */
    private fun line(mind: Mind, situation: String, lang: String): String? {
        repeat(3) {
            val raw = mind.chat(persona.systemPrompt(lang), persona.examples(lang), persona.situationPrompt(situation, lang))
            persona.clean(raw, lang)?.let { persona.said(it); return it }
            Log.i(TAG, "rejected: $raw")
        }
        return null
    }

    /** [text] spoken (empty when there's nothing to say; null when it can't be voiced here). */
    private fun reply(text: String?, lang: String, voice: BlueVoice, speed: Float = 0.92f): ByteArray? {
        if (text.isNullOrBlank()) return ByteArray(0)
        if (lang == "en") return ByteArray(0)  // no English voice here yet: the game uses its recorded line
        return voice.speak(persona.forVoice(text), speed)
    }

    private fun story(mind: Mind, req: JSONObject): ByteArray? {
        val lang = if (req.optString("lang") == "en") "en" else "he"
        val iconsJson = req.optJSONObject("icons") ?: return null
        val icons = iconsJson.keys().asSequence().associateWith { iconsJson.getString(it) }
        if (icons.isEmpty()) return null
        val played = req.optString("played").take(300).ifEmpty { if (lang == "he") "משחקים" else "games" }
        val schema = JSONObject().put("type", "object").put("required", JSONArray().put("sentences")).put("properties", JSONObject()
            .put("sentences", JSONObject().put("type", "array").put("minItems", 4).put("maxItems", 7).put("items", JSONObject()
                .put("type", "object").put("required", JSONArray().put("text").put("icon")).put("properties", JSONObject()
                    .put("text", JSONObject().put("type", "string"))
                    .put("icon", JSONObject().put("type", "string").put("enum", JSONArray(icons.keys.toList()))))))).toString()
        repeat(2) {
            val raw = mind.json(persona.systemPrompt(lang), persona.storyPrompt(played, icons, lang), schema)
            val items = runCatching { JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)).getJSONArray("sentences") }.getOrNull()
            if (items != null) {
                val out = JSONArray()
                for (i in 0 until items.length()) {
                    val it = items.getJSONObject(i)
                    val text = persona.clean(it.optString("text"), lang)
                    val icon = it.optString("icon")
                    if (text != null && icon in icons) out.put(JSONObject().put("text", text).put("icon", icon))
                }
                if (out.length() >= 3) {
                    val last = out.getJSONObject(out.length() - 1).getString("icon")
                    out.put(JSONObject().put("text", persona.storyClosing(lang)).put("icon", if ("ic_bedtime_moon" in icons) "ic_bedtime_moon" else last))
                    return JSONObject().put("sentences", out).toString().toByteArray()
                }
            }
            Log.i(TAG, "story rejected: ${raw.take(160)}")
        }
        return ByteArray(0)
    }

    private fun parseQuery(q: String): Map<String, List<String>> =
        q.split('&').filter { '=' in it }.groupBy({ URLDecoder.decode(it.substringBefore('='), "UTF-8") }, { URLDecoder.decode(it.substringAfter('='), "UTF-8") })

    private companion object {
        const val TAG = "PukiBrain"
    }
}
