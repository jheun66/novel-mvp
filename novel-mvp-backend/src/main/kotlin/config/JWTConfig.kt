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

            return JWTConfig(
                realm = config.property("jwt.realm").getString(),
                secret = config.property("jwt.secret").getString(),
                issuer = config.property("jwt.issuer").getString(),
                audience = config.property("jwt.audience").getString(),
                accessTokenExpiry = Duration.ofMillis(
                    config.property("jwt.accessTokenExpiry").getString().toLong()
                ),
                refreshTokenExpiry = Duration.ofMillis(
                    config.property("jwt.refreshTokenExpiry").getString().toLong()
                )
            )
        }
    }
}
