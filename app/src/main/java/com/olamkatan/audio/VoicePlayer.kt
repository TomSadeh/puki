package com.olamkatan.audio

import android.content.Context
import android.media.MediaPlayer
import com.olamkatan.data.models.Gender
import com.olamkatan.data.models.Language
import kotlin.random.Random

class VoicePlayer(private val context: Context) {

    /** Lines the parrot recorded onto the tablet (e.g. with the child's name). */
    private val personalDir = java.io.File(context.filesDir, "voice")

    private var mediaPlayer: MediaPlayer? = null
    var enabled = true

    var gender: Gender = Gender.GIRL

    /** The parent's volume for voices, 0..1: the recorded lines and Puki (parent settings). */
    var level = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            runCatching { mediaPlayer?.setVolume(field, field) }
        }

    fun play(voiceKey: String, language: Language, onComplete: (() -> Unit)? = null) {
        if (!enabled) {
            onComplete?.invoke()
            return
        }
        stop()
        personalFile(voiceKey, language)?.let {
            playFile(it, onComplete)
            return
        }
        val resId = resolve(voiceKey, language)
        if (resId == 0) {
            onComplete?.invoke()
            return
        }
        mediaPlayer = MediaPlayer.create(context, resId)?.apply {
            setVolume(level, level)
            setOnCompletionListener {
                it.release()
                mediaPlayer = null
                onComplete?.invoke()
            }
            start()
        } ?: run {
            onComplete?.invoke()
            null
        }
    }

    /** Plays an audio file (e.g. a companion line from the brain server). */
    fun playFile(file: java.io.File, onComplete: (() -> Unit)? = null) {
        if (!enabled) {
            onComplete?.invoke()
            return
        }
        stop()
        val player = MediaPlayer()
        mediaPlayer = runCatching {
            player.apply {
                setDataSource(file.absolutePath)
                setVolume(level, level)
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    onComplete?.invoke()
                }
                prepare()
                start()
            }
        }.getOrElse {
            player.release()  // a broken or half-written file: don't leak the player
            onComplete?.invoke()
            null
        }
    }

    fun playSequence(voiceKeys: List<String>, language: Language, onAllComplete: (() -> Unit)? = null) {
        if (voiceKeys.isEmpty()) {
            onAllComplete?.invoke()
            return
        }
        play(voiceKeys.first(), language) {
            playSequence(voiceKeys.drop(1), language, onAllComplete)
        }
    }

    /**
     * A random praise line for a right answer. Half the time it's one that says
     * the child's name, when those personal lines were generated (they're optional).
     */
    fun praiseKey(language: Language): String? {
        val named = (1..4).map { "voice_praise_name_$it" }.filter { has(it, language) }
        val plain = (listOf("voice_prompt_great_job", "voice_prompt_well_done") + (1..5).map { "voice_praise_$it" })
            .filter { has(it, language) }
        return if (named.isNotEmpty() && (plain.isEmpty() || Random.nextBoolean())) named.random() else plain.randomOrNull()
    }

    /** Plays a random praise line, see [praiseKey]. */
    fun playPraise(language: Language, onComplete: (() -> Unit)? = null) {
        val key = praiseKey(language)
        if (key == null) onComplete?.invoke() else play(key, language, onComplete)
    }

    /** The personal version of a line (e.g. voice_hello_name) if it exists, otherwise the generic one. */
    fun personalKey(voiceKey: String, language: Language): String =
        "${voiceKey}_name".takeIf { has(it, language) } ?: voiceKey

    private fun has(voiceKey: String, language: Language) =
        personalFile(voiceKey, language) != null || resolve(voiceKey, language) != 0

    private fun personalFile(voiceKey: String, language: Language): java.io.File? {
        val suffix = if (language == Language.HEBREW) "_he" else "_en"
        return java.io.File(personalDir, "$voiceKey$suffix.ogg").takeIf { it.exists() }
    }

    // For Hebrew, try the gendered variant first (e.g. voice_prompt_bath_he_f),
    // then fall back to the base one (voice_prompt_bath_he)
    private fun resolve(voiceKey: String, language: Language): Int {
        val suffix = when (language) {
            Language.HEBREW -> "_he"
            Language.ENGLISH -> "_en"
        }
        val genderSuffix = when (gender) {
            Gender.GIRL -> "_f"
            Gender.BOY -> "_m"
        }
        val gendered = rawId("$voiceKey$suffix$genderSuffix")
        if (gendered != 0) return gendered
        return rawId("$voiceKey$suffix")
    }

    // getIdentifier is slow (a praise pick asks about a dozen names); the answers never change
    private val ids = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private fun rawId(name: String): Int =
        ids.getOrPut(name) { context.resources.getIdentifier(name, "raw", context.packageName) }

    fun isPlaying(): Boolean = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
    }
}
