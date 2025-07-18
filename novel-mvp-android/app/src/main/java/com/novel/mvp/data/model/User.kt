package com.novel.mvp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?,
    val authProvider: String,
    val personalityProfile: PersonalityProfileResponse?,
    val subscriptionStatus: String,
    val subscriptionExpiresAt: String?,
    val createdAt: String,
    val canGenerateStory: Boolean,
    val dailyStoryCount: Int,
    val totalStoriesGenerated: Int
)

@Serializable
data class PersonalityProfileResponse(
    val traits: Map<String, Int>,
    val preferredGenres: Set<String>,
    val lastUpdated: String
)

@Serializable
data class AuthResponse(
    val user: UserResponse,
    val tokens: TokensResponse
)

@Serializable
data class TokensResponse(
    val accessToken: String,
    val refreshToken: String
)