package com.wowforever.addonmanager.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AddonItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val downloadUrl: String,
    val author: String
)

@Composable
fun BrowseScreen(folderUri: Uri?, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AddonItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderUri) {
        folderUri?.let { uri ->
            val tree = DocumentFile.fromTreeUri(context, uri)
            installed = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            Text("Browse Addons", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search addons") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (query.isBlank()) return@Button
                loading = true
                status = null
                scope.launch {
                    try {
                        results = searchWowInterface(query)
                        if (results.isEmpty()) status = "No results for \"$query\""
                    } catch (e: Exception) {
                        status = "Search failed: ${e.message}"
                    }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Search") }

        if (loading) CircularProgressIndicator()
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn {
            items(results) { addon ->
                AddonCard(
                    addon = addon,
                    isInstalled = installed.any { it.equals(addon.name, ignoreCase = true) },
                    folderUri = folderUri,
                    onStatus = { status = it },
                    onInstalledChanged = {
                        folderUri?.let { uri ->
                            val tree = DocumentFile.fromTreeUri(context, uri)
                            installed = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AddonCard(
    addon: AddonItem,
    isInstalled: Boolean,
    folderUri: Uri?,
    onStatus: (String) -> Unit,
    onInstalledChanged: () -> Unit
) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(addon.name, style = MaterialTheme.typography.titleMedium)
            Text("v${addon.version} by ${addon.author}", style = MaterialTheme.typography.bodySmall)
            if (addon.description.isNotBlank()) {
                Text(addon.description, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                if (!isInstalled) {
                    Button(
                        onClick = {
                            if (folderUri == null) return@Button
                            busy = true
                            scope.launch {
                                try {
                                    installFromUrl(addon.downloadUrl, folderUri, context)
                                    onStatus("Installed ${addon.name}")
                                    onInstalledChanged()
                                } catch (e: Exception) {
                                    onStatus("Install failed: ${e.message}")
                                }
                                busy = false
                            }
                        },
                        enabled = !busy && folderUri != null
                    ) { Text("Install") }
                } else {
                    Button(
                        onClick = {
                            if (folderUri == null) return@Button
                            busy = true
                            scope.launch {
                                try {
                                    uninstallAddon(addon.name, folderUri, context)
                                    onStatus("Uninstalled ${addon.name}")
                                    onInstalledChanged()
                                } catch (e: Exception) {
                                    onStatus("Uninstall failed: ${e.message}")
                                }
                                busy = false
                            }
                        },
                        enabled = !busy
                    ) { Text("Uninstall") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (folderUri == null) return@Button
                            busy = true
                            scope.launch {
                                try {
                                    installFromUrl(addon.downloadUrl, folderUri, context)
                                    onStatus("Updated ${addon.name}")
                                    onInstalledChanged()
                                } catch (e: Exception) {
                                    onStatus("Update failed: ${e.message}")
                                }
                                busy = false
                            }
                        },
                        enabled = !busy
                    ) { Text("Update") }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp))
        }
    }
}

suspend fun searchWowInterface(query: String): List<AddonItem> {
    val client = OkHttpClient()
    val req = Request.Builder()
        .url("https://api.mmoui.com/v3/game/WOW/filelist.json")
        .header("User-Agent", "WoWForeverAddonManager/1.0")
        .build()
    val body = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Empty response")
        }
    }
    val arr = JSONArray(body)
    val q = query.trim().lowercase()
    val matches = mutableListOf<AddonItem>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val name = obj.optString("UIName", "")
        if (name.isBlank()) continue
        if (name.lowercase().contains(q)) {
            val id = obj.optString("UID", "")
            val version = obj.optString("UIVersion", "")
            val author = obj.optString("UIAuthorName", "")
            val downloadUrl = "https://cdn.wowinterface.com/downloads/getfile.php?id=$id"
            matches.add(
                AddonItem(
                    id = id,
                    name = name,
                    version = version,
                    description = "",
                    downloadUrl = downloadUrl,
                    author = author
                )
            )
        }
        if (matches.size >= 40) break
    }
    return matches
}

suspend fun uninstallAddon(name: String, folderUri: Uri, context: android.content.Context) {
    val tree = DocumentFile.fromTreeUri(context, folderUri) ?: throw Exception("Invalid folder")
    withContext(Dispatchers.IO) {
        tree.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.equals(name, ignoreCase = true)) {
                file.delete()
            }
        }
    }
}
