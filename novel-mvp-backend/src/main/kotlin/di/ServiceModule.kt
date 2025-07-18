package com.novel.di

import com.novel.application.user.*
import com.novel.config.JWTConfig
import com.novel.domain.user.UserRepository
import com.novel.infrastructure.event.DailyStoryCountResetJob
import com.novel.infrastructure.event.DomainEventPublisherImpl
import com.novel.infrastructure.event.UserEventHandlers
import com.novel.infrastructure.services.MockPaymentService
import com.novel.infrastructure.services.PersonalityAnalyzerImpl
import com.novel.infrastructure.user.UserRepositoryImpl
import com.novel.middleware.OAuthTokenValidator
import com.novel.services.JWTService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
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
            // Logging All Requests and Responses
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
        }
    }
    
    // Infrastructure Layer
    single<UserRepository> { UserRepositoryImpl() }
    single<DomainEventPublisher> { DomainEventPublisherImpl() }
    single<PersonalityAnalyzer> { PersonalityAnalyzerImpl() }
    single<PaymentService> { MockPaymentService() } // Use MockPaymentService for development
    
    // Application Layer - Use Cases
    single<CreateUserUseCase> { 
        CreateUserUseCase(
            userRepository = get(),
            jwtService = get(),
            eventPublisher = get()
        )
    }
    
    single<LoginUseCase> {
        LoginUseCase(
            userRepository = get(),
            jwtService = get()
        )
    }
    
    single<OAuthLoginUseCase> {
        OAuthLoginUseCase(
            userRepository = get(),
            jwtService = get(),
            eventPublisher = get()
        )
    }
    
    single<RefreshTokenUseCase> {
        RefreshTokenUseCase(
            userRepository = get(),
            jwtService = get()
        )
    }
    
    single<GetUserUseCase> {
        GetUserUseCase(
            userRepository = get()
        )
    }
    
    single<UpdateUserProfileUseCase> {
        UpdateUserProfileUseCase(
            userRepository = get()
        )
    }
    
    single<UpdatePersonalityProfileUseCase> {
        UpdatePersonalityProfileUseCase(
            userRepository = get(),
            personalityAnalyzer = get(),
            eventPublisher = get()
        )
    }
    
    single<UpgradeSubscriptionUseCase> {
        UpgradeSubscriptionUseCase(
            userRepository = get(),
            paymentService = get(),
            eventPublisher = get()
        )
    }
    
    single<CheckStoryGenerationEligibilityUseCase> {
        CheckStoryGenerationEligibilityUseCase(
            userRepository = get()
        )
    }
    
    single<LogoutUseCase> {
        LogoutUseCase(
            userRepository = get()
        )
    }
    
    // OAuth Token Validator
    single<OAuthTokenValidator> { OAuthTokenValidator(get()) }
    
    // Event Handlers
    single<UserEventHandlers> { UserEventHandlers() }
    single<DailyStoryCountResetJob> { DailyStoryCountResetJob() }
}
