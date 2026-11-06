package com.wowforever.addonmanager.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class AddonItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val downloadUrl: String,
    val author: String,
    val iconUrl: String? = null
)

data class InstalledAddon(
    val folderName: String,
    val title: String,
    val version: String,
    val matchedRemote: AddonItem? = null,
    val hasUpdate: Boolean = false
)

@Composable
fun BrowseScreen(folderUri: Uri?, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AddonItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var installedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
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
            Text("Browse Addons", style = MaterialTheme.typography.titleLarge, color = CurseText)
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)
        ) { Text("Search") }

        if (loading) CircularProgressIndicator(color = CurseOrange)
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { addon ->
                AddonCard(
                    addon = addon,
                    isInstalled = installedFolders.any { it.equals(addon.name, ignoreCase = true) },
                    folderUri = folderUri,
                    onStatus = { status = it },
                    onInstalledChanged = {
                        folderUri?.let { uri ->
                            val tree = DocumentFile.fromTreeUri(context, uri)
                            installedFolders = tree?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.toSet() ?: emptySet()
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CurseCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!addon.iconUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(addon.iconUrl),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(addon.name, style = MaterialTheme.typography.titleMedium, color = CurseText)
                Text("v${addon.version} • ${addon.author}", style = MaterialTheme.typography.bodySmall, color = CurseMuted)
                if (addon.description.isNotBlank()) {
                    Text(addon.description, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = CurseMuted)
                }
            }
            Column {
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
                        enabled = !busy && folderUri != null,
                        colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)
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
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))
                    ) { Text("Uninstall") }
                    Spacer(Modifier.height(4.dp))
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
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)
                    ) { Text("Update") }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CurseOrange)
    }
}

@Composable
fun InstalledScreen(folderUri: Uri?, apiKey: String, onBack: () -> Unit, context: android.content.Context) {
    var installed by remember { mutableStateOf<List<InstalledAddon>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderUri) {
        if (folderUri == null) return@LaunchedEffect
        loading = true
        scope.launch {
            installed = loadInstalledAddons(folderUri, context)
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            Text("Installed Addons", style = MaterialTheme.typography.titleLarge, color = CurseText)
        }
        Spacer(Modifier.height(8.dp))
        if (loading) CircularProgressIndicator(color = CurseOrange)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(installed) { addon ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CurseCard),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(addon.title.ifBlank { addon.folderName }, style = MaterialTheme.typography.titleMedium, color = CurseText)
                        Text("v${addon.version} • Folder: ${addon.folderName}", style = MaterialTheme.typography.bodySmall, color = CurseMuted)
                        if (addon.hasUpdate && addon.matchedRemote != null) {
                            Text("Update available: v${addon.matchedRemote.version}", color = CurseOrange, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            installFromUrl(addon.matchedRemote.downloadUrl, folderUri!!, context)
                                            // refresh
                                            installed = loadInstalledAddons(folderUri, context)
                                        } catch (_: Exception) {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)
                            ) { Text("Update") }
                        } else if (addon.matchedRemote != null) {
                            Text("Up to date", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (installed.isEmpty() && !loading) {
            Text("No addon folders found.", style = MaterialTheme.typography.bodyMedium, color = CurseMuted)
        }
    }
}

suspend fun loadInstalledAddons(folderUri: Uri, context: android.content.Context): List<InstalledAddon> {
    val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val result = mutableListOf<InstalledAddon>()
    val client = OkHttpClient()

    // Load remote catalog once for matching
    val remoteList = try {
        val req = Request.Builder().url("https://api.mmoui.com/v3/game/WOW/filelist.json").build()
        withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<JSONObject>()
                val arr = JSONArray(resp.body?.string() ?: "[]")
                (0 until arr.length()).map { arr.getJSONObject(it) }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    tree.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
        val folderName = dir.name ?: return@forEach
        var title = folderName
        var version = "?"

        // Try to read .toc
        dir.listFiles()?.find { it.name?.endsWith(".toc", ignoreCase = true) == true }?.let { toc ->
            context.contentResolver.openInputStream(toc.uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("## Title:")) {
                            title = trimmed.substringAfter("## Title:").trim().removePrefix("|")
                        } else if (trimmed.startsWith("## Version:")) {
                            version = trimmed.substringAfter("## Version:").trim()
                        }
                    }
                }
            }
        }

        // Match to remote
        val match = remoteList.find { it.optString("UIName", "").equals(title, ignoreCase = true) || it.optString("UIName", "").equals(folderName, ignoreCase = true) }
        val remoteItem = match?.let {
            val id = it.optString("UID", "")
            AddonItem(
                id = id,
                name = it.optString("UIName", ""),
                version = it.optString("UIVersion", ""),
                description = "",
                downloadUrl = "https://cdn.wowinterface.com/downloads/getfile.php?id=$id",
                author = it.optString("UIAuthorName", ""),
                iconUrl = it.optJSONArray("UIIMG_Thumbs")?.optString(0)
            )
        }

        val hasUpdate = remoteItem != null && remoteItem.version.isNotBlank() && version != "?" && remoteItem.version != version

        result.add(InstalledAddon(folderName, title, version, remoteItem, hasUpdate))
    }
    return result.sortedBy { it.title }
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
            val icon = obj.optJSONArray("UIIMG_Thumbs")?.optString(0)
            val downloadUrl = "https://cdn.wowinterface.com/downloads/getfile.php?id=$id"
            matches.add(
                AddonItem(
                    id = id,
                    name = name,
                    version = version,
                    description = "",
                    downloadUrl = downloadUrl,
                    author = author,
                    iconUrl = icon
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
