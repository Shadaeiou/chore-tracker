package com.chore.tracker.data

import com.chore.tracker.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChoreApi {
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): AuthResponse

    @GET("api/household")
    suspend fun household(): HouseholdResponse

    @POST("api/invites")
    suspend fun createInvite(): Invite

    @GET("api/areas")
    suspend fun areas(): List<Area>

    @POST("api/areas")
    suspend fun createArea(@Body req: CreateAreaRequest): Area

    @DELETE("api/areas/{id}")
    suspend fun deleteArea(@Path("id") id: String)

    @GET("api/tasks")
    suspend fun tasks(): List<Task>

    @POST("api/tasks")
    suspend fun createTask(@Body req: CreateTaskRequest): Task

    @PATCH("api/tasks/{id}")
    suspend fun patchTask(@Path("id") id: String, @Body req: PatchTaskRequest)

    @POST("api/tasks/{id}/complete")
    suspend fun completeTask(@Path("id") id: String)

    @DELETE("api/tasks/{id}/completions/last")
    suspend fun undoLastCompletion(@Path("id") id: String)

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String)

    @GET("api/activity")
    suspend fun activity(
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int? = null,
    ): List<ActivityEntry>

    @GET("api/household/workload")
    suspend fun workload(): List<WorkloadEntry>

    @POST("api/device-tokens")
    suspend fun registerDeviceToken(@Body req: DeviceTokenRequest)

    @DELETE("api/device-tokens/{token}")
    suspend fun deleteDeviceToken(@Path("token") token: String)
}

object ApiFactory {
    private val json = Json { ignoreUnknownKeys = true }

    fun create(session: Session, baseUrl: String = BuildConfig.API_BASE_URL): ChoreApi {
        val auth = okhttp3.Interceptor { chain ->
            val token = runBlocking { session.token() }
            val req = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else chain.request()
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChoreApi::class.java)
    }
}
