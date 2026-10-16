package com.olamkatan.brain

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlin.random.Random

/**
 * Gemma 4 E4B on this device, through LiteRT-LM on the GPU (vision too, for her drawings).
 * Each request is a fresh conversation: the instructions, a few example exchanges, then the
 * question, like the PC brain's chat requests. Start-up takes a while the first time (the GPU
 * programs are compiled and cached in [cacheDir]).
 */
class Mind(model: File, cacheDir: File) {
    private val engine = Engine(
        EngineConfig(
            modelPath = model.path,
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
            maxNumImages = 1,
            cacheDir = cacheDir.path,
        )
    )

    fun load() = engine.initialize()

    /** A reply to [user], after the [system] instructions and [examples] (user, reply) pairs. */
    @Synchronized
    fun chat(system: String, examples: List<Pair<String, String>>, user: String, maxTokens: Int = 48, temperature: Double = 0.9): String =
        engine.createConversation(config(system, examples, maxTokens, temperature)).use { c ->
            text(c.sendMessage(user))
        }

    /** A reply about an image (her drawing). */
    @Synchronized
    fun look(system: String, ask: String, png: ByteArray, maxTokens: Int = 60, temperature: Double = 0.8): String =
        engine.createConversation(config(system, emptyList(), maxTokens, temperature)).use { c ->
            text(c.sendMessage(Contents.of(Content.ImageBytes(png), Content.Text(ask))))
        }

    /** A reply held to a JSON schema (the bedtime story). */
    @Synchronized
    fun json(system: String, ask: String, schema: String, maxTokens: Int = 400, temperature: Double = 0.8): String =
        engine.createConversation(config(system, emptyList(), maxTokens, temperature, jsonOut = true)).use { c ->
            text(c.sendMessage(ask, responseFormat = ResponseFormat.json(schema)))
        }

    fun close() = engine.close()

    private fun config(system: String, examples: List<Pair<String, String>>, maxTokens: Int, temperature: Double, jsonOut: Boolean = false) =
        ConversationConfig(
            systemInstruction = Contents.of(system),
            initialMessages = examples.flatMap { (u, a) ->
                listOf(Message.user(u), Message.model(a))
            },
            samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = temperature, seed = Random.nextInt()),
            maxOutputToken = maxTokens,
            thinkingConfig = ThinkingConfig(false),  // as on the PC: answer straight away
            enableResponseFormat = jsonOut,
        )

    private fun text(m: Message) = m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
}
