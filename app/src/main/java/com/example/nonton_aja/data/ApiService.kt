package com.example.nonton_aja.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/home")
    suspend fun getHome(): HomeResponse

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 5,
        @Query("source") source: String? = null
    ): SearchResponse

    @GET("api/stream/{source}/{mediaId}")
    suspend fun getStream(
        @Path("source") source: String,
        @Path("mediaId", encoded = true) mediaId: String
    ): StreamResponse

    @GET("api/subtitles/{source}/{mediaId}")
    suspend fun getSubtitles(
        @Path("source") source: String,
        @Path("mediaId", encoded = true) mediaId: String
    ): SubtitleResponse

    @POST("api/stream/idlix")
    suspend fun startIDLIXStream(@Body request: IDLIXRequest): IDLIXJobResponse

    @GET("api/status/{jobId}")
    suspend fun getJobStatus(@Path("jobId") jobId: String): IDLIXStatusResponse
}

object ApiClient {
    private const val BASE_URL = "http://192.168.18.10:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
