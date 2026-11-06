package com.wowforever.addonmanager.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CurseForgeBrowseScreen(folderUri: Uri?, onBack: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var installedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderUri) {
        folderUri?.let { uri ->
            val tree = DocumentFile.fromTreeUri(context, uri)
            installedFolders = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar with Back and status
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) {
                Text("Back")
            }
            Spacer(Modifier.width(8.dp))
            Text("CurseForge", style = MaterialTheme.typography.titleMedium, color = CurseText)
            Spacer(Modifier.weight(1f))
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CurseMuted) }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onInstall(projectUrl: String, name: String) {
                            scope.launch {
                                status = "Installing $name..."
                                try {
                                    if (folderUri == null) {
                                        status = "Select AddOns folder first"
                                        return@launch
                                    }
                                    val info = fetchCurseForgeInfo(projectUrl)
                                    installFromUrl(info.downloadUrl, folderUri, context)
                                    injectProjectId(info.projectId, info.slug, folderUri, context)
                                    status = "Installed $name"
                                    // Refresh installed list
                                    val tree = DocumentFile.fromTreeUri(context, folderUri)
                                    installedFolders = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
                                } catch (e: Exception) {
                                    status = "Install failed: ${e.message}"
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onUninstall(name: String) {
                            scope.launch {
                                status = "Uninstalling $name..."
                                try {
                                    if (folderUri == null) return@launch
                                    uninstallAddon(name, folderUri, context)
                                    status = "Uninstalled $name"
                                    val tree = DocumentFile.fromTreeUri(context, folderUri)
                                    installedFolders = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
                                } catch (e: Exception) {
                                    status = "Uninstall failed: ${e.message}"
                                }
                            }
                        }

                        @JavascriptInterface
                        fun isInstalled(name: String): Boolean {
                            return installedFolders.any { it.equals(name, ignoreCase = true) }
                        }
                    }, "AddonBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Inject UI modifications
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    // Add a simple top search if not present
                                    if (!document.getElementById('wf-search')) {
                                        const bar = document.createElement('div');
                                        bar.id = 'wf-search';
                                        bar.style.cssText = 'position:sticky;top:0;z-index:9999;background:#1a1a1a;padding:8px;display:flex;gap:8px;';
                                        bar.innerHTML = '<input id="wf-q" placeholder="Search addons" style="flex:1;padding:8px;background:#222;color:#fff;border:1px solid #FF6A00;border-radius:4px;" /><button id="wf-go" style="background:#FF6A00;color:#fff;border:none;padding:8px 12px;border-radius:4px;">Search</button>';
                                        document.body.prepend(bar);
                                        document.getElementById('wf-go').onclick = function() {
                                            const q = document.getElementById('wf-q').value.trim();
                                            if (q) window.location.href = 'https://www.curseforge.com/wow/addons/search?search=' + encodeURIComponent(q);
                                        };
                                        document.getElementById('wf-q').addEventListener('keydown', function(e) {
                                            if (e.key === 'Enter') document.getElementById('wf-go').click();
                                        });
                                    }

                                    // Rewrite download / install buttons
                                    const links = document.querySelectorAll('a[href*="/wow/addons/"]');
                                    const seen = new Set();
                                    links.forEach(a => {
                                        const href = a.href || '';
                                        if (!href.includes('/wow/addons/') || href.includes('/search')) return;
                                        const slug = href.split('/').filter(Boolean).pop();
                                        if (!slug || seen.has(slug)) return;
                                        seen.add(slug);

                                        // Find nearby download-like buttons
                                        const parent = a.closest('div, article, li, section') || a.parentElement;
                                        if (!parent) return;
                                        const buttons = parent.querySelectorAll('button, a[href*="download"], [class*="download"], [class*="install"]');
                                        buttons.forEach(btn => {
                                            if (btn.dataset.wfHooked) return;
                                            btn.dataset.wfHooked = '1';
                                            const name = (a.innerText || slug).trim().split('\n')[0];
                                            const installed = window.AddonBridge && window.AddonBridge.isInstalled(name);
                                            if (installed) {
                                                btn.textContent = 'Update';
                                                btn.onclick = function(e) {
                                                    e.preventDefault();
                                                    e.stopPropagation();
                                                    window.AddonBridge.onInstall(href, name);
                                                };
                                                // Add uninstall
                                                if (!parent.querySelector('.wf-uninstall')) {
                                                    const u = document.createElement('button');
                                                    u.className = 'wf-uninstall';
                                                    u.textContent = 'Uninstall';
                                                    u.style.cssText = 'margin-left:6px;background:#555;color:#fff;border:none;padding:6px 10px;border-radius:4px;';
                                                    u.onclick = function(e) {
                                                        e.preventDefault();
                                                        window.AddonBridge.onUninstall(name);
                                                    };
                                                    btn.parentElement.appendChild(u);
                                                }
                                            } else {
                                                btn.textContent = 'Install';
                                                btn.onclick = function(e) {
                                                    e.preventDefault();
                                                    e.stopPropagation();
                                                    window.AddonBridge.onInstall(href, name);
                                                };
                                            }
                                        });
                                    });
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }
                    loadUrl("https://www.curseforge.com/wow/addons")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
