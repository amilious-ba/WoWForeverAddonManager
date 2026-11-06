package com.wowforever.addonmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Composable
fun AddonManagerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("addon_manager", Context.MODE_PRIVATE) }
    var folderUri by remember { mutableStateOf(prefs.getString("folder_uri", null)?.let { Uri.parse(it) }) }
    var currentScreen by remember { mutableStateOf("home") }
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            folderUri = it
            prefs.edit().putString("folder_uri", it.toString()).apply()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (currentScreen) {
            "home" -> HomeScreen(
                folderUri = folderUri,
                onPickFolder = { folderPicker.launch(null) },
                onInstalled = { currentScreen = "installed" },
                onBrowse = { currentScreen = "browse" },
                onInstallFromUrl = { currentScreen = "url" },
                onCurseForge = { currentScreen = "curseforge" },
                context = context
            )
            "installed" -> InstalledScreen(
                folderUri = folderUri,
                apiKey = "",
                onBack = { currentScreen = "home" },
                context = context
            )
            "url" -> UrlInstallScreen(
                folderUri = folderUri,
                onBack = { currentScreen = "home" },
                context = context
            )
            "curseforge" -> CurseForgeInstallScreen(
                folderUri = folderUri,
                onBack = { currentScreen = "home" },
                context = context
            )
            "browse" -> BrowseScreen(
                folderUri = folderUri,
                onBack = { currentScreen = "home" }
            )
        }
    }
}

@Composable
fun HomeScreen(
    folderUri: Uri?,
    onPickFolder: () -> Unit,
    onInstalled: () -> Unit,
    onBrowse: () -> Unit,
    onInstallFromUrl: () -> Unit,
    onCurseForge: () -> Unit,
    context: Context
) {
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("WoW Forever Addon Manager", style = MaterialTheme.typography.headlineMedium, color = CurseText)
        Text("Browse and install addons from WoWInterface or CurseForge.", style = MaterialTheme.typography.bodySmall, color = CurseMuted)

        Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) {
            Text(if (folderUri != null) "Change AddOns Folder" else "Select AddOns Folder")
        }
        if (folderUri != null) {
            Text("Folder selected", style = MaterialTheme.typography.bodySmall, color = CurseMuted)
        } else {
            Text("Please select the AddOns folder.\nCommon path: /storage/emulated/0/WoW Forever/_forever_/Interface/AddOns", style = MaterialTheme.typography.bodySmall, color = CurseMuted)
        }

        Button(onClick = onBrowse, enabled = folderUri != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) {
            Text("Browse Addons")
        }
        Button(onClick = onInstalled, enabled = folderUri != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) {
            Text("Installed Addons")
        }
        Button(onClick = onCurseForge, enabled = folderUri != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) {
            Text("Install from CurseForge URL")
        }
        Button(onClick = onInstallFromUrl, enabled = folderUri != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))) {
            Text("Install from Zip URL")
        }

        Button(
            onClick = {
                checkingUpdate = true
                updateMessage = null
                scope.launch {
                    try {
                        val result = checkForUpdate(context)
                        updateMessage = result
                    } catch (e: Exception) {
                        updateMessage = "Update check failed: ${e.message}"
                    }
                    checkingUpdate = false
                }
            },
            enabled = !checkingUpdate,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)
        ) {
            Text(if (checkingUpdate) "Checking..." else "Check for App Updates")
        }
        updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CurseMuted) }
    }
}

suspend fun checkForUpdate(context: Context): String {
    val pm = context.packageManager
    val info = pm.getPackageInfo(context.packageName, 0)
    val currentVersion = info.versionName ?: "1.0"

    val client = OkHttpClient()
    val req = Request.Builder()
        .url("https://api.github.com/repos/amilious-ba/WoWForeverAddonManager/releases/latest")
        .header("Accept", "application/vnd.github+json")
        .build()
    val body = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { it.body?.string() ?: throw Exception("Empty response") }
    }
    val json = JSONObject(body)
    val tag = json.getString("tag_name")
    val latestVersion = tag.removePrefix("v").substringBefore("-")

    if (latestVersion <= currentVersion) {
        return "You are up to date (v$currentVersion). Latest is $tag."
    }

    val assets = json.getJSONArray("assets")
    var apkUrl: String? = null
    for (i in 0 until assets.length()) {
        val asset = assets.getJSONObject(i)
        val name = asset.getString("name")
        if (name.endsWith(".apk")) {
            apkUrl = asset.getString("browser_download_url")
            break
        }
    }
    if (apkUrl == null) return "No APK found in latest release $tag"

    val apkFile = File(context.cacheDir, "WoWForeverAddonManager.apk")
    withContext(Dispatchers.IO) {
        val dlReq = Request.Builder().url(apkUrl).build()
        client.newCall(dlReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Download failed: ${resp.code}")
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(apkFile).use { output -> input.copyTo(output) }
            }
        }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
    return try {
        context.startActivity(intent)
        "Update $tag downloaded. Install prompt opened."
    } catch (e: Exception) {
        "Downloaded but could not open installer: ${e.message}\nFile: ${apkFile.absolutePath}"
    }
}

@Composable
fun UrlInstallScreen(folderUri: Uri?, onBack: () -> Unit, context: Context) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CurseOrange)) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            Text("Install from URL", style = MaterialTheme.typography.titleMedium, color = CurseText)
        }
        Text("Paste a direct .zip download link.", style = MaterialTheme.typography.bodySmall, color = CurseMuted)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Addon .zip URL") },
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
                if (url.isBlank() || folderUri == null) return@Button
                busy = true
                status = null
                scope.launch {
                    try {
                        installFromUrl(url.trim(), folderUri, context)
                        status = "Installed successfully"
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

suspend fun installFromUrl(url: String, folderUri: Uri, context: Context) {
    val client = OkHttpClient()
    val req = Request.Builder().url(url).build()
    val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
    if (!resp.isSuccessful) throw Exception("Download failed (${resp.code})")

    val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank { "addon.zip" }
    val tempFile = File(context.cacheDir, fileName)
    withContext(Dispatchers.IO) {
        resp.body?.byteStream()?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
    }

    val tree = DocumentFile.fromTreeUri(context, folderUri) ?: throw Exception("Invalid folder")
    withContext(Dispatchers.IO) {
        ZipInputStream(tempFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.isNotEmpty()) {
                    val parts = entry.name.trim('/').split('/')
                    if (parts.isNotEmpty()) {
                        var current: DocumentFile = tree
                        for (i in 0 until parts.size - 1) {
                            val part = parts[i]
                            if (part.isNotEmpty()) {
                                current = current.findFile(part) ?: current.createDirectory(part)
                                    ?: throw Exception("Failed to create dir $part")
                            }
                        }
                        val outName = parts.last()
                        if (outName.isNotEmpty()) {
                            val outFile = current.findFile(outName)
                                ?: current.createFile("application/octet-stream", outName)
                                ?: throw Exception("Failed to create file $outName")
                            context.contentResolver.openOutputStream(outFile.uri)?.use { out ->
                                zis.copyTo(out)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
    tempFile.delete()
}
