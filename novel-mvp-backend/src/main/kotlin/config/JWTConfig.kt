package com.novel.config

import io.ktor.server.application.*
import java.time.Duration

data class JWTConfig(
    val realm: String,
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTokenExpiry: Duration,
    val refreshTokenExpiry: Duration
) {
    companion object {
        fun fromApplication(application: Application): JWTConfig {
            val config = application.environment.config
            val isDevelopment = config.property("ktor.development").getString().toBoolean()
            
            return JWTConfig(
                realm = config.property("jwt.realm").getString(),
                secret = config.property("jwt.secret").getString(), // TODO : Only use for development
                issuer = config.property("jwt.domain").getString(),
                audience = config.property("jwt.audience").getString(),
                accessTokenExpiry = if (isDevelopment) Duration.ofDays(7) else Duration.ofMinutes(15),
                refreshTokenExpiry = Duration.ofDays(30)
            )
        }
    }
}
