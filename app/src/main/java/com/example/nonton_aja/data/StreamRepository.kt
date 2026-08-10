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
        val cleanTitle = title.replace(Regex("\\(\\d{4}\\)"), "").trim()
        Log.d("StreamRepo", "searchFilm: '$cleanTitle' (year=$year) on $source")

        // Cari tanpa tahun dulu — lebih banyak hasil
        var response = searchRepo.search(cleanTitle, 1, 20, source)
        Log.d("StreamRepo", "Search without year: ${response.results.size} results")

        // Kalau 0 hasil, coba cari kata kunci penting aja (2 kata pertama)
        if (response.results.none { it.source == source }) {
            val shortQuery = cleanTitle.split("\\s+".toRegex()).take(2).joinToString(" ")
            Log.d("StreamRepo", "Fallback search: '$shortQuery'")
            response = searchRepo.search(shortQuery, 1, 20, source)
        }

        val normalizedQuery = cleanTitle.lowercase().trim()

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
                val yearBonus = if (year != null && item.year == year) -5 else 0
                titleScore + yearBonus
            }

        Log.d("StreamRepo", "Best match: ${match?.id} - ${match?.title} (${match?.year})")
        return match?.id
    }
}
