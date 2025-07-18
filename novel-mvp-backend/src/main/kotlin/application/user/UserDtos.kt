package com.novel.application.user

import com.novel.domain.user.*
import kotlinx.serialization.Serializable

// Request DTOs
@Serializable
data class CreateUserRequest(
    val email: String,
    val username: String,
    val displayName: String,
    val password: String? = null, // null for OAuth users
    val authProvider: String = "LOCAL",
    val profileImageUrl: String? = null
)

@Serializable
data class UpdateUserProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val profileImageUrl: String? = null
)

@Serializable
data class PersonalityTestRequest(
    val responses: Map<String, Int>, // question_id to response value
    val preferredGenres: Set<String>
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class OAuthLoginRequest(
    val provider: String,
    val accessToken: String,
    val email: String,
    val displayName: String,
    val profileImageUrl: String? = null
)

// Response DTOs
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

// Mappers
fun User.toResponse() = UserResponse(
    id = id.value.toString(),
    email = email.value,
    username = username.value,
    displayName = displayName,
    profileImageUrl = profileImageUrl,
    authProvider = authProvider.name,
    personalityProfile = personalityProfile?.toResponse(),
    subscriptionStatus = subscriptionStatus.name,
    subscriptionExpiresAt = subscriptionExpiresAt?.toString(),
    createdAt = createdAt.toString(),
    canGenerateStory = canGenerateStory(),
    dailyStoryCount = dailyStoryCount,
    totalStoriesGenerated = totalStoriesGenerated
)

fun PersonalityProfile.toResponse() = PersonalityProfileResponse(
    traits = traits.mapKeys { it.key.name },
    preferredGenres = preferredGenres.map { it.name }.toSet(),
    lastUpdated = lastUpdated.toString()
)

// Exceptions
class UserNotFoundException(message: String) : Exception(message)
class UserAlreadyExistsException(message: String) : Exception(message)
class InvalidCredentialsException(message: String) : Exception(message)
class PaymentFailedException(message: String) : Exception(message)
