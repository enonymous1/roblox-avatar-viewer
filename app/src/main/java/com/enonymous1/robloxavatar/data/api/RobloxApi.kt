package com.enonymous1.robloxavatar.data.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/** Users API: https://users.roblox.com/ */
interface UsersApi {
    @POST("v1/usernames/users")
    suspend fun usersByUsernames(@Body request: UsernamesRequest): Response<UsersResponse>

    @GET("v1/users/search")
    suspend fun searchUsers(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int,
    ): Response<UsersResponse>
}

/** Thumbnails API: https://thumbnails.roblox.com/
 *
 * [avatarData] takes an absolute URL because the avatar-3d response hands
 * back an `imageUrl` pointing at the JSON document; Retrofit uses it
 * verbatim when it is absolute. */
interface ThumbnailsApi {
    @GET("v1/users/avatar-3d")
    suspend fun avatar3d(@Query("userId") userId: Long): Response<ThumbnailsResponse>

    @GET
    suspend fun avatarData(@Url url: String): Response<AvatarDataDto>
}

object RobloxApiFactory {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun createUsersApi(client: OkHttpClient = createClient()): UsersApi =
        retrofit("https://users.roblox.com/", client).create(UsersApi::class.java)

    fun createThumbnailsApi(client: OkHttpClient = createClient()): ThumbnailsApi =
        retrofit("https://thumbnails.roblox.com/", client).create(ThumbnailsApi::class.java)
}
