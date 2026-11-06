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
        val req = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("API error ${response.code}: ${response.body?.string()}")
            }
            return response.body?.string() ?: throw Exception("Empty body")
        }
    }

    fun searchMods(query: String, page: Int = 0, pageSize: Int = 20): ModSearchResponse {
        val url = "$baseUrl/mods/search?gameId=$gameId&searchFilter=${java.net.URLEncoder.encode(query, "UTF-8")}&gameVersion=$gameVersion&index=${page * pageSize}&pageSize=$pageSize&sortField=2&sortOrder=desc"
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
