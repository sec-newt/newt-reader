package io.github.secnewt.reader

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.secnewt.reader.reader.ReaderPrefs
import io.github.secnewt.reader.ui.SettingsScreen
import io.github.secnewt.ui.NewtTheme

class MainActivity : ComponentActivity() {
    private var serviceOn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = ReaderPrefs(this)
        setContent {
            NewtTheme {
                var scale by remember { mutableFloatStateOf(prefs.textScale) }
                var bubble by remember { mutableStateOf(prefs.floatingButton) }
                SettingsScreen(
                    serviceOn = serviceOn,
                    textScale = scale,
                    onTextScale = { scale = it; prefs.textScale = it },
                    floatingButton = bubble,
                    onFloatingButton = { bubble = it; prefs.floatingButton = it },
                    onOpenAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
