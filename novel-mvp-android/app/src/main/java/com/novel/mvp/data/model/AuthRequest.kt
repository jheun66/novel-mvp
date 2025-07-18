package com.novel.mvp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val email: String,
    val username: String,
    val displayName: String,
    val password: String? = null,
    val authProvider: String = "LOCAL",
    val profileImageUrl: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class OAuthLoginRequest(
    val provider: String,
    val accessToken: String,
    val email: String,
    val displayName: String,
    val profileImageUrl: String? = null
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class UpdateUserProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val profileImageUrl: String? = null
)

@Serializable
data class PersonalityTestRequest(
    val responses: Map<String, Int>,
    val preferredGenres: Set<String>
)