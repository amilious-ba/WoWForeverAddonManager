package com.wowforever.addonmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class ModSearchResponse(
    val data: List<Mod>,
    val pagination: Pagination
)

@Serializable
data class Mod(
    val id: Int,
    val name: String,
    val summary: String = "",
    val downloadCount: Long = 0,
    val logo: ModLogo? = null,
    val links: ModLinks? = null
)

@Serializable
data class ModLogo(
    val thumbnailUrl: String? = null
)

@Serializable
data class ModLinks(
    val websiteUrl: String? = null
)

@Serializable
data class Pagination(
    val index: Int = 0,
    val pageSize: Int = 0,
    val resultCount: Int = 0,
    val totalCount: Long = 0
)

@Serializable
data class ModFilesResponse(
    val data: List<ModFile>
)

@Serializable
data class ModFile(
    val id: Int,
    val displayName: String,
    val fileName: String,
    val fileDate: String,
    val downloadUrl: String? = null,
    val gameVersions: List<String> = emptyList(),
    val releaseType: Int = 1
)

@Serializable
data class DownloadUrlResponse(
    val data: String
)
