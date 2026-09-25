package com.ridesaathi.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.delay

private const val ASSET_HOST = "appassets.androidplatform.net"
private enum class MapStatus { Loading, Ready, Error }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapPreview(mapUrl: String, language: String) {
    var attempt by remember(mapUrl) { mutableIntStateOf(0) }
    key(mapUrl, attempt) {
        var status by remember { mutableStateOf(MapStatus.Loading) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        var disposed by remember { mutableStateOf(false) }
        val bundled = mapUrl.toUri().let {
            it.host in setOf("www.openstreetmap.org", "openstreetmap.org") && it.path == "/export/embed.html"
        }
        val displayUrl = if (bundled) mapUrl.toUri().buildUpon()
            .authority(ASSET_HOST).path("/assets/map/index.html").build().toString() else mapUrl
        val mapAlpha by animateFloatAsState(
            targetValue = if (status == MapStatus.Ready) 1f else 0.72f,
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            label = "mapPreviewAlpha"
        )

        DisposableEffect(Unit) {
            onDispose { disposed = true }
        }
        LaunchedEffect(webView) {
            val view = webView ?: return@LaunchedEffect
            var loadingSince = SystemClock.elapsedRealtime()
            while (status != MapStatus.Error) {
                if (bundled) view.evaluateJavascript("window.rideSaathiMapStatus || 'loading'") { result ->
                    if (!disposed && status != MapStatus.Error) status = when (result) {
                        "\"ready\"" -> MapStatus.Ready
                        "\"error\"" -> MapStatus.Error
                        else -> MapStatus.Loading
                    }
                }
                if (status == MapStatus.Ready) loadingSince = SystemClock.elapsedRealtime()
                else if (SystemClock.elapsedRealtime() - loadingSince > 20_000) status = MapStatus.Error
                delay(300)
            }
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.userAgentString = "${settings.userAgentString} RideSaathi/0.1 (com.ridesaathi.app)"
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                            if (request.url.host != ASSET_HOST) return null
                            val name = request.url.lastPathSegment
                            val mime = when (name) {
                                "index.html" -> "text/html"
                                "leaflet.js" -> "application/javascript"
                                "leaflet.css" -> "text/css"
                                else -> return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
                            }
                            return WebResourceResponse(mime, "UTF-8", context.assets.open("map/$name"))
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (!disposed && status != MapStatus.Error) status = MapStatus.Loading
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            // The bundled map reports readiness only after its tiles load.
                            if (!bundled && !disposed && status != MapStatus.Error) status = MapStatus.Ready
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) {
                            if (request.isForMainFrame && !disposed) status = MapStatus.Error
                        }

                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest, errorResponse: WebResourceResponse?) {
                            if (request.isForMainFrame && !disposed) status = MapStatus.Error
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                            if (request.hasGesture() && request.url.scheme == "https") {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                return true
                            }
                            return request.url.scheme != "https"
                        }
                    }
                    loadUrl(displayUrl)
                    webView = this
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
            modifier = Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.medium)
                .alpha(mapAlpha)
        )
        AnimatedVisibility(
            visible = status == MapStatus.Loading,
            enter = fadeIn(tween(180)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f),
            exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.92f)
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Text(Words.get(language, "mapAttribution"), style = MaterialTheme.typography.bodySmall)
        AnimatedVisibility(
            visible = status == MapStatus.Error,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160))
        ) {
            Column(Modifier.animateContentSize(tween(220, easing = FastOutSlowInEasing))) {
                Text(Words.get(language, "mapUnavailable"), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) {
                    Text(Words.get(language, "retry"))
                }
            }
        }
    }
}
