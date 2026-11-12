package io.github.secnewt.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.secnewt.reader.engine.Action
import io.github.secnewt.reader.engine.ActionInfo
import io.github.secnewt.reader.engine.Actions
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.Control
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.neonOutline

/**
 * Body text with generous line spacing: at Reader's default size a line is about 48 dp, so inline links are easy to hit.
 * Long words hyphenate rather than break at a random letter when the text is very large.
 */
@Composable
fun readingStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 2.em, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockView(block: Block, onTap: (NodeRef, TapKind) -> Unit) {
    when (block) {
        is Block.Heading -> {
            val tap = block.target?.let { t -> Modifier.clickable(onClickLabel = "Expand") { onTap(t, TapKind.FOLLOW) }.heightIn(min = 48.dp) } ?: Modifier
            Text(
                if (block.target != null) "${block.text}  ▸" else block.text,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = NewtFonts.Display, hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().then(tap).semantics { heading() },
            )
        }
        is Block.Paragraph -> Text(paragraphText(block, onTap), style = readingStyle())
        is Block.ListItem -> Surface(
            onClick = { onTap(block.target, TapKind.FOLLOW) },
            shape = RoundedCornerShape(12.dp),
            border = neonOutline(),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(block.text, style = readingStyle(), modifier = Modifier.padding(16.dp)) }
        is Block.Button -> ReaderButton(block.control.label, block.control.kind) { onTap(block.control.target, block.control.kind) }
        is Block.ControlRow -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.controls.forEach { c ->
                val action = c.action
                if (action != null) {
                    ActionChip(c, action) { onTap(c.target, c.kind) }
                } else {
                    OutlinedButton(onClick = { onTap(c.target, c.kind) }, border = neonOutline(), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (c.kind == TapKind.CLOSE_FIRST) "${c.label} ↗" else c.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        is Block.Field -> ReaderButton(
            if (block.password) "✎ Type in: ••• (hidden)" else "✎ Type in: ${block.label}",
            TapKind.CLOSE_FIRST,
        ) { onTap(block.target, TapKind.CLOSE_FIRST) }
        is Block.Image -> if (block.target != null) {
            ReaderButton(block.description?.let { "🖼 See picture: $it" } ?: "🖼 See picture", TapKind.CLOSE_FIRST) {
                onTap(block.target, TapKind.CLOSE_FIRST)
            }
        } else {
            Text(
                block.description?.let { "🖼 Image: $it" } ?: "🖼 Image",
                style = readingStyle().copy(fontStyle = FontStyle.Italic),
            )
        }
    }
}

/**
 * A post action as a big icon and its count ("♥ 4.5K"). Screen readers hear the app's own full
 * label ("4473 Likes. Like"). "N replies" and "Reply" keep their words: they're the two easy to mix up.
 */
@Composable
private fun ActionChip(c: Control, action: ActionInfo, onClick: () -> Unit) {
    val icon = when (action.action) {
        Action.READ_REPLIES -> Icons.Outlined.ChatBubbleOutline
        Action.REPLY -> Icons.AutoMirrored.Filled.Reply
        Action.REPOST -> Icons.Filled.Repeat
        Action.LIKE -> if (action.on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        Action.BOOKMARK -> if (action.on) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
        Action.SHARE -> Icons.Filled.Share
        Action.VIEWS -> Icons.Outlined.BarChart
    }
    val words = when (action.action) {
        Action.READ_REPLIES -> c.label
        Action.REPLY -> "Reply"
        else -> action.count?.takeIf { it > 0 }?.let(Actions::compact)
    }
    val leaves = c.kind == TapKind.CLOSE_FIRST
    OutlinedButton(
        onClick = onClick,
        border = neonOutline(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 56.dp).semantics(mergeDescendants = true) {
            contentDescription = if (leaves) "${c.label}, leaves Reader" else c.label
        },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        words?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {}) }
        if (leaves) Text("↗", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics {})
    }
}

/** A full-width 56 dp Newt button; ↗ marks one that leaves Reader. */
@Composable
fun ReaderButton(label: String, kind: TapKind, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, border = neonOutline(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (kind == TapKind.CLOSE_FIRST) {
            Text("↗", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "leaves Reader" })
        }
    }
}

@Composable
private fun paragraphText(p: Block.Paragraph, onTap: (NodeRef, TapKind) -> Unit): AnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.tertiary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        p.spans.forEach { s ->
            when (s) {
                is Span.Plain -> append(s.text)
                is Span.Link -> withLink(LinkAnnotation.Clickable(s.text, linkStyle) { onTap(s.target, TapKind.FOLLOW) }) { append(s.text) }
            }
        }
    }
}
