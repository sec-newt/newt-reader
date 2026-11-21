package io.github.secnewt.reader.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * [SpeechPort] on Android's text-to-speech: the engine, voice and speed the owner picked in the
 * phone's settings. Plays as speech on the media volume. Callbacks are moved to the main thread.
 */
class AndroidSpeech(private val context: Context) : SpeechPort {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    override var listener: SpeechPort.Listener? = null

    override val maxInput: Int get() = TextToSpeech.getMaxSpeechInputLength()

    override fun start(onReady: (SpeechPort.Ready) -> Unit) {
        tts = TextToSpeech(context) { status ->
            main.post {
                val t = tts
                if (status != TextToSpeech.SUCCESS || t == null) return@post onReady(SpeechPort.Ready.NO_ENGINE)
                // Page text must never leave the phone: refuse a voice that synthesizes online,
                // switching to an offline voice in the same language when the engine has one.
                if (!offlineVoice(t)) return@post onReady(SpeechPort.Ready.ONLINE_ONLY)
                t.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                t.setOnUtteranceProgressListener(Progress())
                onReady(SpeechPort.Ready.YES)
            }
        }
    }

    /** True if [t] now speaks with a voice that needs no internet. */
    private fun offlineVoice(t: TextToSpeech): Boolean {
        val current = t.voice ?: t.defaultVoice ?: return true  // engine doesn't report voices: nothing to check
        if (!current.isNetworkConnectionRequired) return true
        val offline = t.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == current.locale.language }
            .minByOrNull { if (it.locale == current.locale) 0 else 1 }
            ?: return false
        return t.setVoice(offline) == TextToSpeech.SUCCESS
    }

    override fun speak(id: String, text: String, flush: Boolean) {
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (tts?.speak(text, mode, null, id) != TextToSpeech.SUCCESS) main.post { listener?.onError(id) }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        listener = null
    }

    private inner class Progress : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit
        override fun onDone(utteranceId: String) { main.post { listener?.onDone(utteranceId) } }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) { main.post { listener?.onError(utteranceId) } }
        override fun onError(utteranceId: String, errorCode: Int) { main.post { listener?.onError(utteranceId) } }
    }
}
