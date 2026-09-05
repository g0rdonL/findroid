package dev.jdtech.jellyfin.presentation.theater

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

const val THEATER_URL = "http://100.99.195.85:8181"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TheaterScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val webView =
        remember {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(THEATER_URL)
            }
        }

    DisposableEffect(Unit) { onDispose { webView.destroy() } }

    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webView },
    )
}