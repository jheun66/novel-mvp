package com.novel.domain.user

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.*

// Value Objects
data class UserId(val value: UUID = UUID.randomUUID())

data class Email(val value: String) {
    init {
        require(value.matches(emailRegex)) { "Invalid email format: $value" }
    }
    
    companion object {
        private val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    }
}

data class Username(val value: String) {
    init {
        require(value.length in 3..50) { "Username must be between 3 and 50 characters" }
        require(value.matches(usernameRegex)) { "Username can only contain letters, numbers, and underscores" }
    }
    
    companion object {
        private val usernameRegex = "^[a-zA-Z0-9_]+$".toRegex()
    }
}

enum class AuthProvider {
    LOCAL, GOOGLE, KAKAO
}

enum class SubscriptionStatus {
    FREE, PREMIUM, PREMIUM_TRIAL
}

data class PersonalityProfile(
    val traits: Map<PersonalityTrait, Int>, // 0-100 scale
    val preferredGenres: Set<StoryGenre>,
    val analysisVersion: String = "1.0",
    val lastUpdated: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
)

enum class PersonalityTrait {
    OPENNESS,
    CONSCIENTIOUSNESS,
    EXTROVERSION,
    AGREEABLENESS,
    NEUROTICISM,
    CREATIVITY,
    EMOTIONAL_DEPTH
}

enum class StoryGenre {
    HEALING, ROMANCE, MYSTERY, FANTASY, SLICE_OF_LIFE, ADVENTURE, COMEDY
}

// Domain Entity
class User(
    val id: UserId,
    var email: Email,
    var username: Username,
    var displayName: String,
    var profileImageUrl: String? = null,
    val authProvider: AuthProvider,
    var passwordHash: String? = null, // null for OAuth users
    var personalityProfile: PersonalityProfile? = null,
    var subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    var subscriptionExpiresAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    var updatedAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    var lastLoginAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    var isActive: Boolean = true,
    var dailyStoryCount: Int = 0,
    var totalStoriesGenerated: Int = 0,
    var refreshToken: String? = null
) {
    fun updateProfile(
        username: Username? = null,
        displayName: String? = null,
        profileImageUrl: String? = null
    ) {
        username?.let { this.username = it }
        displayName?.let { this.displayName = it }
        profileImageUrl?.let { this.profileImageUrl = it }
        this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    
    fun updatePersonalityProfile(profile: PersonalityProfile) {
        personalityProfile = profile
        this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    
    fun upgradeToPremium(expiresAt: LocalDateTime) {
        subscriptionStatus = SubscriptionStatus.PREMIUM
        subscriptionExpiresAt = expiresAt
        this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    
    fun checkSubscriptionExpiry(): Boolean {
        if (subscriptionStatus == SubscriptionStatus.PREMIUM) {
            subscriptionExpiresAt?.let {
                if (it < Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) {
                    subscriptionStatus = SubscriptionStatus.FREE
                    subscriptionExpiresAt = null
                    this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    return true
                }
            }
        }
        return false
    }
    
    fun canGenerateStory(): Boolean {
        return when (subscriptionStatus) {
            SubscriptionStatus.FREE -> dailyStoryCount < 3
            SubscriptionStatus.PREMIUM, SubscriptionStatus.PREMIUM_TRIAL -> true
        }
    }
    
    fun incrementStoryCount() {
        dailyStoryCount++
        totalStoriesGenerated++
        this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    
    fun resetDailyCount() {
        dailyStoryCount = 0
        this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    
    fun updateLastLogin() {
        lastLoginAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    
    fun updateRefreshToken(token: String?) {
        refreshToken = token
        this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
}

// Repository Interface
interface UserRepository {
    suspend fun save(user: User): User
    suspend fun findById(id: UserId): User?
    suspend fun findByEmail(email: Email): User?
    suspend fun findByUsername(username: Username): User?
    suspend fun existsByEmail(email: Email): Boolean
    suspend fun existsByUsername(username: Username): Boolean
    suspend fun update(user: User): User
    suspend fun delete(id: UserId)
    suspend fun findAllBySubscriptionStatus(status: SubscriptionStatus): List<User>
    suspend fun resetDailyStoryCounts()
}

// Domain Events
sealed class UserDomainEvent {
    abstract val userId: UserId
    abstract val occurredAt: LocalDateTime
    
    data class UserCreated(
        override val userId: UserId,
        val email: Email,
        val authProvider: AuthProvider,
        override val occurredAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ) : UserDomainEvent()
    
    data class PersonalityProfileUpdated(
        override val userId: UserId,
        val profile: PersonalityProfile,
        override val occurredAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ) : UserDomainEvent()
    
    data class SubscriptionUpgraded(
        override val userId: UserId,
        val newStatus: SubscriptionStatus,
        val expiresAt: LocalDateTime?,
        override val occurredAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ) : UserDomainEvent()
    
    data class StoryGenerated(
        override val userId: UserId,
        val storyId: UUID,
        override val occurredAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    ) : UserDomainEvent()
}
