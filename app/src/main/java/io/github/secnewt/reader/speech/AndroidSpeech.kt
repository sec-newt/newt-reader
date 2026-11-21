package io.github.secnewt.reader.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

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
                offlineVoice(t)?.let { return@post onReady(it) }
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

    /**
     * Makes [t] speak with a voice that needs no internet. Null when it does; otherwise why it can't.
     * Fails closed: if the engine won't say which voice it uses, Reader can't promise the text stays on the phone.
     */
    private fun offlineVoice(t: TextToSpeech): SpeechPort.Ready? {
        val active = t.voice
        if (active != null && !active.isNetworkConnectionRequired) return null
        // No active voice reported: the default isn't guaranteed to be in use, so select it explicitly.
        val current = active ?: t.defaultVoice
        if (active == null && current != null && !current.isNetworkConnectionRequired && t.setVoice(current) == TextToSpeech.SUCCESS) return null
        val locale = current?.locale ?: Locale.getDefault()
        // Offline voices in the same language, exact locale first; the first one the engine accepts wins.
        val switched = t.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == locale.language }
            .sortedBy { if (it.locale == locale) 0 else 1 }
            .any { t.setVoice(it) == TextToSpeech.SUCCESS }
        return when {
            switched -> null
            current == null -> SpeechPort.Ready.UNKNOWN_VOICE
            else -> SpeechPort.Ready.ONLINE_ONLY
        }
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
