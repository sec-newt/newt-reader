package io.github.secnewt.reader.engine

/** What tapping something in Reader does: stay open and re-read, or close Reader and then tap. */
enum class TapKind { FOLLOW, CLOSE_FIRST }

/** A run of text inside a paragraph; links are tappable. */
sealed interface Span {
    val text: String
    data class Plain(override val text: String) : Span
    data class Link(override val text: String, val target: NodeRef) : Span
}

/** One tappable control: what it says, where it is, and what tapping it does. */
data class Control(val label: String, val target: NodeRef, val kind: TapKind)

/** One piece of the reading view, in reading order. */
sealed interface Block {
    /** True if it was on the app's screen when read; Reader opens at the first such block. */
    val onScreen: Boolean
    val text: String
    /** Same type and text = the same block; used to skip repeats when loading more. */
    val key: String get() = "${this::class.simpleName}:$text"

    /** [target] is set when the heading itself is tappable (mobile Wikipedia's section toggles); tapping it FOLLOWs. */
    data class Heading(override val text: String, override val onScreen: Boolean, val target: NodeRef? = null) : Block

    data class Paragraph(val spans: List<Span>, override val onScreen: Boolean) : Block {
        override val text: String get() = spans.joinToString("") { it.text }
    }

    /** A tappable row with real content (a post, a headline, a forecast day). Always FOLLOW. */
    data class ListItem(override val text: String, val target: NodeRef, override val onScreen: Boolean) : Block

    data class Button(val control: Control, override val onScreen: Boolean) : Block {
        override val text: String get() = control.label
    }

    /** Three or more small buttons in a row (toolbars, like/reply/share), shown as chips. */
    data class ControlRow(val controls: List<Control>, override val onScreen: Boolean) : Block {
        override val text: String get() = controls.joinToString(" · ") { it.label }
    }

    /** A text field. Tapping it always closes Reader so the keyboard can appear. */
    data class Field(val label: String, val target: NodeRef, val password: Boolean, override val onScreen: Boolean) : Block {
        override val text: String get() = label
    }

    data class Image(val description: String?, override val onScreen: Boolean) : Block {
        override val text: String get() = description.orEmpty()
    }
}
