package com.ewbible.app.ui.screens

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    fontSize: Float,
    textColor: Color,
    secondaryText: Color,
    hasError: Boolean = false,
    bookName: String = "",
    chapter: Int = 1,
) {
    val context = LocalContext.current
    // FIX 1: Use Activity context, not Application context - WebView crashes with Application context
    val activity = context as? Activity ?: return

    var webViewFailed by remember { mutableStateOf(false) }

    // FIX 2: Clean HTML safely - never crash on bad HTML
    val renderedHtml = remember(commentaryHtml) {
        runCatching {
            val t = commentaryHtml.trim()
            if (t.contains("<body", ignoreCase = true)) {
                t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
            } else t
        }.getOrDefault("")
    }

    val textColorCss = "#%06X".format(0xFFFFFF and textColor.toArgb())
    val secondaryColorCss = "#%06X".format(0xFFFFFF and secondaryText.toArgb())

    // KEEP YOUR HTML + CSS - same look as before
    val html = remember(renderedHtml, textColorCss, secondaryColorCss, fontSize) {
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { color: $textColorCss; background: transparent; font-family: sans-serif; font-size: ${fontSize}px; line-height: 1.6; margin: 8px; padding-bottom: 80px; }
            p { margin: 0 0 1em; }
            h1, h2, h3, h4 { color: $textColorCss; line-height: 1.3; }
            a { color: #C9A227; }
            .empty { color: $secondaryColorCss; }
            blockquote { border-left: 3px solid #C9A227; margin-left: 0; padding-left: 12px; color: $secondaryColorCss; }
        </style>
        </head><body>${if (renderedHtml.isBlank()) "<p class=\"empty\">No commentary available for this chapter.</p>" else renderedHtml}</body></html>
        """.trimIndent()
    }

    // FIX 3: Create WebView ONCE and keep it - don't recreate on every recomposition / tab switch
    val webView = remember(activity) {
        runCatching {
            WebView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        webViewFailed = true
                    }
                }
            }
        }.getOrNull()
    }

    // FIX 4: Proper destroy - stop loading first, then destroy. This stops "keeps stopping"
    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView?.stopLoading()
                webView?.removeAllViews()
                webView?.destroy()
            }
        }
    }

    if (webView == null || webViewFailed || hasError) {
        // Fallback - never show blank / crash, show text version
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            if (bookName.isNotBlank()) {
                Text(
                    text = "$bookName $chapter - Open EW",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                text = if (hasError) "Could not load commentary. Please try again."
                else if (renderedHtml.isBlank()) "No commentary available for this chapter."
                else renderedHtml.replace(Regex("<[^>]*>"), " ").trim(),
                color = if (renderedHtml.isBlank()) secondaryText else textColor,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 8).sp
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (bookName.isNotBlank()) {
            Text(
                text = "$bookName $chapter - Open EW",
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }

        // FIX 5: Only load when html actually changes, on UI thread, in try/catch
        val lastLoaded = remember { mutableStateOf("") }
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            factory = { webView },
            update = { wv ->
                if (lastLoaded.value != html) {
                    lastLoaded.value = html
                    runCatching {
                        wv.post {
                            runCatching {
                                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                            }
                        }
                    }
                }
            },
            onRelease = { wv ->
                // Don't destroy here - DisposableEffect handles it, just stop loading
                runCatching { wv.stopLoading() }
            }
        )
    }
}