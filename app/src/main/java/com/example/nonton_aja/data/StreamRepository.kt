package com.example.nonton_aja.data

import android.util.Log

class StreamRepository(
    private val api: ApiService = ApiClient.api,
    private val searchRepo: SearchRepository = SearchRepository()
) {

    suspend fun getStream(source: String, mediaId: String): StreamResponse {
        return api.getStream(source, mediaId)
    }

    suspend fun getSubtitles(source: String, mediaId: String): SubtitleResponse {
        return api.getSubtitles(source, mediaId)
    }

    suspend fun startIdlixStream(request: IDLIXRequest): IDLIXJobResponse {
        return api.startIDLIXStream(request)
    }

    suspend fun getJobStatus(jobId: String): IDLIXStatusResponse {
        return api.getJobStatus(jobId)
    }

    suspend fun searchFilm(title: String, source: String): String? {
        // Strip tahun dari title untuk search lebih akurat
        val cleanTitle = title.replace(Regex("\\(\\d{4}\\)"), "").replace(Regex("\\d{4}$"), "").trim()
        Log.d("StreamRepo", "searchFilm: '$cleanTitle' on $source")

        val response = searchRepo.search(cleanTitle, 1, 10, source)
        Log.d("StreamRepo", "Found ${response.results.size} results on $source")

        val normalizedQuery = cleanTitle.lowercase().trim()
        val match = response.results
            .filter { it.source == source }
            .minByOrNull {
                val t = it.title.lowercase().trim()
                if (t == normalizedQuery) 0
                else t.indexOf(normalizedQuery).let { idx -> if (idx >= 0) idx else 999 }
            }

        Log.d("StreamRepo", "Best match: ${match?.id} - ${match?.title}")
        return match?.id
    }
}
