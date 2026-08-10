package com.example.nonton_aja.data

class HomeRepository(private val api: ApiService = ApiClient.api) {

    suspend fun getHome(): HomeResponse {
        return api.getHome()
    }
}
