package com.novel

import com.novel.config.OAuthConfig
import com.novel.services.JWTService
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant

val CorrelationIdKey = AttributeKey<String>("CorrelationId")

// Extension function to get current correlation ID
fun ApplicationCall.correlationId(): String =
    attributes.getOrNull(CorrelationIdKey) ?: "N/A"

@Serializable
data class ErrorResponse(
    val error: ErrorDetails,
    val correlationId: String,
    val timestamp: String = Instant.now().toString()
)

@Serializable
data class ErrorDetails(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
    val path: String? = null
)

fun Application.configureSecurity() {
    // Koin에서 의존성 주입 - 완전한 타입 안전성
    val httpClient by inject<HttpClient>()
    val jwtService by inject<JWTService>()
    val oauthConfig by inject<OAuthConfig>()

    val redirects = mutableMapOf<String, String>()

    authentication {
        // Google OAuth 설정 - 설정 객체 사용
        oauth("auth-oauth-google") {
            urlProvider = { oauthConfig.google.callbackUrl }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = oauthConfig.google.clientId,
                    clientSecret = oauthConfig.google.clientSecret,
                    defaultScopes = listOf(
                        "https://www.googleapis.com/auth/userinfo.profile",
                        "https://www.googleapis.com/auth/userinfo.email"
                    ),
                    extraAuthParameters = listOf(
                        "access_type" to "offline",
                        "prompt" to "consent"
                    ),
                    onStateCreated = { call, state ->
                        call.request.queryParameters["redirectUrl"]?.let {
                            redirects[state] = it
                        }
                    }
                )
            }
            client = httpClient
        }

        // Access Token 검증 - JWTService 사용
        jwt("auth-jwt") {
            realm = jwtService.realm
            verifier { jwtService.verifier }
            validate { credential -> jwtService.validateAccessToken(credential.payload) }
            challenge { _, _ ->
                val correlationId = call.correlationId()

                val errorResponse = ErrorResponse(
                    error = ErrorDetails(
                        code = when {
                            call.request.headers["Authorization"] == null -> "TOKEN_MISSING"
                            call.request.headers["Authorization"]?.startsWith("Bearer ") == false -> "TOKEN_FORMAT_INVALID"
                            else -> "TOKEN_INVALID"
                        },
                        message = when {
                            call.request.headers["Authorization"] == null -> "Authorization header is required"
                            call.request.headers["Authorization"]?.startsWith("Bearer ") == false -> "Authorization header must start with 'Bearer '"
                            else -> "Token is not valid or has expired"
                        },
                        path = call.request.path()
                    ),
                    correlationId = correlationId
                )

                call.respond(HttpStatusCode.Unauthorized, errorResponse)
            }
        }

        // Refresh Token 검증 - JWTService 사용
        jwt("auth-jwt-refresh") {
            realm = jwtService.realm
            verifier { jwtService.verifier }
            validate { credential -> jwtService.validateRefreshToken(credential.payload) }
        }
    }
}
