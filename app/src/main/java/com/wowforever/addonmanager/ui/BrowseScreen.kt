package com.wowforever.addonmanager.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowseScreen(folderUri: Uri?, onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text("Browse Addons", style = MaterialTheme.typography.titleMedium)
        }
        status?.let { Text(it, Modifier.padding(horizontal = 8.dp)) }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.contains(".zip") || url.contains("download") || url.contains("forgecdn")) {
                                if (folderUri != null) {
                                    status = "Installing..."
                                    scope.launch {
                                        try {
                                            installFromUrl(url, folderUri, context)
                                            status = "Installed from $url"
                                        } catch (e: Exception) {
                                            status = "Install failed: ${e.message}"
                                        }
                                    }
                                    return true
                                } else {
                                    status = "Select AddOns folder first"
                                    return true
                                }
                            }
                            return false
                        }
                    }
                    loadUrl("https://www.curseforge.com/wow/search?page=1&pageSize=20&sortType=relevancy&search=addon")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
