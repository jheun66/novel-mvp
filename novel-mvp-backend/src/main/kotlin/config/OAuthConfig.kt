package com.novel.config

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*

data class OAuthConfig(
    val google: GoogleOAuthConfig
) {
    data class GoogleOAuthConfig(
        val clientId: String,
        val clientSecret: String,
        val callbackUrl: String
    )
    
    companion object {
        fun fromApplication(application: Application): OAuthConfig {
            val config = application.environment.config
            val isDevelopment = config.property("ktor.development").getString().toBoolean()

            // Load environment variables from .env file
            val dotenv = dotenv {
                ignoreIfMissing = true // 개발 환경에서 .env 파일이 없어도 에러 발생하지 않음
            }

            // TODO : Only use for development
            return OAuthConfig(
                google = GoogleOAuthConfig(
                    clientId = if (isDevelopment) {
                        config.propertyOrNull("oauth.google.clientId")?.getString() ?: ""
                    } else {
                        dotenv["GOOGLE_CLIENT_ID"] ?: System.getenv("GOOGLE_CLIENT_ID") ?: ""
                    },
                    clientSecret = if (isDevelopment) {
                        config.propertyOrNull("oauth.google.clientSecret")?.getString() ?: ""
                    } else {
                        dotenv["GOOGLE_CLIENT_SECRET"] ?: System.getenv("GOOGLE_CLIENT_SECRET") ?: ""
                    },
                    callbackUrl = if (isDevelopment) {
                        "http://localhost:8080/auth/oauth/google/callback"
                    } else {
                        "https://api.yourdomain.com/auth/oauth/google/callback"
                    }
                )
            )
        }
    }
}
