package com.example.nonton_aja.data

class SearchRepository(private val api: ApiService = ApiClient.api) {

    suspend fun search(query: String, page: Int = 1, limit: Int = 5, source: String? = null): SearchResponse {
        return api.search(query, page, limit, source)
    }
}
