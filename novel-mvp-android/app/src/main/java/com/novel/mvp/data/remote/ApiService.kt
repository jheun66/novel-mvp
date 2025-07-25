package com.novel.mvp.data.remote

import com.novel.mvp.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class ApiService(private val client: HttpClient) {
    
    companion object {
        //private const val BASE_URL = "http://10.0.2.2:8080/api/v1"
        private const val BASE_URL = "http://192.168.219.114:8080/api/v1"
    }
    
    suspend fun register(request: CreateUserRequest): AuthResponse {
        return client.post("$BASE_URL/users/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun login(request: LoginRequest): AuthResponse {
        return client.post("$BASE_URL/users/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun oAuthLogin(request: OAuthLoginRequest): AuthResponse {
        return client.post("$BASE_URL/users/oauth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        return client.post("$BASE_URL/users/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun getUser(token: String): UserResponse {
        return client.get("$BASE_URL/users/me") {
            bearerAuth(token)
        }.body()
    }
    
    suspend fun updateProfile(token: String, request: UpdateUserProfileRequest): UserResponse {
        return client.patch("$BASE_URL/users/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun updatePersonality(token: String, request: PersonalityTestRequest): UserResponse {
        return client.post("$BASE_URL/users/me/personality") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
    
    suspend fun logout(token: String): SuccessResponse {
        return client.post("$BASE_URL/users/logout") {
            bearerAuth(token)
        }.body()
    }
    
    suspend fun canGenerateStory(token: String): Boolean {
        return client.get("$BASE_URL/users/me/can-generate-story") {
            bearerAuth(token)
        }.body<Map<String, Boolean>>()["canGenerate"] ?: false
    }
}