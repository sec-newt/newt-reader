package io.github.secnewt.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.secnewt.reader.R
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.SectionHeading
import io.github.secnewt.ui.neonOutline
import kotlin.math.roundToInt

/** The launcher screen: whether Reader is on, how to turn it on, and its two settings. */
@Composable
fun SettingsScreen(
    serviceOn: Boolean,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    floatingButton: Boolean,
    onFloatingButton: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (serviceOn) "Reader is on. Swipe up with two fingers, or tap the Read screen tile in Quick Settings, to read any screen."
                else "Reader is off. Turn it on in Accessibility settings, then choose it for the two-finger swipe up.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = onOpenAccessibilitySettings, border = neonOutline(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Open accessibility settings", style = MaterialTheme.typography.titleMedium)
            }
            if (!serviceOn) {
                Text(
                    "If Android says the setting is restricted: open App info for Newt Reader, tap ⋮, then Allow restricted settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionHeading("Text size")
            Text("How big Reader draws text. Your phone's own text size doesn't change.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onTextScale(ReaderPrefs.stepped(textScale, up = false)) },
                    border = neonOutline(),
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Smaller text" },
                ) { Text("A−", style = MaterialTheme.typography.titleMedium) }
                Text(
                    "${(textScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = NewtFonts.Mono),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onTextScale(ReaderPrefs.stepped(textScale, up = true)) },
                    border = neonOutline(),
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Bigger text" },
                ) { Text("A+", style = MaterialTheme.typography.titleMedium) }
            }
            Text(
                "Sample: the quick brown fox reads the news.",
                style = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * textScale) },
            )

            SectionHeading("Floating button")
            // The whole row is the switch, so its sentence is what a screen reader says and the tap target is big.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = floatingButton, role = Role.Switch, onValueChange = onFloatingButton),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Shows a small Newt button on the edge of the screen. Tap it to read; drag it out of the way.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = floatingButton, onCheckedChange = null)
            }
        }
    }
}
