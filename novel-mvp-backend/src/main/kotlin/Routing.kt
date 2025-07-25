package com.novel

import com.novel.agents.ConversationAgent
import com.novel.agents.EmotionAnalysisAgent
import com.novel.agents.StoryGenerationAgent
import com.novel.agents.base.SimpleAgentCommunicator
import com.novel.routes.userRoutes
import com.novel.services.ElevenLabsConfig
import com.novel.services.ElevenLabsTTSService
import com.novel.services.WhisperConfig
import com.novel.services.WhisperSTTService
import com.novel.services.NovelWebSocketService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.util.Base64
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

@Serializable
data class ElevenLabsTTSTestRequest(
    val text: String,
    val emotion: String? = null,
    val speed: Float? = null,
    val addPauses: Boolean? = null
)

@Serializable
data class ElevenLabsTTSTestResponse(
    val success: Boolean,
    val message: String,
    val audioBase64: String? = null,
    val audioSize: Int? = null,
    val processingTimeMs: Long? = null,
    val voice: String? = null,
    val model: String? = null,
    val error: String? = null,
    val availableEmotions: List<String>? = null,
    val availableVoices: List<String>? = null
)

@Serializable
data class ElevenLabsTTSLoadTestRequest(
    val text: String,
    val concurrentRequests: Int = 5
)

@Serializable
data class ElevenLabsTTSLoadTestResult(
    val requestId: Int,
    val success: Boolean,
    val durationMs: Long,
    val audioSize: Int,
    val error: String? = null
)

@Serializable
data class ElevenLabsTTSLoadTestResponse(
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val totalDurationMs: Long,
    val averageRequestDurationMs: Long,
    val results: List<ElevenLabsTTSLoadTestResult>
)

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    
    // Load environment variables from .env file
    val dotenv = dotenv {
        ignoreIfMissing = true // 개발 환경에서 .env 파일이 없어도 에러 발생하지 않음
    }

    val elevenLabsApiKey = dotenv["ELEVENLABS_API_KEY"]
        ?: System.getenv("ELEVENLABS_API_KEY")
        ?: throw IllegalStateException("ELEVENLABS_API_KEY not set in .env file or environment variables")


    // Whisper STT URL - .env 파일 또는 시스템 환경변수에서 읽기 (기본값 제공)
    val whisperSTTUrl = dotenv["WHISPER_STT_URL"]
        ?: System.getenv("WHISPER_STT_URL")
        ?: "http://localhost:5001" // Default for local development

    // Gemini API Key - .env 파일 또는 시스템 환경변수에서 읽기
    val geminiApiKey = dotenv["GEMINI_API_KEY"] 
        ?: System.getenv("GEMINI_API_KEY")
        ?: throw IllegalStateException("GEMINI_API_KEY not set in .env file or environment variables")

    val httpClient by inject<HttpClient>()

    // Initialize services
    val communicator = SimpleAgentCommunicator()
    val elevenLabsConfig = ElevenLabsConfig(apiKey = elevenLabsApiKey)
    val ttsService = ElevenLabsTTSService(elevenLabsConfig, httpClient)
    
    val whisperConfig = WhisperConfig(baseUrl = whisperSTTUrl)
    val sttService = WhisperSTTService(whisperConfig, httpClient)

    // Initialize agents
    val openAiApiKey = dotenv["OPENAI_API_KEY"]
        ?: System.getenv("OPENAI_API_KEY")
        ?: throw IllegalStateException("OPENAI_API_KEY not set in .env file or environment variables")
    val conversationAgent = ConversationAgent(openAiApiKey, communicator)
    val emotionAnalysisAgent = EmotionAnalysisAgent(openAiApiKey, communicator)
    val storyGenerationAgent = StoryGenerationAgent(geminiApiKey, communicator)

    val webSocketService = NovelWebSocketService(
        conversationAgent,
        emotionAnalysisAgent,
        storyGenerationAgent,
        ttsService,
        sttService,
        communicator
    )

    // Routes
    routing {
        // User routes
        userRoutes()
        
        // WebSocket endpoint - Authentication handled during handshake
        webSocket("/ws/novel") {
            // Get Authorization header or token query parameter
            val authHeader = call.request.headers["Authorization"]
            val tokenParam = call.request.queryParameters["token"]
            
            val token = when {
                authHeader?.startsWith("Bearer ") == true -> authHeader.removePrefix("Bearer ").trim()
                !tokenParam.isNullOrBlank() -> tokenParam
                else -> null
            }
            
            if (token.isNullOrBlank()) {
                logger.warn("WebSocket connection attempt without token")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                return@webSocket
            }
            
            webSocketService.handleWebSocketSession(this, token)
        }

        get("/") {
            call.respondText("Novel MVP API is running!")
        }
        
        get("/health") {
            call.respondText("OK")
        }
        
        // ElevenLabs TTS Test APIs
        route("/api/test/elevenlabs") {
            val elevenLabsTTSService by inject<ElevenLabsTTSService>()
            
            // Test simple text-to-speech
            post("/chat-tts") {
                try {
                    val request = call.receive<ElevenLabsTTSTestRequest>()
                    logger.info("Testing ElevenLabs Chat TTS: '${request.text}' with emotion: ${request.emotion}")
                    
                    val startTime = System.currentTimeMillis()
                    val result = elevenLabsTTSService.generateChatTTS(
                        text = request.text,
                        emotion = request.emotion ?: "NEUTRAL",
                        speed = request.speed ?: 1.1f
                    )
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (result.isSuccess) {
                        val audioData = result.getOrNull()
                        if (audioData != null) {
                            call.respond(ElevenLabsTTSTestResponse(
                                success = true,
                                message = "Chat TTS generated successfully",
                                audioBase64 = Base64.getEncoder().encodeToString(audioData),
                                audioSize = audioData.size,
                                processingTimeMs = duration,
                                voice = elevenLabsTTSService.getAvailableEmotions()[request.emotion ?: "NEUTRAL"],
                                model = "eleven_multilingual_v2"
                            ))
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                                success = false,
                                message = "No audio data received",
                                processingTimeMs = duration
                            ))
                        }
                    } else {
                        val error = result.exceptionOrNull()
                        call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                            success = false,
                            message = "TTS generation failed: ${error?.message}",
                            error = error?.javaClass?.simpleName,
                            processingTimeMs = duration
                        ))
                    }
                } catch (e: Exception) {
                    logger.error("ElevenLabs Chat TTS test failed", e)
                    call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                        success = false,
                        message = "Test failed: ${e.message}",
                        error = e.javaClass.simpleName
                    ))
                }
            }
            
            // Test story TTS
            post("/story-tts") {
                try {
                    val request = call.receive<ElevenLabsTTSTestRequest>()
                    logger.info("Testing ElevenLabs Story TTS: '${request.text}' with emotion: ${request.emotion}")
                    
                    val startTime = System.currentTimeMillis()
                    val result = elevenLabsTTSService.generateStoryTTS(
                        text = request.text,
                        emotion = request.emotion ?: "NEUTRAL",
                        speed = request.speed ?: 0.9f,
                        addPauses = request.addPauses ?: true
                    )
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (result.isSuccess) {
                        val audioData = result.getOrNull()
                        if (audioData != null) {
                            call.respond(ElevenLabsTTSTestResponse(
                                success = true,
                                message = "Story TTS generated successfully",
                                audioBase64 = Base64.getEncoder().encodeToString(audioData),
                                audioSize = audioData.size,
                                processingTimeMs = duration
                            ))
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                                success = false,
                                message = "No audio data received",
                                processingTimeMs = duration
                            ))
                        }
                    } else {
                        val error = result.exceptionOrNull()
                        call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                            success = false,
                            message = "Story TTS generation failed: ${error?.message}",
                            error = error?.javaClass?.simpleName,
                            processingTimeMs = duration
                        ))
                    }
                } catch (e: Exception) {
                    logger.error("ElevenLabs Story TTS test failed", e)
                    call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                        success = false,
                        message = "Test failed: ${e.message}",
                        error = e.javaClass.simpleName
                    ))
                }
            }
            
            // Health check
            get("/health") {
                try {
                    val startTime = System.currentTimeMillis()
                    val healthStatus = elevenLabsTTSService.getHealthStatus()
                    val duration = System.currentTimeMillis() - startTime
                    
                    call.respond(ElevenLabsTTSTestResponse(
                        success = true,
                        message = healthStatus,
                        processingTimeMs = duration
                    ))
                } catch (e: Exception) {
                    logger.error("ElevenLabs health check failed", e)
                    call.respond(HttpStatusCode.ServiceUnavailable, ElevenLabsTTSTestResponse(
                        success = false,
                        message = "Health check failed: ${e.message}",
                        error = e.javaClass.simpleName
                    ))
                }
            }
            
            // Service info
            get("/info") {
                try {
                    val startTime = System.currentTimeMillis()
                    val serviceInfo = elevenLabsTTSService.getServiceInfo()
                    val duration = System.currentTimeMillis() - startTime
                    
                    call.respond(ElevenLabsTTSTestResponse(
                        success = true,
                        message = serviceInfo,
                        processingTimeMs = duration,
                        availableEmotions = elevenLabsTTSService.getAvailableEmotions().keys.toList(),
                        availableVoices = elevenLabsTTSService.getAvailableVoices()
                    ))
                } catch (e: Exception) {
                    logger.error("ElevenLabs service info failed", e)
                    call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                        success = false,
                        message = "Service info failed: ${e.message}",
                        error = e.javaClass.simpleName
                    ))
                }
            }
            
            // Load test - multiple concurrent requests
            post("/load-test") {
                try {
                    val request = call.receive<ElevenLabsTTSLoadTestRequest>()
                    logger.info("Starting ElevenLabs TTS load test: ${request.concurrentRequests} concurrent requests")
                    
                    val startTime = System.currentTimeMillis()
                    val results = mutableListOf<ElevenLabsTTSLoadTestResult>()
                    
                    // Run concurrent requests (simplified version)
                    repeat(request.concurrentRequests) { i ->
                        val requestStartTime = System.currentTimeMillis()
                        val result = elevenLabsTTSService.generateChatTTS(
                            text = request.text,
                            emotion = "NEUTRAL"
                        )
                        val requestDuration = System.currentTimeMillis() - requestStartTime
                        
                        results.add(ElevenLabsTTSLoadTestResult(
                            requestId = i + 1,
                            success = result.isSuccess,
                            durationMs = requestDuration,
                            audioSize = result.getOrNull()?.size ?: 0,
                            error = result.exceptionOrNull()?.message
                        ))
                    }
                    
                    val totalDuration = System.currentTimeMillis() - startTime
                    val successCount = results.count { it.success }
                    val avgDuration = results.map { it.durationMs }.average()
                    
                    call.respond(ElevenLabsTTSLoadTestResponse(
                        totalRequests = request.concurrentRequests,
                        successfulRequests = successCount,
                        failedRequests = request.concurrentRequests - successCount,
                        totalDurationMs = totalDuration,
                        averageRequestDurationMs = avgDuration.toLong(),
                        results = results
                    ))
                } catch (e: Exception) {
                    logger.error("ElevenLabs TTS load test failed", e)
                    call.respond(HttpStatusCode.InternalServerError, ElevenLabsTTSTestResponse(
                        success = false,
                        message = "Load test failed: ${e.message}",
                        error = e.javaClass.simpleName
                    ))
                }
            }
        }
    }
}
