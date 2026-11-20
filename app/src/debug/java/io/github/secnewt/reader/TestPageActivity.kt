package io.github.secnewt.reader

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback

/** Debug build only: a two-page local web page for the emulator test. No JavaScript, no internet. */
class TestPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this).apply {
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/page1.html")
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else finish() }
    }
}
