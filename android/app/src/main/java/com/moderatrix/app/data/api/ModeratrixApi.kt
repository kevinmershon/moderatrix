package com.moderatrix.app.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface ModeratrixApi {
    @GET("health")
    suspend fun health(): String

    @GET("config")
    suspend fun getConfig(): ApiConfig

    @PUT("config")
    suspend fun putConfig(@Body config: ApiConfig)

    @retrofit2.http.POST("sync")
    suspend fun sync(@Body request: ApiSyncRequest): ApiSyncResponse

    @GET("day")
    suspend fun getDay(@Query("date") date: String): ApiDayLog

    @GET("last-recorded")
    suspend fun lastRecorded(): ApiLastRecorded
}
