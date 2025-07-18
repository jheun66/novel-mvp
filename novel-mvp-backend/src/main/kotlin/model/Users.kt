package com.novel.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val username = varchar("username", 100).uniqueIndex()
    val displayName = varchar("display_name", 255)
    val profileImageUrl = varchar("profile_image_url", 500).nullable()
    val authProvider = varchar("auth_provider", 50) // LOCAL, GOOGLE, KAKAO
    val passwordHash = varchar("password_hash", 255).nullable()
    val personalityProfile = text("personality_profile").nullable() // JSON
    val subscriptionStatus = varchar("subscription_status", 50).default("FREE")
    val subscriptionExpiresAt = datetime("subscription_expires_at").nullable()
    val isActive = bool("is_active").default(true)
    val dailyStoryCount = integer("daily_story_count").default(0)
    val totalStoriesGenerated = integer("total_stories_generated").default(0)
    val lastLoginAt = datetime("last_login_at").defaultExpression(CurrentDateTime)
    val refreshToken = text("refresh_token").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}
