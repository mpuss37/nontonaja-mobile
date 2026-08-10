package com.example.nonton_aja.data

import com.google.gson.annotations.SerializedName

data class SearchResponse(
    @SerializedName("results") val results: List<SearchItem>,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("has_more") val hasMore: Boolean = false
)

data class SearchItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("year") val year: String = "",
    @SerializedName("image") val image: String = "",
    @SerializedName("type") val type: String = "movie",
    @SerializedName("source") val source: String,
    @SerializedName("sources") val sources: Map<String, String> = emptyMap()
)

data class StreamResponse(
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("quality") val quality: Int = 720,
    @SerializedName("subtitles") val subtitles: List<Subtitle> = emptyList(),
    @SerializedName("headers") val headers: Map<String, String> = emptyMap()
)

data class SubtitleResponse(
    @SerializedName("subtitles") val subtitles: List<Subtitle> = emptyList()
)

data class Subtitle(
    @SerializedName("url") val url: String,
    @SerializedName("language") val language: String = "id",
    @SerializedName("label") val label: String = "Indonesian"
)

data class IDLIXRequest(
    @SerializedName("content_id") val contentId: String,
    @SerializedName("content_type") val contentType: String = "movie",
    @SerializedName("title") val title: String = ""
)

data class IDLIXJobResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("status") val status: String = "waiting",
    @SerializedName("estimated_seconds") val estimatedSeconds: Int = 15
)

data class IDLIXStatusResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("status") val status: String,
    @SerializedName("elapsed_seconds") val elapsedSeconds: Double = 0.0,
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("subtitles") val subtitles: List<Subtitle> = emptyList(),
    @SerializedName("error") val error: String? = null
)

data class HomeResponse(
    @SerializedName("hero") val hero: SearchItem? = null,
    @SerializedName("trending") val trending: List<SearchItem> = emptyList(),
    @SerializedName("popular_movies") val popularMovies: List<SearchItem> = emptyList(),
    @SerializedName("popular_tv") val popularTv: List<SearchItem> = emptyList(),
    @SerializedName("new_releases") val newReleases: List<SearchItem> = emptyList(),
    @SerializedName("top_rated") val topRated: List<SearchItem> = emptyList()
)
