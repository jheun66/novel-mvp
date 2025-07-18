package com.novel.mvp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_tokens")

class TokenStorage(private val context: Context) {
    
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }
    
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.tokenDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
        }
    }
    
    fun getAccessToken(): Flow<String?> {
        return context.tokenDataStore.data.map { preferences ->
            preferences[ACCESS_TOKEN_KEY]
        }
    }
    
    fun getRefreshToken(): Flow<String?> {
        return context.tokenDataStore.data.map { preferences ->
            preferences[REFRESH_TOKEN_KEY]
        }
    }
    
    suspend fun clearTokens() {
        context.tokenDataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
        }
    }
}