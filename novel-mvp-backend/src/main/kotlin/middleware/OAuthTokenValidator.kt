package com.novel.middleware

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class OAuthTokenValidator(
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(OAuthTokenValidator::class.java)
    
    suspend fun validateGoogleToken(accessToken: String): GoogleUserInfo? {
        return try {
            val response = httpClient.get("https://www.googleapis.com/oauth2/v3/userinfo") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }
            
            if (response.status.isSuccess()) {
                response.body<GoogleUserInfo>()
            } else {
                logger.error("Google token validation failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error validating Google token", e)
            null
        }
    }
    
    suspend fun validateKakaoToken(accessToken: String): KakaoUserInfo? {
        return try {
            val response = httpClient.get("https://kapi.kakao.com/v2/user/me") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }
            
            if (response.status.isSuccess()) {
                response.body<KakaoUserInfo>()
            } else {
                logger.error("Kakao token validation failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            logger.error("Error validating Kakao token", e)
            null
        }
    }
}

@Serializable
data class GoogleUserInfo(
    val sub: String,  // Google user ID
    val email: String,
    val email_verified: Boolean,
    val name: String,
    val given_name: String,
    val family_name: String,
    val picture: String,
    val locale: String? = null
)

@Serializable
data class KakaoUserInfo(
    val id: Long,
    val connected_at: String? = null,
    val kakao_account: KakaoAccount? = null,
    val properties: Map<String, String>? = null
)

@Serializable
data class KakaoAccount(
    val email: String? = null,
    val email_needs_agreement: Boolean? = null,
    val is_email_valid: Boolean? = null,
    val is_email_verified: Boolean? = null,
    val profile: KakaoProfile? = null,
    val has_email: Boolean? = null
)

@Serializable
data class KakaoProfile(
    val nickname: String? = null,
    val thumbnail_image_url: String? = null,
    val profile_image_url: String? = null,
    val is_default_image: Boolean? = null
)
