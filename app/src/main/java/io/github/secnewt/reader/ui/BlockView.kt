package io.github.secnewt.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.secnewt.reader.engine.Block
import io.github.secnewt.reader.engine.NodeRef
import io.github.secnewt.reader.engine.Span
import io.github.secnewt.reader.engine.TapKind
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.neonOutline

/** Body text with generous line spacing: at Reader's default size a line is about 48 dp, so inline links are easy to hit. */
@Composable
fun readingStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 2.em)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockView(block: Block, onTap: (NodeRef, TapKind) -> Unit) {
    when (block) {
        is Block.Heading -> {
            val tap = block.target?.let { t -> Modifier.clickable(onClickLabel = "Expand") { onTap(t, TapKind.FOLLOW) }.heightIn(min = 48.dp) } ?: Modifier
            Text(
                if (block.target != null) "${block.text}  ▸" else block.text,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = NewtFonts.Display),
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
                OutlinedButton(onClick = { onTap(c.target, c.kind) }, border = neonOutline(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (c.kind == TapKind.CLOSE_FIRST) "${c.label} ↗" else c.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        is Block.Field -> ReaderButton(
            if (block.password) "✎ Type in: ••• (hidden)" else "✎ Type in: ${block.label}",
            TapKind.CLOSE_FIRST,
        ) { onTap(block.target, TapKind.CLOSE_FIRST) }
        is Block.Image -> Text(
            block.description?.let { "🖼 Image: $it" } ?: "🖼 Image",
            style = readingStyle().copy(fontStyle = FontStyle.Italic),
        )
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
