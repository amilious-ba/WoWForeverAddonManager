package com.wowforever.addonmanager.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@Composable
fun CurseForgeInstallScreen(folderUri: Uri?, onBack: () -> Unit, context: Context) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            Text("Install from CurseForge", style = MaterialTheme.typography.titleMedium, color = CurseText)
        }
        Text("Paste a CurseForge project URL (e.g. https://www.curseforge.com/wow/addons/olympus-guild)", style = MaterialTheme.typography.bodySmall, color = CurseMuted)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("CurseForge project URL") },
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
                if (url.isBlank()) return@Button
                scope.launch {
                    try {
                        val info = fetchCurseForgeInfo(url.trim())
                        preview = "${info.title}\nv${info.version} • ${info.author}"
                        status = null
                    } catch (e: Exception) {
                        status = "Could not read project: ${e.message}"
                        preview = null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))
        ) { Text("Preview") }

        preview?.let { Text(it, color = CurseText, style = MaterialTheme.typography.bodyMedium) }

        Button(
            onClick = {
                if (url.isBlank() || folderUri == null) return@Button
                busy = true
                status = null
                scope.launch {
                    try {
                        val info = fetchCurseForgeInfo(url.trim())
                        installFromUrl(info.downloadUrl, folderUri, context)
                        injectProjectId(info.projectId, info.slug, folderUri, context)
                        status = "Installed ${info.title} (project ID ${info.projectId} written to .toc)"
                        preview = null
                    } catch (e: Exception) {
                        status = "Failed: ${e.message}"
                    }
                    busy = false
                }
            },
            enabled = !busy && url.isNotBlank() && folderUri != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)
        ) {
            Text(if (busy) "Installing..." else "Download & Install")
        }
        status?.let { Text(it, color = CurseText) }
    }
}

data class CurseInfo(
    val projectId: Int,
    val title: String,
    val version: String,
    val author: String,
    val downloadUrl: String,
    val slug: String
)

suspend fun fetchCurseForgeInfo(projectUrl: String): CurseInfo {
    val slug = projectUrl.trim().trimEnd('/').substringAfterLast('/').substringBefore('?')
    val client = OkHttpClient()
    val req = Request.Builder()
        .url("https://api.cfwidget.com/wow/addons/$slug")
        .header("User-Agent", "WoWForeverAddonManager/1.0")
        .build()
    val body = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Project not found (HTTP ${resp.code})")
            resp.body?.string() ?: throw Exception("Empty response")
        }
    }
    val json = JSONObject(body)
    val id = json.getInt("id")
    val title = json.optString("title", slug)
    val author = json.optJSONArray("members")?.optJSONObject(0)?.optString("username", "") ?: ""
    val files = json.optJSONArray("files")
    if (files == null || files.length() == 0) throw Exception("No files found")
    val latest = files.getJSONObject(0)
    val fileId = latest.getInt("id")
    val version = latest.optString("name", "").substringBefore(".zip").substringAfterLast("-")
    val downloadUrl = "https://www.curseforge.com/api/v1/mods/$id/files/$fileId/download"
    return CurseInfo(id, title, version, author, downloadUrl, slug)
}

suspend fun injectProjectId(projectId: Int, slug: String, folderUri: Uri, context: Context) {
    val tree = DocumentFile.fromTreeUri(context, folderUri) ?: return
    val dirs = tree.listFiles()?.filter { it.isDirectory } ?: return
    val target = dirs.find { it.name.equals(slug, ignoreCase = true) } ?: dirs.maxByOrNull { it.lastModified() } ?: return

    val toc = target.listFiles()?.find { it.name?.endsWith(".toc", ignoreCase = true) == true } ?: return
    val lines = mutableListOf<String>()
    var hasProjectId = false
    context.contentResolver.openInputStream(toc.uri)?.use { stream ->
        BufferedReader(InputStreamReader(stream)).useLines { seq ->
            seq.forEach { line ->
                if (line.trim().startsWith("## X-Curse-Project-ID:")) hasProjectId = true
                lines.add(line)
            }
        }
    }
    if (!hasProjectId) {
        lines.add("## X-Curse-Project-ID: $projectId")
        lines.add("## X-Curse-Project-URL: https://www.curseforge.com/wow/addons/$slug")
        context.contentResolver.openOutputStream(toc.uri)?.use { out ->
            OutputStreamWriter(out).use { writer ->
                lines.forEach { writer.write(it + "\n") }
            }
        }
    }
}
