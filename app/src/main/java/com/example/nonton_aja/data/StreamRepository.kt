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

    suspend fun searchFilm(title: String, source: String, year: String? = null): String? {
        // Gunakan judul asli (tanpa strip tahun) supaya search lebih akurat
        val cleanTitle = title.replace(Regex("\\(\\d{4}\\)"), "").trim()
        Log.d("StreamRepo", "searchFilm: '$cleanTitle' (year=$year) on $source")

        val response = searchRepo.search(cleanTitle, 1, 20, source)
        Log.d("StreamRepo", "Found ${response.results.size} results on $source")

        val normalizedQuery = cleanTitle.lowercase().trim()

        // Score: exact title match = 0, contains = index, year match bonus
        val match = response.results
            .filter { it.source == source }
            .minByOrNull { item ->
                val t = item.title.lowercase().trim()
                val titleScore = when {
                    t == normalizedQuery -> 0
                    t.contains(normalizedQuery) -> 1
                    normalizedQuery.contains(t) -> 2
                    else -> {
                        val words = normalizedQuery.split("\\s+".toRegex())
                        val matched = words.count { w -> t.contains(w) }
                        10 - matched
                    }
                }
                // Bonus: kalau tahun cocok, score lebih rendah (lebih baik)
                val yearBonus = if (year != null && item.year == year) -5 else 0
                titleScore + yearBonus
            }

        Log.d("StreamRepo", "Best match: ${match?.id} - ${match?.title} (${match?.year})")
        return match?.id
    }
}
