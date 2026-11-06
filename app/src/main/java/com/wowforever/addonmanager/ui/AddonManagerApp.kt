package com.wowforever.addonmanager.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.wowforever.addonmanager.api.CurseForgeApi
import com.wowforever.addonmanager.model.Mod
import com.wowforever.addonmanager.model.ModFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import android.content.pm.PackageManager
import androidx.core.content.FileProvider

@Composable
fun AddonManagerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("addon_manager", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
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
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it; prefs.edit().putString("api_key", it).apply() },
                folderUri = folderUri,
                onPickFolder = { folderPicker.launch(null) },
                onSearch = { currentScreen = "search" },
                onInstalled = { currentScreen = "installed" },
                onAutoDetect = {
                    // Try common path
                    val common = File(Environment.getExternalStorageDirectory(), "WoW Forever/_forever_/Interface/AddOns")
                    if (common.exists() && common.isDirectory) {
                        // For simplicity, note that SAF is preferred; user can still pick
                        // In practice, for shared storage we might need different handling
                    }
                },
                context = context
            )
            "search" -> SearchScreen(
                apiKey = apiKey,
                folderUri = folderUri,
                onBack = { currentScreen = "home" },
                context = context
            )
            "installed" -> InstalledScreen(
                folderUri = folderUri,
                apiKey = apiKey,
                onBack = { currentScreen = "home" },
                context = context
            )
        }
    }
}

@Composable
fun HomeScreen(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    folderUri: Uri?,
    onPickFolder: () -> Unit,
    onSearch: () -> Unit,
    onInstalled: () -> Unit,
    onAutoDetect: () -> Unit,
    context: Context
) {
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("WoW Forever Addon Manager", style = MaterialTheme.typography.headlineMedium)
        Text("Get API key from console.curseforge.com", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("CurseForge API Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
            Text(if (folderUri != null) "Change AddOns Folder" else "Select AddOns Folder")
        }
        if (folderUri != null) {
            Text("Folder selected", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Please select the AddOns folder.\nCommon path: /storage/emulated/0/WoW Forever/_forever_/Interface/AddOns", style = MaterialTheme.typography.bodySmall)
        }

        Button(onClick = onSearch, enabled = apiKey.isNotBlank() && folderUri != null, modifier = Modifier.fillMaxWidth()) {
            Text("Search & Install Addons")
        }
        Button(onClick = onInstalled, enabled = folderUri != null, modifier = Modifier.fillMaxWidth()) {
            Text("Manage Installed")
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (checkingUpdate) "Checking..." else "Check for App Updates")
        }
        updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Text(
            "Note: Full CurseForge account login/sync is not supported in this quick app (requires proprietary auth). Local management + API key works for search/install/updates.",
            style = MaterialTheme.typography.bodySmall
        )
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
    val tag = json.getString("tag_name") // e.g. v1.0-17
    val latestVersion = tag.removePrefix("v").substringBefore("-")

    if (latestVersion <= currentVersion) {
        return "You are up to date (v$currentVersion). Latest is $tag."
    }

    val assets = json.getJSONArray("assets")
    var apkUrl: String? = null
    var apkName = "WoWForeverAddonManager.apk"
    for (i in 0 until assets.length()) {
        val asset = assets.getJSONObject(i)
        val name = asset.getString("name")
        if (name.endsWith(".apk")) {
            apkUrl = asset.getString("browser_download_url")
            apkName = name
            break
        }
    }
    if (apkUrl == null) return "No APK found in latest release $tag"

    // Download the APK
    val apkFile = File(context.cacheDir, apkName)
    withContext(Dispatchers.IO) {
        val dlReq = Request.Builder().url(apkUrl).build()
        client.newCall(dlReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Download failed: ${resp.code}")
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(apkFile).use { output -> input.copyTo(output) }
            }
        }
    }

    // Prompt install via FileProvider
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)

    return "Update $tag downloaded. Install prompt opened."
}

@Composable
fun SearchScreen(apiKey: String, folderUri: Uri?, onBack: () -> Unit, context: Context) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Mod>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search addons") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (query.isBlank()) return@Button
                loading = true
                error = null
                scope.launch {
                    try {
                        val api = CurseForgeApi(apiKey)
                        val resp = withContext(Dispatchers.IO) { api.searchMods(query) }
                        results = resp.data
                    } catch (e: Exception) {
                        error = e.message
                    }
                    loading = false
                }
            }) { Text("Search") }
        }
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn {
            items(results) { mod ->
                ModCard(mod, apiKey, folderUri, context)
            }
        }
    }
}

@Composable
fun ModCard(mod: Mod, apiKey: String, folderUri: Uri?, context: Context) {
    var expanded by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<ModFile>>(emptyList()) }
    var installing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(mod.name, style = MaterialTheme.typography.titleMedium)
            Text(mod.summary, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            Text("${mod.downloadCount} downloads", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                expanded = !expanded
                if (expanded && files.isEmpty()) {
                    scope.launch {
                        try {
                            val api = CurseForgeApi(apiKey)
                            files = withContext(Dispatchers.IO) { api.getModFiles(mod.id) }
                        } catch (e: Exception) { /* ignore */ }
                    }
                }
            }) { Text(if (expanded) "Hide files" else "Show files / Install") }

            if (expanded) {
                files.take(5).forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${file.displayName} (${file.fileName})", modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                installing = true
                                scope.launch {
                                    try {
                                        installMod(apiKey, mod.id, file, folderUri, context)
                                    } catch (e: Exception) { /* show error somehow */ }
                                    installing = false
                                }
                            },
                            enabled = !installing && folderUri != null
                        ) { Text("Install") }
                    }
                }
                if (installing) CircularProgressIndicator()
            }
        }
    }
}

suspend fun installMod(apiKey: String, modId: Int, file: ModFile, folderUri: Uri?, context: Context) {
    if (folderUri == null) return
    val api = CurseForgeApi(apiKey)
    val downloadUrl = withContext(Dispatchers.IO) { api.getDownloadUrl(modId, file.id) }

    // Download
    val client = okhttp3.OkHttpClient()
    val req = okhttp3.Request.Builder().url(downloadUrl).header("x-api-key", apiKey).build()
    val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
    if (!resp.isSuccessful) throw Exception("Download failed")

    val tempFile = File(context.cacheDir, file.fileName)
    withContext(Dispatchers.IO) {
        resp.body?.byteStream()?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
    }

    // Extract to SAF folder
    val tree = DocumentFile.fromTreeUri(context, folderUri) ?: throw Exception("Invalid folder")
    withContext(Dispatchers.IO) {
        ZipInputStream(tempFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.isNotEmpty()) {
                    val parts = entry.name.trim('/').split('/')
                    if (parts.isNotEmpty()) {
                        var current: DocumentFile = tree
                        // Create intermediate directories
                        for (i in 0 until parts.size - 1) {
                            val part = parts[i]
                            if (part.isNotEmpty()) {
                                current = current.findFile(part) ?: current.createDirectory(part)
                                    ?: throw Exception("Failed to create dir $part")
                            }
                        }
                        val fileName = parts.last()
                        if (fileName.isNotEmpty()) {
                            val outFile = current.findFile(fileName)
                                ?: current.createFile("application/octet-stream", fileName)
                                ?: throw Exception("Failed to create file $fileName")
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

@Composable
fun InstalledScreen(folderUri: Uri?, apiKey: String, onBack: () -> Unit, context: Context) {
    var installed by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(folderUri) {
        folderUri?.let { uri ->
            val tree = DocumentFile.fromTreeUri(context, uri)
            installed = tree?.listFiles()?.filter { it.isDirectory }?.map { it.name ?: "" } ?: emptyList()
        }
    }
    Column {
        Button(onClick = onBack) { Text("Back") }
        Text("Installed addons (folders found):", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(installed) { name ->
                Text(name)
            }
        }
        Text("Update checking: Compare against CurseForge latest for 1.60.1. Full auto-update scan can be extended.", style = MaterialTheme.typography.bodySmall)
    }
}
