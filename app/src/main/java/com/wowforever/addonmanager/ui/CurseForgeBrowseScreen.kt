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
import kotlinx.coroutines.launch

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
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    // Hide CurseForge chrome, promo, filters, pagination
                                    const hideSelectors = [
                                        'header', 'footer', 'nav',
                                        '[class*="header"]', '[class*="footer"]', '[class*="nav"]',
                                        '[class*="bottom"]', '[class*="tab-bar"]', '[class*="toolbar"]',
                                        '[class*="mobile-nav"]', '[class*="app-nav"]',
                                        '[class*="banner"]', '[class*="promo"]', '[class*="hero"]',
                                        '[class*="filter"]', '[class*="sort"]',
                                        '[class*="pagination"]', '[class*="pager"]',
                                        '[class*="breadcrumb"]'
                                    ];
                                    hideSelectors.forEach(sel => {
                                        document.querySelectorAll(sel).forEach(el => {
                                            if (el.id !== 'wf-search') el.style.display = 'none';
                                        });
                                    });

                                    // Also hide by text content
                                    document.querySelectorAll('div, section, aside').forEach(el => {
                                        const t = (el.innerText || '').toLowerCase();
                                        if (t.includes('filters') && t.includes('sort') && el.children.length < 5) el.style.display = 'none';
                                        if (t.includes('browse addons for') || t.includes('10,000+ projects') || t.includes('addon support')) el.style.display = 'none';
                                    });

                                    // Sticky search bar at top
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

                                    // Process project cards
                                    const links = document.querySelectorAll('a[href*="/wow/addons/"]');
                                    const seen = new Set();
                                    links.forEach(a => {
                                        const href = a.href || '';
                                        if (!href.includes('/wow/addons/') || href.includes('/search') || href.includes('/files')) return;
                                        const slug = href.split('/').filter(Boolean).pop();
                                        if (!slug || seen.has(slug) || slug.length < 2) return;
                                        seen.add(slug);

                                        const parent = a.closest('div, article, li, section') || a.parentElement;
                                        if (!parent || parent.dataset.wfProcessed) return;
                                        parent.dataset.wfProcessed = '1';

                                        const name = (a.innerText || slug).trim().split('\n')[0].substring(0, 60);
                                        const installed = window.AddonBridge && window.AddonBridge.isInstalled(name);

                                        // Hide original download buttons
                                        const originals = parent.querySelectorAll('button, a[href*="download"], [class*="download"], [class*="Download"]');
                                        originals.forEach(el => {
                                            el.style.display = 'none';
                                            el.onclick = function(e) { e.preventDefault(); e.stopImmediatePropagation(); return false; };
                                        });

                                        // Inject buttons at the bottom of the card
                                        if (!parent.querySelector('.wf-actions')) {
                                            const actions = document.createElement('div');
                                            actions.className = 'wf-actions';
                                            actions.style.cssText = 'display:flex;gap:8px;margin-top:10px;padding-top:8px;border-top:1px solid #333;';

                                            if (installed) {
                                                const upd = document.createElement('button');
                                                upd.textContent = 'Update';
                                                upd.style.cssText = 'background:#FF6A00;color:#fff;border:none;padding:8px 16px;border-radius:4px;font-size:14px;flex:1;';
                                                upd.onclick = function(e) {
                                                    e.preventDefault();
                                                    e.stopImmediatePropagation();
                                                    upd.textContent = 'Updating...';
                                                    upd.disabled = true;
                                                    window.AddonBridge.onInstall(href, name);
                                                    return false;
                                                };
                                                actions.appendChild(upd);

                                                const un = document.createElement('button');
                                                un.textContent = 'Uninstall';
                                                un.style.cssText = 'background:#555;color:#fff;border:none;padding:8px 16px;border-radius:4px;font-size:14px;';
                                                un.onclick = function(e) {
                                                    e.preventDefault();
                                                    e.stopImmediatePropagation();
                                                    window.AddonBridge.onUninstall(name);
                                                    return false;
                                                };
                                                actions.appendChild(un);
                                            } else {
                                                const inst = document.createElement('button');
                                                inst.textContent = 'Install';
                                                inst.style.cssText = 'background:#FF6A00;color:#fff;border:none;padding:8px 16px;border-radius:4px;font-size:14px;flex:1;';
                                                inst.onclick = function(e) {
                                                    e.preventDefault();
                                                    e.stopImmediatePropagation();
                                                    inst.textContent = 'Installing...';
                                                    inst.disabled = true;
                                                    window.AddonBridge.onInstall(href, name);
                                                    return false;
                                                };
                                                actions.appendChild(inst);
                                            }
                                            parent.appendChild(actions);
                                        }
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
