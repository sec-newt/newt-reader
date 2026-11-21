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

    /** Connects to the engine; [onReady] gets true once it can speak, false if there's none. */
    fun start(onReady: (Boolean) -> Unit)

    /** Says [text]. [flush] drops anything still queued first; otherwise it's queued after it. */
    fun speak(id: String, text: String, flush: Boolean)

    /** Stops speaking and drops the queue. No callbacks follow for what was dropped. */
    fun stop()

    /** Disconnects from the engine. */
    fun shutdown()
}
