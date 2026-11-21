package io.github.secnewt.reader.speech

/** A text-to-speech engine. Calls and callbacks happen on the main thread. */
interface SpeechPort {
    interface Listener {
        fun onDone(id: String)
        fun onError(id: String)
    }

    var listener: Listener?

    /** The longest text one [speak] call accepts. */
    val maxInput: Int

    /** YES: ready with an offline voice. ONLINE_ONLY: only online voices. UNKNOWN_VOICE: the engine won't say which voice it uses. */
    enum class Ready { YES, NO_ENGINE, ONLINE_ONLY, UNKNOWN_VOICE }

    /** Connects to the engine; [onReady] says whether it can speak, using only a voice that stays on the phone. */
    fun start(onReady: (Ready) -> Unit)

    /** Says [text]. [flush] drops anything still queued first; otherwise it's queued after it. */
    fun speak(id: String, text: String, flush: Boolean)

    /** Stops speaking and drops the queue. No callbacks follow for what was dropped. */
    fun stop()

    /** Disconnects from the engine. */
    fun shutdown()
}
