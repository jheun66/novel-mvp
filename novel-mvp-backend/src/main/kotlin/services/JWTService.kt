package com.novel.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.Serializable
import java.time.Duration
import java.util.*

@Serializable
data class TokenPair(val accessToken: String, val refreshToken: String)

class JWTService(
    val realm: String,
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val accessTokenExpiry: Duration,
    private val refreshTokenExpiry: Duration
) {
    val verifier: JWTVerifier = JWT.require(Algorithm.HMAC256(secret))
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    fun verifyToken(token: String): DecodedJWT? {
        return try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            null
        }
    }

    fun generateTokenPair(userId: String, email: String): TokenPair {
        val now = Date()

        val accessToken = JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "access")
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + accessTokenExpiry.toMillis()))
            .sign(Algorithm.HMAC256(secret))

        val refreshToken = JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("type", "refresh")
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + refreshTokenExpiry.toMillis()))
            .sign(Algorithm.HMAC256(secret))

        return TokenPair(accessToken, refreshToken)
    }

    fun validateAccessToken(payload: Payload): Any? {
        val userId = payload.getClaim("userId").asString()
        val tokenType = payload.getClaim("type").asString()

        return if (tokenType == "access" && userId != null) {
            if (payload.audience.contains(audience)) {
                JWTPrincipal(payload)
            } else null
        } else null
    }

    fun validateRefreshToken(payload: Payload): Any? {
        val tokenType = payload.getClaim("type").asString()
        return if (tokenType == "refresh") {
            JWTPrincipal(payload)
        } else null
    }
}