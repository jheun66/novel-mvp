package com.novel.di

import com.novel.config.JWTConfig
import com.novel.services.JWTService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val serviceModule = module {
    // JWTService - JWTConfig에 의존
    single<JWTService> {
        val jwtConfig = get<JWTConfig>()

        JWTService(
            realm = jwtConfig.realm,
            secret = jwtConfig.secret,
            issuer = jwtConfig.issuer,
            audience = jwtConfig.audience,
            accessTokenExpiry = jwtConfig.accessTokenExpiry,
            refreshTokenExpiry = jwtConfig.refreshTokenExpiry
        )
    }

    // HTTP Client
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            engine {
                requestTimeout = 30_000 // 30 seconds
            }
        }
    }

//    // TODO: 비즈니스 서비스들
//    // single<UserService> { UserService(get()) }
//    // single<StoryService> { StoryService(get(), get()) }
//    // single<LoggingExample.UserService> { LoggingExample.UserService(get()) }
}
