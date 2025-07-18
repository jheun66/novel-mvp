package com.novel.auth

import com.novel.services.JWTService
import io.ktor.websocket.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class WebSocketAuth : KoinComponent {
    private val jwtService: JWTService by inject()
    private val logger = LoggerFactory.getLogger(WebSocketAuth::class.java)
    
    suspend fun authenticateWebSocket(
        session: DefaultWebSocketSession,
        token: String?
    ): AuthenticatedUser? {
        if (token.isNullOrBlank()) {
            logger.warn("WebSocket connection attempt without token")
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
            return null
        }
        
        // Remove "Bearer " prefix if present
        val cleanToken = token.removePrefix("Bearer ").trim()
        
        // Verify JWT token
        val decodedJWT = jwtService.verifyToken(cleanToken)
        if (decodedJWT == null) {
            logger.warn("Invalid JWT token for WebSocket connection")
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid authentication token"))
            return null
        }
        
        // Check token type (must be access token)
        val tokenType = decodedJWT.getClaim("type").asString()
        if (tokenType != "access") {
            logger.warn("Wrong token type for WebSocket: $tokenType")
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token type"))
            return null
        }
        
        // Extract user information
        val userId = decodedJWT.getClaim("userId").asString()
        val email = decodedJWT.getClaim("email").asString()
        
        if (userId.isNullOrBlank() || email.isNullOrBlank()) {
            logger.error("Missing user information in token")
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token payload"))
            return null
        }
        
        logger.info("WebSocket authenticated for user: $userId ($email)")
        
        return AuthenticatedUser(
            userId = userId,
            email = email
        )
    }
}

data class AuthenticatedUser(
    val userId: String,
    val email: String
)
