package io.github.secnewt.reader.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.roundToInt

/** Reader's only saved settings: text size and the floating button. Nothing Reader reads is stored. */
class ReaderPrefs(context: Context) {
    private val sp = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    var textScale: Float
        get() = sp.getFloat(KEY_SCALE, DEFAULT_SCALE)
        set(value) = sp.edit { putFloat(KEY_SCALE, value.coerceIn(MIN_SCALE, MAX_SCALE)) }

    var floatingButton: Boolean
        get() = sp.getBoolean(KEY_BUBBLE, false)
        set(value) = sp.edit { putBoolean(KEY_BUBBLE, value) }

    fun watch(onChange: () -> Unit) {
        unwatch()
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
            .also(sp::registerOnSharedPreferenceChangeListener)
    }

    fun unwatch() {
        listener?.let(sp::unregisterOnSharedPreferenceChangeListener)
        listener = null
    }

    companion object {
        const val DEFAULT_SCALE = 1.5f
        const val MIN_SCALE = 1.0f
        const val MAX_SCALE = 3.0f
        private const val KEY_SCALE = "text_scale"
        private const val KEY_BUBBLE = "floating_button"

        /** One A−/A+ press: the nearest tenth, one step up or down, kept in range. */
        fun stepped(current: Float, up: Boolean): Float {
            val tenths = (current * 10).roundToInt() + if (up) 1 else -1
            return (tenths / 10f).coerceIn(MIN_SCALE, MAX_SCALE)
        }
    }
}
