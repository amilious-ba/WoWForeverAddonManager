package com.wowforever.addonmanager.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CurseForgeBrowseScreen(folderUri: Uri?, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AddonItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var installedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderUri) {
        folderUri?.let { uri ->
            val tree = DocumentFile.fromTreeUri(context, uri)
            installedFolders = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            Text("Browse CurseForge", style = MaterialTheme.typography.titleLarge, color = CurseText)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search addons") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CurseOrange,
                unfocusedBorderColor = CurseMuted,
                focusedLabelColor = CurseOrange,
                cursorColor = CurseOrange
            )
        )
        Button(
            onClick = {
                if (query.isBlank() || webView == null) return@Button
                loading = true
                status = null
                results = emptyList()
                val searchUrl = "https://www.curseforge.com/wow/addons/search?search=${Uri.encode(query.trim())}"
                webView?.loadUrl(searchUrl)
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)
        ) { Text("Search") }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = CurseOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Loading CurseForge results...", color = CurseMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // Hidden WebView for scraping
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url?.contains("/search") == true) {
                                scope.launch {
                                    delay(2500) // wait for JS to render
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            const cards = document.querySelectorAll('[class*="project-card"], [class*="addon-card"], a[href*="/wow/addons/"]');
                                            const results = [];
                                            const seen = new Set();
                                            cards.forEach(el => {
                                                const link = el.closest('a') || el.querySelector('a') || el;
                                                const href = link.href || '';
                                                if (!href.includes('/wow/addons/') || href.includes('/search')) return;
                                                const slug = href.split('/').filter(Boolean).pop() || '';
                                                if (seen.has(slug)) return;
                                                seen.add(slug);
                                                const nameEl = el.querySelector('h3, h2, [class*="name"], [class*="title"]') || el;
                                                const name = (nameEl.innerText || nameEl.textContent || slug).trim().split('\n')[0];
                                                const img = el.querySelector('img');
                                                const icon = img ? img.src : '';
                                                const authorEl = el.querySelector('[class*="author"], [class*="creator"]');
                                                const author = authorEl ? authorEl.innerText.trim() : '';
                                                results.push({name, slug, icon, author, url: href});
                                            });
                                            return JSON.stringify(results.slice(0, 30));
                                        })();
                                        """.trimIndent()
                                    ) { json ->
                                        try {
                                            val clean = json?.trim('"')?.replace("\\\"", "\"")?.replace("\\n", "") ?: "[]"
                                            val arr = JSONArray(clean)
                                            val items = mutableListOf<AddonItem>()
                                            for (i in 0 until arr.length()) {
                                                val obj = arr.getJSONObject(i)
                                                val name = obj.optString("name", "")
                                                val slug = obj.optString("slug", "")
                                                val icon = obj.optString("icon", "")
                                                val author = obj.optString("author", "")
                                                val projectUrl = obj.optString("url", "")
                                                if (name.isNotBlank() && slug.isNotBlank()) {
                                                    items.add(
                                                        AddonItem(
                                                            id = slug,
                                                            name = name,
                                                            version = "latest",
                                                            description = "",
                                                            downloadUrl = projectUrl, // will be resolved on install
                                                            author = author,
                                                            iconUrl = icon.ifBlank { null }
                                                        )
                                                    )
                                                }
                                            }
                                            results = items
                                            status = if (items.isEmpty()) "No results (or page blocked). Try a different search." else null
                                        } catch (e: Exception) {
                                            status = "Failed to parse results"
                                        }
                                        loading = false
                                    }
                                }
                            }
                        }
                    }
                    webView = this
                }
            },
            modifier = Modifier.size(0.dp) // hidden
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { addon ->
                AddonCard(
                    addon = addon,
                    isInstalled = installedFolders.any { it.equals(addon.name, ignoreCase = true) || it.equals(addon.id, ignoreCase = true) },
                    folderUri = folderUri,
                    onStatus = { status = it },
                    onInstalledChanged = {
                        folderUri?.let { uri ->
                            val tree = DocumentFile.fromTreeUri(context, uri)
                            installedFolders = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
                        }
                    },
                    isCurseForge = true
                )
            }
        }
    }
}
