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
import com.novel.services.ElevenLabsTTSService
import com.novel.services.ElevenLabsConfig
import com.novel.services.WhisperSTTService
import com.novel.services.WhisperConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import io.github.cdimascio.dotenv.dotenv

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

    // General HTTP Client
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
                level = LogLevel.INFO
                filter { request ->
                    // Exclude body logging for Whisper STT endpoint to avoid binary data logging
                    !request.url.toString().contains("/v1/audio/transcriptions")
                }
                sanitizeHeader { name -> name.lowercase() in listOf("authorization", "cookie") }
            }
        }
    }
    
    // ElevenLabs API HTTP Client - Optimized for TTS requests
    single<HttpClient>(qualifier = org.koin.core.qualifier.named("elevenLabsClient")) {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            engine {
                requestTimeout = 60_000 // 1 minute for TTS processing
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
                filter { request ->
                    // Exclude body logging for TTS endpoints to avoid large audio data logging
                    !request.url.toString().contains("/text-to-speech/")
                }
                sanitizeHeader { name -> name.lowercase() in listOf("authorization", "xi-api-key") }
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
    
    // TTS/STT Services
    single<ElevenLabsConfig> {
        val dotenv = dotenv { ignoreIfMissing = true }
        val elevenLabsApiKey = dotenv["ELEVENLABS_API_KEY"]
            ?: System.getenv("ELEVENLABS_API_KEY")
            ?: throw IllegalStateException("ELEVENLABS_API_KEY not set in .env file or environment variables")
        ElevenLabsConfig(apiKey = elevenLabsApiKey)
    }
    
    single<ElevenLabsTTSService> {
        ElevenLabsTTSService(
            config = get<ElevenLabsConfig>(), 
            httpClient = get<HttpClient>()
        )
    }
    
    single<WhisperConfig> {
        val dotenv = dotenv { ignoreIfMissing = true }
        val whisperSTTUrl = dotenv["WHISPER_STT_URL"]
            ?: System.getenv("WHISPER_STT_URL")
            ?: "http://localhost:5001"
        WhisperConfig(baseUrl = whisperSTTUrl)
    }
    
    single<WhisperSTTService> {
        WhisperSTTService(get<WhisperConfig>(), get<HttpClient>())
    }
}
