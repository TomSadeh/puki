package io.github.tomsadeh.puki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.tomsadeh.puki.audio.Companion
import io.github.tomsadeh.puki.audio.Connection
import io.github.tomsadeh.puki.audio.VoicePlayer
import io.github.tomsadeh.puki.brain.Persona
import io.github.tomsadeh.puki.brain.WhisperNative
import io.github.tomsadeh.puki.brain.voice.VoiceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/** Runs the device brain's text handling on Android's own regex engine, and checks the whisper library loads (debug builds only). */
class SelfTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = "PukiSelfTest"
        try {
            val p = Persona()
            fun check(what: String, got: Any?, want: Any?) =
                Log.i(tag, "${if (got == want) "ok  " else "FAIL"} $what: got [$got] want [$want]")
            check("clean he", p.clean("וואו! צהוב כמו השמש!", "he"), "וואו! צהוב כמו השמש!")
            check("compliment dropped", p.clean("את מקסימה! הפרה אוהבת עשב!", "he"), "הפרה אוהבת עשב!")
            check("banned word", p.clean("זו טעות קטנה", "he"), null)
            check("hungry is fine", p.clean("הכלב רעב!", "he"), "הכלב רעב!")
            check("english", p.clean("Yay! Green!", "en"), "Yay! Green!")
            check("word", p.wordMatch("הכלב", "כלב"), "word")
            check("close", p.wordMatch("פה", "פרה"), "close")
            check("tried", p.wordMatch("אריה", "קוף"), "tried")
            check("misread", p.forVoice("הידד! צבע אדום!"), "יש! צבע אדום!")
            check("normalize dash", VoiceText.prepareTextForSynthesis("סבתא, אבא ואמא – כולם כאן!"), "סבתא, אבא ואמא כולם כאן!")
            check("geresh", VoiceText.prepareTextForSynthesis("ג'ירפה גבוהה."), "ג׳ירפה גבוהה.")
            check("chunks", VoiceText.chunkText("ʔˈejze tsijˈuʁ jafˈe! ʃˈemeʃ.").size, 1)
            check("tags", VoiceText.stripLangTags("<he>ʃalˈom</he>"), "ʃalˈom")
            // The whisper library's functions are named after the package: this fails if they don't match
            WhisperNative.load()
            check("whisper binding", WhisperNative.init("/no/such/model.bin"), 0L)
            // With --es host <ip:port> --es code <CODE> --es pin <hex>: the app's real connection check
            // against that PC (right pin, wrong pin, and first use), off the main thread
            val host = intent.getStringExtra("host")
            val code = intent.getStringExtra("code")
            if (host != null && code != null) {
                val pin = intent.getStringExtra("pin") ?: ""
                val app = context.applicationContext
                val companion = Companion(app, VoicePlayer(app), CoroutineScope(Dispatchers.IO))
                val pending = goAsync()
                thread {
                    try {
                        runBlocking {
                            check("https, pinned", companion.checkConnection(host, code, pin), Connection.OK)
                            check("https, other pin", companion.checkConnection(host, code, "00".repeat(32)), Connection.WRONG_PC)
                            var learned = ""
                            companion.onPinLearned = { learned = it }
                            check("https, first use", companion.checkConnection(host, code, ""), Connection.OK)
                            check("first use learns the pin", learned, pin)
                            check("wrong code", companion.checkConnection(host, "WRONGCOD", pin), Connection.WRONG_CODE)
                        }
                    } catch (e: Throwable) {
                        Log.e(tag, "CRASH", e)
                    } finally {
                        Log.i(tag, "done")
                        pending.finish()
                    }
                }
                return
            }
            Log.i(tag, "done")
        } catch (e: Throwable) {
            Log.e(tag, "CRASH", e)
        }
    }
}
