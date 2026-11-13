package com.ewbible.app.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    fontSize: Float,
    textColor: Color,
    secondaryText: Color,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                webViewClient = WebViewClient()
                loadDataWithBaseURL(
                    null,
                    if (commentaryHtml.isBlank()) "<p style='color:#888888'>No commentary available for this chapter.</p>" else commentaryHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            val html = if (commentaryHtml.isBlank()) {
                "<p style='color:#888888'>No commentary available for this chapter.</p>"
            } else commentaryHtml
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
    )
}

