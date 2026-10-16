package com.olamkatan.brain.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer
import java.text.Normalizer

/**
 * Hebrew letters to IPA with RenikudPlus (notmax123/RenikudPlus, ONNX), ported from its
 * Python wrapper (renikud_onnx 0.3.0, G2P.phonemize). The model predicts a consonant, a vowel
 * and stress for every letter; the rules here turn those into a phoneme string. Its vocabularies
 * and letter rules ship inside the model file's metadata.
 */
class Renikud(private val env: OrtEnvironment, model: File, threads: Int = 2) {
    private val session: OrtSession = env.createSession(model.path, OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    })
    private val vocab: Map<String, Int>
    private val consonants: Map<Int, String>
    private val vowels: Map<Int, String>
    private val clsId: Int
    private val sepId: Int
    private val letterConstraints: Map<String, List<Int>>
    private val gereshMap: Map<String, String>
    private val gendered = session.inputNames.containsAll(listOf("speaker", "target_speaker"))

    init {
        val meta = session.metadata.customMetadata
        vocab = jsonMap(meta.getValue("vocab")) { it as Int }
        consonants = jsonMap(meta.getValue("consonant_vocab")) { it as String }.mapKeys { it.key.toInt() }
        vowels = jsonMap(meta.getValue("vowel_vocab")) { it as String }.mapKeys { it.key.toInt() }
        clsId = meta.getValue("cls_token_id").toInt()
        sepId = meta.getValue("sep_token_id").toInt()
        letterConstraints = jsonMap(meta.getValue("letter_consonant_constraints")) { v ->
            val a = v as org.json.JSONArray
            List(a.length()) { a.getInt(it) }
        }
        gereshMap = jsonMap(meta["geresh_map"] ?: "{}") { it as String }
    }

    fun phonemize(text: String): String {
        val normalized = Normalizer.normalize(normalizeGraphemes(text), Normalizer.Form.NFD)
        // _tokenize: one token per character, between [CLS] and [SEP]
        val unk = vocab["[UNK]"] ?: 0
        val n = normalized.length + 2
        val ids = LongArray(n)
        ids[0] = clsId.toLong()
        for (i in normalized.indices) ids[i + 1] = (vocab[normalized[i].toString()] ?: unk).toLong()
        ids[n - 1] = sepId.toLong()
        // token t (1..len) is character t-1; [CLS]/[SEP] cover nothing

        val inputs = mutableMapOf(
            "input_ids" to OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, n.toLong())),
            "attention_mask" to OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { 1 }), longArrayOf(1, n.toLong())),
        )
        if (gendered) {
            inputs["speaker"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1))
            inputs["target_speaker"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1))
        }
        val consonantLogits: Array<FloatArray>
        val vowelLogits: Array<FloatArray>
        val stressLogits: Array<FloatArray>
        try {
            session.run(inputs, setOf("consonant_logits", "vowel_logits", "stress_logits")).use { r ->
                @Suppress("UNCHECKED_CAST")
                fun get(name: String) = (r.get(name).get().value as Array<Array<FloatArray>>)[0]
                consonantLogits = get("consonant_logits")
                vowelLogits = get("vowel_logits")
                stressLogits = get("stress_logits")
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
        val vowelPred = IntArray(n) { argmax(vowelLogits[it]) }
        val stressed = bestStressPerWord(normalized, stressLogits, vowelPred)

        val out = StringBuilder()
        for (t in 1..normalized.length) {
            val start = t - 1
            val end = t
            val ch = normalized[start]
            if (ch !in 'א'..'ת') {
                if (ch != '\'' && ch != '"') out.append(ch)
                continue
            }
            var consonantId = argmax(consonantLogits[t])
            val allowed = letterConstraints[ch.toString()]
            if (allowed != null && consonantId !in allowed) consonantId = allowed.maxBy { consonantLogits[t][it] }
            var consonant = consonants[consonantId] ?: "∅"
            if (ch.toString() in gereshMap && end < normalized.length && normalized[end] == '\'') {
                consonant = gereshMap.getValue(ch.toString())
            }
            val vowel = vowels[vowelPred[t]] ?: "∅"
            val stress = t in stressed
            val wordFinal = end >= normalized.length || !normalized[end].isLetter()
            if (ch == 'ח' && wordFinal && vowel == "a") {
                out.append(if (stress) "ˈaχ" else "aχ")
                continue
            }
            if (consonant != "∅") out.append(consonant)
            if (stress && vowel != "∅") out.append('ˈ')
            if (vowel != "∅") out.append(vowel)
        }
        return out.toString()
    }

    /** In each word, the vowel-bearing letter the model is surest carries the stress. */
    private fun bestStressPerWord(normalized: String, stressLogits: Array<FloatArray>, vowelPred: IntArray): Set<Int> {
        val stressed = mutableSetOf<Int>()
        for (word in Regex("\\S+").findAll(normalized)) {
            var best = -1
            for (t in word.range.first + 1..word.range.last + 1) {  // tokens of this word's characters
                if ((vowels[vowelPred[t]] ?: "∅") == "∅") continue
                if (best < 0 || stressLogits[t][1] > stressLogits[best][1]) best = t
            }
            if (best >= 0) stressed += best
        }
        return stressed
    }

    fun close() = session.close()

    private companion object {
        fun normalizeGraphemes(text: String): String =
            Regex("[׳'`´]").replace(text, "'").let { Regex("[״\"]").replace(it, "\"") }

        fun argmax(a: FloatArray): Int {
            var best = 0
            for (i in 1 until a.size) if (a[i] > a[best]) best = i
            return best
        }

        fun <T> jsonMap(json: String, convert: (Any) -> T): Map<String, T> {
            val o = JSONObject(json)
            return o.keys().asSequence().associateWith { convert(o.get(it)) }
        }
    }
}
