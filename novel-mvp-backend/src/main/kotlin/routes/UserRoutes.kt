package com.novel.routes

import com.novel.application.user.*
import com.novel.middleware.OAuthTokenValidator
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

// Common OAuth user info
data class OAuthUserInfo(
    val email: String,
    val name: String,
    val picture: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val details: Map<String, String>? = null
)

@Serializable
data class SuccessResponse(
    val message: String
)

fun Route.userRoutes() {
    val logger = LoggerFactory.getLogger("UserRoutes")
    
    // Inject use cases
    val createUserUseCase by inject<CreateUserUseCase>()
    val loginUseCase by inject<LoginUseCase>()
    val oAuthLoginUseCase by inject<OAuthLoginUseCase>()
    val refreshTokenUseCase by inject<RefreshTokenUseCase>()
    val getUserUseCase by inject<GetUserUseCase>()
    val updateUserProfileUseCase by inject<UpdateUserProfileUseCase>()
    val updatePersonalityProfileUseCase by inject<UpdatePersonalityProfileUseCase>()
    val upgradeSubscriptionUseCase by inject<UpgradeSubscriptionUseCase>()
    val checkStoryGenerationEligibilityUseCase by inject<CheckStoryGenerationEligibilityUseCase>()
    val logoutUseCase by inject<LogoutUseCase>()
    val oAuthTokenValidator by inject<OAuthTokenValidator>()

    route("/api/v1/users") {
        // Public endpoints
        post("/register") {
            try {
                val request = call.receive<CreateUserRequest>()
                val response = createUserUseCase.execute(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: UserAlreadyExistsException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("USER_EXISTS", e.message ?: "User already exists")
                )
            } catch (e: Exception) {
                logger.error("Registration failed", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("REGISTRATION_FAILED", e.message ?: "Registration failed")
                )
            }
        }
        
        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                val response = loginUseCase.execute(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: InvalidCredentialsException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_CREDENTIALS", e.message ?: "Invalid credentials")
                )
            } catch (e: Exception) {
                logger.error("Login failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("LOGIN_FAILED", e.message ?: "Login failed")
                )
            }
        }

        authenticate("auth-oauth-google") {
            get("/login/google") {
                // 이 블록은 실제로는 실행되지 않고,
                // Ktor가 자동으로 사용자를 Google의 인증 페이지로 리다이렉트합니다.
                // 성공적으로 인증되면, Google은 callbackUrl로 다시 리다이렉트합니다.
            }

            get("/oauth/callback/google") {
                try {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                    if (principal == null) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("OAUTH_FAILED", "OAuth login failed")
                        )
                        return@get
                    }

                    // 성공적으로 인증되었을 때, Google에서 받은 Access Token을 사용
                    val accessToken = principal.accessToken

                    oAuthTokenValidator.validateGoogleToken(accessToken)?.let { userInfo ->
                        val email = userInfo.email
                        val name = userInfo.name
                        val picture = userInfo.picture

                        // OAuthLoginUseCase를 사용하여 로그인/등록 처리
                        val request = OAuthLoginRequest(
                            provider = "GOOGLE",
                            accessToken = accessToken,
                            email = email,
                            displayName = name,
                            profileImageUrl = picture
                        )
                        val authResponse: AuthResponse = oAuthLoginUseCase.execute(request)
                        call.respond(HttpStatusCode.OK, authResponse)
                    } ?: run {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ErrorResponse("INVALID_OAUTH_TOKEN", "Invalid or expired Google access token")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("OAUTH_FAILED", e.message ?: "OAuth login failed")
                    )
                }
            }
        }

        // OAuth login for mobile/external clients
        post("/oauth/login") {
            try {
                val request = call.receive<OAuthLoginRequest>()
                
                // Validate OAuth token with provider
                val userInfo = when (request.provider.uppercase()) {
                    "GOOGLE" -> {
                        oAuthTokenValidator.validateGoogleToken(request.accessToken)?.let { userInfo ->
                            OAuthUserInfo(
                                email = userInfo.email,
                                name = userInfo.name,
                                picture = userInfo.picture
                            )
                        } ?: run {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ErrorResponse("INVALID_OAUTH_TOKEN", "Invalid or expired Google access token")
                            )
                            return@post
                        }
                    }

                    // TODO : 다음에 추가하기
//                    "KAKAO" -> {
//                        oAuthTokenValidator.validateKakaoToken(request.accessToken)?.let { userInfo ->
//                            OAuthUserInfo(
//                                email = userInfo.kakao_account?.email ?: throw Exception("Email not provided by Kakao"),
//                                name = userInfo.kakao_account.profile?.nickname ?: "Unknown",
//                                picture = userInfo.kakao_account.profile?.profile_image_url
//                            )
//                        } ?: run {
//                            call.respond(
//                                HttpStatusCode.Unauthorized,
//                                ErrorResponse("INVALID_OAUTH_TOKEN", "Invalid or expired Kakao access token")
//                            )
//                            return@post
//                        }
//                    }
                    
                    else -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("UNSUPPORTED_PROVIDER", "Unsupported OAuth provider: ${request.provider}")
                        )
                        return@post
                    }
                }
                
                // Validate that the email from token matches the request
                if (userInfo.email != request.email) {
                    logger.error("Email mismatch: token email=${userInfo.email}, request email=${request.email}")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("EMAIL_MISMATCH", "Email from OAuth token does not match request")
                    )
                    return@post
                }
                
                // Create validated OAuth login request
                val validatedRequest = OAuthLoginRequest(
                    provider = request.provider,
                    accessToken = request.accessToken,
                    email = userInfo.email,
                    displayName = request.displayName,
                    profileImageUrl = request.profileImageUrl ?: userInfo.picture
                )
                
                val response = oAuthLoginUseCase.execute(validatedRequest)
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: InvalidCredentialsException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("OAUTH_FAILED", e.message ?: "OAuth login failed")
                )
            } catch (e: Exception) {
                logger.error("OAuth login failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("OAUTH_FAILED", e.message ?: "OAuth login failed")
                )
            }
        }
        
        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()
                val response = refreshTokenUseCase.execute(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: InvalidCredentialsException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_TOKEN", e.message ?: "Invalid refresh token")
                )
            } catch (e: Exception) {
                logger.error("Token refresh failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("REFRESH_FAILED", e.message ?: "Token refresh failed")
                )
            }
        }
        
        // Protected endpoints
        authenticate("auth-jwt") {
            get("/me") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: throw Exception("User ID not found in token")
                    
                    val response = getUserUseCase.execute(userId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: UserNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("USER_NOT_FOUND", e.message ?: "User not found")
                    )
                } catch (e: Exception) {
                    logger.error("Get user failed", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("GET_USER_FAILED", e.message ?: "Failed to get user")
                    )
                }
            }
            
            patch("/me") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: throw Exception("User ID not found in token")
                    
                    val request = call.receive<UpdateUserProfileRequest>()
                    val response = updateUserProfileUseCase.execute(userId, request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: UserNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("USER_NOT_FOUND", e.message ?: "User not found")
                    )
                } catch (e: UserAlreadyExistsException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse("USERNAME_TAKEN", e.message ?: "Username already taken")
                    )
                } catch (e: Exception) {
                    logger.error("Update user failed", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("UPDATE_FAILED", e.message ?: "Failed to update user")
                    )
                }
            }
            
            post("/me/personality") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: throw Exception("User ID not found in token")
                    
                    val request = call.receive<PersonalityTestRequest>()
                    val response = updatePersonalityProfileUseCase.execute(userId, request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: UserNotFoundException) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("USER_NOT_FOUND", e.message ?: "User not found")
                    )
                } catch (e: Exception) {
                    logger.error("Update personality failed", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("PERSONALITY_UPDATE_FAILED", e.message ?: "Failed to update personality")
                    )
                }
            }
            
            post("/me/subscription/upgrade") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: throw Exception("User ID not found in token")
                    
                    @Serializable
                    data class UpgradeRequest(val paymentToken: String, val months: Int = 1)
                    
                    val request = call.receive<UpgradeRequest>()
                    val response = upgradeSubscriptionUseCase.execute(userId, request.paymentToken, request.months)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: PaymentFailedException) {
                    call.respond(
                        HttpStatusCode.PaymentRequired,
                        ErrorResponse("PAYMENT_FAILED", e.message ?: "Payment processing failed")
                    )
                } catch (e: Exception) {
                    logger.error("Subscription upgrade failed", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("UPGRADE_FAILED", e.message ?: "Failed to upgrade subscription")
                    )
                }
            }
            
            get("/me/can-generate-story") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: throw Exception("User ID not found in token")
                    
                    val canGenerate = checkStoryGenerationEligibilityUseCase.execute(userId)
                    
                    @Serializable
                    data class EligibilityResponse(val canGenerate: Boolean)
                    
                    call.respond(HttpStatusCode.OK, EligibilityResponse(canGenerate))
                } catch (e: Exception) {
                    logger.error("Check eligibility failed", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("CHECK_FAILED", e.message ?: "Failed to check eligibility")
                    )
                }
            }
            
            post("/logout") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: throw Exception("User ID not found in token")
                    
                    logoutUseCase.execute(userId)
                    call.respond(HttpStatusCode.OK, SuccessResponse("Logged out successfully"))
                } catch (e: Exception) {
                    logger.error("Logout failed", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("LOGOUT_FAILED", e.message ?: "Failed to logout")
                    )
                }
            }
        }
    }
}
