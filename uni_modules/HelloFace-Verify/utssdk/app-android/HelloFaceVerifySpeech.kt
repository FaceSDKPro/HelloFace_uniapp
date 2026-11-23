package uts.sdk.modules.HelloFaceVerify

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/** SDK Demo voice policy: accept ISO-2/ISO-3 locales and prefer the matching region. */
internal object HelloFaceVerifyVoices {
    fun offline(voices: Set<Voice>, locale: Locale): Voice? {
        val languages = setOf(locale.language, locale.getISO3Language())
        val countries = setOf(locale.country, locale.getISO3Country())
        return voices.asSequence().filter {
            it.locale.language in languages && !it.isNetworkConnectionRequired &&
                it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }.minByOrNull {
            when {
                it.locale.country in countries -> 0
                it.locale.country.isEmpty() -> 1
                else -> 2
            }
        }
    }
}

/** Owns the current offline camera prompt; only the playback marker crosses TTS threads. */
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
    @Volatile private var startedID: String? = null

    init {
        stopFinishingPrompt()
        // A failed initialization can report back before the constructor has assigned the engine.
        engine = runCatching {
            TextToSpeech(context.applicationContext) { status -> main.post { initialized(status) } }
        }.getOrNull()
    }

    fun update(text: String?, actionStep: Int) {
        if (text.isNullOrEmpty() || engine == null || finishingPrompt === this) return
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
            startedID = null
            runCatching { engine?.stop() }
        }
    }

    /** Let the already playing prompt finish without holding the camera or result callback. */
    fun finishCurrentPrompt() {
        if (finishingPrompt === this) return
        val speaking = active && utteranceID != null && utteranceID == startedID &&
            runCatching { engine?.isSpeaking == true }.getOrDefault(false)
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
        startedID = null
        runCatching { current.stop() }
        runCatching { current.shutdown() }
    }

    private fun initialized(status: Int) {
        val current = engine ?: return
        val usable = runCatching {
            if (status != TextToSpeech.SUCCESS) return@runCatching false
            val voice = HelloFaceVerifyVoices.offline(current.voices.orEmpty(), Locale.SIMPLIFIED_CHINESE)
                ?: return@runCatching false
            if (current.setVoice(voice) != TextToSpeech.SUCCESS) return@runCatching false
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) { startedID = id }
                override fun onDone(id: String) { utteranceEnded(id) }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(id: String) { utteranceEnded(id) }
                override fun onStop(id: String, interrupted: Boolean) { utteranceEnded(id) }
            }) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!usable) {
            Log.w("HelloFaceVerify", "TTS initialization failed or no installed offline Chinese voice")
            close(); return
        }
        ready = true
        speakLatest()
    }

    private fun speakLatest() {
        val current = engine ?: return
        val text = prompt?.first ?: return
        if (!active || !ready) return
        val id = (++nextUtteranceID).toString()
        utteranceID = id
        startedID = null
        if (runCatching { current.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }.getOrNull() != TextToSpeech.SUCCESS) {
            prompt = null
            utteranceID = null
            startedID = null
            runCatching { current.stop() }
            Log.w("HelloFaceVerify", "TTS prompt could not be queued")
        }
    }

    private fun utteranceEnded(id: String) {
        main.post {
            if (id != utteranceID) return@post
            utteranceID = null
            startedID = null
            if (finishingPrompt === this) close()
        }
    }
}
