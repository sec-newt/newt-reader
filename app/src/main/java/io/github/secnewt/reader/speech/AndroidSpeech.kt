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

    override fun start(onReady: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            main.post {
                val t = tts
                val ok = status == TextToSpeech.SUCCESS && t != null
                if (ok) {
                    t.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    t.setOnUtteranceProgressListener(Progress())
                }
                onReady(ok)
            }
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
