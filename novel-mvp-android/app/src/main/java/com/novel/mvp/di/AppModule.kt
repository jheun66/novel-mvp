package com.novel.mvp.di

import android.content.Context
import com.novel.mvp.data.local.TokenStorage
import com.novel.mvp.data.remote.ApiService
import com.novel.mvp.data.remote.HttpClientFactory
import com.novel.mvp.data.repository.AuthRepository
import com.novel.mvp.presentation.login.LoginViewModel
import com.novel.mvp.utils.GoogleCredentialManager
import io.ktor.client.*

object AppModule {
    
    fun provideHttpClient(): HttpClient {
        return HttpClientFactory.create()
    }
    
    fun provideApiService(httpClient: HttpClient): ApiService {
        return ApiService(httpClient)
    }
    
    fun provideTokenStorage(context: Context): TokenStorage {
        return TokenStorage(context)
    }
    
    fun provideAuthRepository(
        apiService: ApiService,
        tokenStorage: TokenStorage
    ): AuthRepository {
        return AuthRepository(apiService, tokenStorage)
    }
    
    fun provideGoogleCredentialManager(context: Context): GoogleCredentialManager {
        return GoogleCredentialManager(context)
    }
    
    fun provideLoginViewModel(authRepository: AuthRepository): LoginViewModel {
        return LoginViewModel(authRepository)
    }
}