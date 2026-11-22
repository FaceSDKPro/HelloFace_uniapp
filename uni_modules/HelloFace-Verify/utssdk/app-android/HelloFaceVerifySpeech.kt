package uts.sdk.modules.HelloFaceVerify

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** One camera screen owns one offline speech engine. All calls run on the main thread. */
internal class HelloFaceVerifySpeech(context: Context, active: Boolean) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var active = active
    private var prompt: Pair<String, Int>? = null
    private var utteranceID = 0L

    init {
        val main = Handler(Looper.getMainLooper())
        // A failed initialization can report back before the constructor has assigned the engine.
        engine = runCatching {
            TextToSpeech(context.applicationContext) { status -> main.post { initialized(status) } }
        }.getOrNull()
    }

    fun update(text: String?, actionStep: Int) {
        if (text == null || engine == null) return
        val next = text to actionStep
        if (next == prompt) return
        prompt = next
        speakLatest()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) speakLatest() else if (ready) runCatching { engine?.stop() }
    }

    fun close() {
        val current = engine ?: return
        engine = null
        ready = false
        prompt = null
        runCatching { current.stop() }
        runCatching { current.shutdown() }
    }

    private fun initialized(status: Int) {
        val current = engine ?: return
        val usable = runCatching {
            if (status != TextToSpeech.SUCCESS) return@runCatching false
            val locale = Locale.SIMPLIFIED_CHINESE
            val voice = current.voices.orEmpty().asSequence().filter {
                it.locale.language == locale.language && !it.isNetworkConnectionRequired &&
                    it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }.minByOrNull { if (it.locale.country == locale.country) 0 else 1 } ?: return@runCatching false
            if (current.setVoice(voice) != TextToSpeech.SUCCESS) return@runCatching false
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            true
        }.getOrDefault(false)
        if (!usable) { close(); return }
        ready = true
        speakLatest()
    }

    private fun speakLatest() {
        val current = engine ?: return
        val text = prompt?.first ?: return
        if (!active || !ready) return
        runCatching { current.speak(text, TextToSpeech.QUEUE_FLUSH, null, (++utteranceID).toString()) }
    }
}
