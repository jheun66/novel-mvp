package com.novel.infrastructure.user

import com.novel.domain.user.*
import com.novel.model.Users
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

class UserRepositoryImpl : UserRepository {
    private val logger = LoggerFactory.getLogger(UserRepositoryImpl::class.java)
    
    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
    
    override suspend fun save(user: User): User = dbQuery {
        val inserted = Users.insert {
            it[id] = user.id.value
            it[email] = user.email.value
            it[username] = user.username.value
            it[displayName] = user.displayName
            it[profileImageUrl] = user.profileImageUrl
            it[authProvider] = user.authProvider.name
            it[passwordHash] = user.passwordHash
            it[personalityProfile] = user.personalityProfile?.let { profile ->
                serializePersonalityProfile(profile)
            }
            it[subscriptionStatus] = user.subscriptionStatus.name
            it[subscriptionExpiresAt] = user.subscriptionExpiresAt
            it[isActive] = user.isActive
            it[dailyStoryCount] = user.dailyStoryCount
            it[totalStoriesGenerated] = user.totalStoriesGenerated
            it[lastLoginAt] = user.lastLoginAt
            it[refreshToken] = user.refreshToken
            it[createdAt] = user.createdAt
            it[updatedAt] = user.updatedAt
        }
        
        user
    }
    
    override suspend fun findById(id: UserId): User? = dbQuery {
        Users.selectAll()
            .where { Users.id eq id.value }
            .singleOrNull()
            ?.toUser()
    }
    
    override suspend fun findByEmail(email: Email): User? = dbQuery {
        Users.selectAll()
            .where { Users.email eq email.value }
            .singleOrNull()
            ?.toUser()
    }
    
    override suspend fun findByUsername(username: Username): User? = dbQuery {
        Users.selectAll()
            .where { Users.username eq username.value }
            .singleOrNull()
            ?.toUser()
    }
    
    override suspend fun existsByEmail(email: Email): Boolean = dbQuery {
        Users.selectAll()
            .where { Users.email eq email.value }
            .count() > 0
    }
    
    override suspend fun existsByUsername(username: Username): Boolean = dbQuery {
        Users.selectAll()
            .where { Users.username eq username.value }
            .count() > 0
    }
    
    override suspend fun update(user: User): User = dbQuery {
        Users.update({ Users.id eq user.id.value }) {
            it[email] = user.email.value
            it[username] = user.username.value
            it[displayName] = user.displayName
            it[profileImageUrl] = user.profileImageUrl
            it[authProvider] = user.authProvider.name
            it[passwordHash] = user.passwordHash
            it[personalityProfile] = user.personalityProfile?.let { profile ->
                serializePersonalityProfile(profile)
            }
            it[subscriptionStatus] = user.subscriptionStatus.name
            it[subscriptionExpiresAt] = user.subscriptionExpiresAt
            it[isActive] = user.isActive
            it[dailyStoryCount] = user.dailyStoryCount
            it[totalStoriesGenerated] = user.totalStoriesGenerated
            it[lastLoginAt] = user.lastLoginAt
            it[refreshToken] = user.refreshToken
            it[updatedAt] = user.updatedAt
        }
        
        user
    }
    
    override suspend fun delete(id: UserId) = dbQuery {
        Users.deleteWhere { Users.id eq id.value }
        Unit
    }
    
    override suspend fun findAllBySubscriptionStatus(status: SubscriptionStatus): List<User> = dbQuery {
        Users.selectAll()
            .where { Users.subscriptionStatus eq status.name }
            .map { it.toUser() }
    }
    
    override suspend fun resetDailyStoryCounts() = dbQuery {
        Users.update {
            it[dailyStoryCount] = 0
            it[updatedAt] = CurrentDateTime
        }
        Unit
    }
    
    private fun ResultRow.toUser(): User {
        val personalityProfileJson = this[Users.personalityProfile]
        val profile = personalityProfileJson?.let { deserializePersonalityProfile(it) }
        
        return User(
            id = UserId(this[Users.id].value),
            email = Email(this[Users.email]),
            username = Username(this[Users.username]),
            displayName = this[Users.displayName],
            profileImageUrl = this[Users.profileImageUrl],
            authProvider = AuthProvider.valueOf(this[Users.authProvider]),
            passwordHash = this[Users.passwordHash],
            personalityProfile = profile,
            subscriptionStatus = SubscriptionStatus.valueOf(this[Users.subscriptionStatus]),
            subscriptionExpiresAt = this[Users.subscriptionExpiresAt],
            createdAt = this[Users.createdAt],
            updatedAt = this[Users.updatedAt],
            lastLoginAt = this[Users.lastLoginAt],
            isActive = this[Users.isActive],
            dailyStoryCount = this[Users.dailyStoryCount],
            totalStoriesGenerated = this[Users.totalStoriesGenerated],
            refreshToken = this[Users.refreshToken]
        )
    }
    
    private fun serializePersonalityProfile(profile: PersonalityProfile): String {
        // Simple JSON serialization
        // In production, use proper JSON library like kotlinx.serialization
        val traits = profile.traits.entries.joinToString(",") { 
            "\"${it.key.name}\":${it.value}" 
        }
        val genres = profile.preferredGenres.joinToString(",") { 
            "\"${it.name}\"" 
        }
        
        return """{
            "traits":{$traits},
            "preferredGenres":[$genres],
            "analysisVersion":"${profile.analysisVersion}",
            "lastUpdated":"${profile.lastUpdated}"
        }"""
    }
    
    private fun deserializePersonalityProfile(json: String): PersonalityProfile {
        // Simple JSON deserialization
        // In production, use proper JSON library
        try {
            // This is a simplified implementation
            // Parse traits
            val traitsMap = mutableMapOf<PersonalityTrait, Int>()
            PersonalityTrait.values().forEach { trait ->
                val regex = "\"${trait.name}\":(\\d+)".toRegex()
                regex.find(json)?.groups?.get(1)?.value?.toIntOrNull()?.let {
                    traitsMap[trait] = it
                }
            }
            
            // Parse genres
            val genres = mutableSetOf<StoryGenre>()
            StoryGenre.values().forEach { genre ->
                if (json.contains("\"${genre.name}\"")) {
                    genres.add(genre)
                }
            }
            
            // Parse version
            val versionRegex = "\"analysisVersion\":\"([^\"]+)\"".toRegex()
            val version = versionRegex.find(json)?.groups?.get(1)?.value ?: "1.0"
            
            // Parse date
            val dateRegex = "\"lastUpdated\":\"([^\"]+)\"".toRegex()
            val dateStr = dateRegex.find(json)?.groups?.get(1)?.value
            val lastUpdated = dateStr?.let { LocalDateTime.parse(it) } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            
            return PersonalityProfile(
                traits = traitsMap,
                preferredGenres = genres,
                analysisVersion = version,
                lastUpdated = lastUpdated
            )
        } catch (e: Exception) {
            logger.error("Failed to deserialize personality profile", e)
            return PersonalityProfile(
                traits = emptyMap(),
                preferredGenres = emptySet()
            )
        }
    }
}
