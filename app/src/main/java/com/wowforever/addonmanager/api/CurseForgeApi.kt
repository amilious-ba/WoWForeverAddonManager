package com.wowforever.addonmanager.api

import com.wowforever.addonmanager.model.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CurseForgeApi(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl = "https://api.curseforge.com/v1"
    private val gameId = 1 // World of Warcraft
    private val gameVersion = "1.60.1" // WoW Forever

    private fun request(url: String): String {
        if (apiKey.isBlank()) throw Exception("API key is empty. Get one at console.curseforge.com")
        val req = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey.trim())
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("API error ${response.code}: $errBody. Check your API key at console.curseforge.com (must be valid and have WoW access).")
            }
            return response.body?.string() ?: throw Exception("Empty body")
        }
    }

    fun searchMods(query: String, page: Int = 0, pageSize: Int = 20): ModSearchResponse {
        // Try with gameVersion first; if it fails the caller will see the error
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/mods/search?gameId=$gameId&searchFilter=$encoded&gameVersion=$gameVersion&index=${page * pageSize}&pageSize=$pageSize&sortField=2&sortOrder=desc"
        val body = request(url)
        return json.decodeFromString(body)
    }

    fun getModFiles(modId: Int): List<ModFile> {
        val url = "$baseUrl/mods/$modId/files?gameVersion=$gameVersion&pageSize=50"
        val body = request(url)
        return json.decodeFromString<ModFilesResponse>(body).data
    }

    fun getDownloadUrl(modId: Int, fileId: Int): String {
        val url = "$baseUrl/mods/$modId/files/$fileId/download-url"
        val body = request(url)
        return json.decodeFromString<DownloadUrlResponse>(body).data
    }
}
