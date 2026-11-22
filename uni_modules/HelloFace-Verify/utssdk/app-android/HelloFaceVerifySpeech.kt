package uts.sdk.modules.HelloFaceVerify

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Owns the current offline camera prompt. All state changes run on the main thread. */
internal class HelloFaceVerifySpeech(context: Context, active: Boolean) {
    companion object {
        private const val FINISH_LIMIT_MS = 2_000L
        private var finishingPrompt: HelloFaceVerifySpeech? = null

        fun stopFinishingPrompt() { finishingPrompt?.close() }
    }

    private val main = Handler(Looper.getMainLooper())
    private val finishTimeout = Runnable { close() }
    private var engine: TextToSpeech? = null
    private var ready = false
    private var active = active
    private var prompt: Pair<String, Int>? = null
    private var nextUtteranceID = 0L
    private var utteranceID: String? = null
    private var started = false

    init {
        // A failed initialization can report back before the constructor has assigned the engine.
        engine = runCatching {
            TextToSpeech(context.applicationContext) { status -> main.post { initialized(status) } }
        }.getOrNull()
    }

    fun update(text: String?, actionStep: Int) {
        if (text == null || engine == null || finishingPrompt === this) return
        val next = text to actionStep
        if (next == prompt) return
        prompt = next
        speakLatest()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) speakLatest() else if (ready) {
            utteranceID = null
            started = false
            runCatching { engine?.stop() }
        }
    }

    /** Let the already playing prompt finish without holding the camera or result callback. */
    fun finishCurrentPrompt() {
        if (finishingPrompt === this) return
        val speaking = active && started && runCatching { engine?.isSpeaking == true }.getOrDefault(false)
        if (!speaking) { close(); return }
        stopFinishingPrompt()
        finishingPrompt = this
        main.postDelayed(finishTimeout, FINISH_LIMIT_MS)
    }

    fun close() {
        if (finishingPrompt === this) {
            finishingPrompt = null
            main.removeCallbacks(finishTimeout)
        }
        val current = engine ?: return
        engine = null
        ready = false
        prompt = null
        utteranceID = null
        started = false
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
            current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) { main.post { if (id == utteranceID) started = true } }
                override fun onDone(id: String) { utteranceEnded(id) }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(id: String) { utteranceEnded(id) }
                override fun onStop(id: String, interrupted: Boolean) { utteranceEnded(id) }
            }) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!usable) { close(); return }
        ready = true
        speakLatest()
    }

    private fun speakLatest() {
        val current = engine ?: return
        val text = prompt?.first ?: return
        if (!active || !ready) return
        val id = (++nextUtteranceID).toString()
        utteranceID = id
        started = false
        if (runCatching { current.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }.getOrNull() != TextToSpeech.SUCCESS) {
            utteranceID = null
        }
    }

    private fun utteranceEnded(id: String) {
        main.post {
            if (id != utteranceID) return@post
            utteranceID = null
            started = false
            if (finishingPrompt === this) close()
        }
    }
}
