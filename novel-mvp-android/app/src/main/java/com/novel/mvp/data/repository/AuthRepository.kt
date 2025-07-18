package com.novel.mvp.data.repository

import com.novel.mvp.data.local.TokenStorage
import com.novel.mvp.data.model.*
import com.novel.mvp.data.remote.ApiService
import io.ktor.client.plugins.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class AuthRepository(
    private val apiService: ApiService,
    private val tokenStorage: TokenStorage
) {
    
    suspend fun register(request: CreateUserRequest): Flow<ApiResult<AuthResponse>> = flow {
        emit(ApiResult.Loading())
        try {
            val response = apiService.register(request)
            tokenStorage.saveTokens(response.tokens.accessToken, response.tokens.refreshToken)
            emit(ApiResult.Success(response))
        } catch (e: ClientRequestException) {
            emit(ApiResult.Error(ErrorResponse("REGISTRATION_FAILED", e.message ?: "Registration failed")))
        } catch (e: Exception) {
            emit(ApiResult.Error(ErrorResponse("NETWORK_ERROR", e.message ?: "Network error")))
        }
    }
    
    suspend fun login(request: LoginRequest): Flow<ApiResult<AuthResponse>> = flow {
        emit(ApiResult.Loading())
        try {
            val response = apiService.login(request)
            tokenStorage.saveTokens(response.tokens.accessToken, response.tokens.refreshToken)
            emit(ApiResult.Success(response))
        } catch (e: ClientRequestException) {
            emit(ApiResult.Error(ErrorResponse("LOGIN_FAILED", e.message ?: "Login failed")))
        } catch (e: Exception) {
            emit(ApiResult.Error(ErrorResponse("NETWORK_ERROR", e.message ?: "Network error")))
        }
    }
    
    suspend fun oAuthLogin(request: OAuthLoginRequest): Flow<ApiResult<AuthResponse>> = flow {
        emit(ApiResult.Loading())
        try {
            val response = apiService.oAuthLogin(request)
            tokenStorage.saveTokens(response.tokens.accessToken, response.tokens.refreshToken)
            emit(ApiResult.Success(response))
        } catch (e: ClientRequestException) {
            emit(ApiResult.Error(ErrorResponse("OAUTH_LOGIN_FAILED", e.message ?: "OAuth login failed")))
        } catch (e: Exception) {
            emit(ApiResult.Error(ErrorResponse("NETWORK_ERROR", e.message ?: "Network error")))
        }
    }
    
    suspend fun logout(): Flow<ApiResult<SuccessResponse>> = flow {
        emit(ApiResult.Loading())
        try {
            val accessToken = tokenStorage.getAccessToken().first()
            if (accessToken != null) {
                val response = apiService.logout(accessToken)
                tokenStorage.clearTokens()
                emit(ApiResult.Success(response))
            } else {
                emit(ApiResult.Error(ErrorResponse("NO_TOKEN", "No access token found")))
            }
        } catch (e: Exception) {
            tokenStorage.clearTokens()
            emit(ApiResult.Success(SuccessResponse("Logged out locally")))
        }
    }
    
    suspend fun getCurrentUser(): Flow<ApiResult<UserResponse>> = flow {
        emit(ApiResult.Loading())
        try {
            val accessToken = tokenStorage.getAccessToken().first()
            if (accessToken != null) {
                val response = apiService.getUser(accessToken)
                emit(ApiResult.Success(response))
            } else {
                emit(ApiResult.Error(ErrorResponse("NO_TOKEN", "No access token found")))
            }
        } catch (e: ClientRequestException) {
            emit(ApiResult.Error(ErrorResponse("GET_USER_FAILED", e.message ?: "Failed to get user")))
        } catch (e: Exception) {
            emit(ApiResult.Error(ErrorResponse("NETWORK_ERROR", e.message ?: "Network error")))
        }
    }
    
    fun isLoggedIn(): Flow<Boolean> = flow {
        val accessToken = tokenStorage.getAccessToken().first()
        emit(!accessToken.isNullOrBlank())
    }
    
    suspend fun refreshToken(): Flow<ApiResult<AuthResponse>> = flow {
        emit(ApiResult.Loading())
        try {
            val refreshToken = tokenStorage.getRefreshToken().first()
            if (refreshToken != null) {
                val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))
                tokenStorage.saveTokens(response.tokens.accessToken, response.tokens.refreshToken)
                emit(ApiResult.Success(response))
            } else {
                emit(ApiResult.Error(ErrorResponse("NO_REFRESH_TOKEN", "No refresh token found")))
            }
        } catch (e: Exception) {
            tokenStorage.clearTokens()
            emit(ApiResult.Error(ErrorResponse("REFRESH_FAILED", e.message ?: "Token refresh failed")))
        }
    }
}