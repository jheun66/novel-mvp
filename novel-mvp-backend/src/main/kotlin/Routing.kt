package com.novel

import com.novel.agents.ConversationAgent
import com.novel.agents.EmotionAnalysisAgent
import com.novel.agents.StoryGenerationAgent
import com.novel.agents.base.SimpleAgentCommunicator
import com.novel.routes.userRoutes
import com.novel.services.ElevenLabsConfig
import com.novel.services.ElevenLabsService
import com.novel.services.NovelWebSocketService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    
    // Load environment variables from .env file
    val dotenv = dotenv {
        ignoreIfMissing = true // 개발 환경에서 .env 파일이 없어도 에러 발생하지 않음
    }

    val openAiApiKey = dotenv["OPENAI_API_KEY"]
        ?: System.getenv("OPENAI_API_KEY")
        ?: throw IllegalStateException("OPENAI_API_KEY not set in .env file or environment variables")

    // ElevenLabs API Key - .env 파일 또는 시스템 환경변수에서 읽기
    val elevenLabsApiKey = dotenv["ELEVENLABS_API_KEY"]
        ?: System.getenv("ELEVENLABS_API_KEY")
        ?: throw IllegalStateException("ELEVENLABS_API_KEY not set in .env file or environment variables")

    // Gemini API Key - .env 파일 또는 시스템 환경변수에서 읽기
    val geminiApiKey = dotenv["GEMINI_API_KEY"] 
        ?: System.getenv("GEMINI_API_KEY")
        ?: throw IllegalStateException("GEMINI_API_KEY not set in .env file or environment variables")

    val httpClient by inject<HttpClient>()

    // Initialize services
    val communicator = SimpleAgentCommunicator()
    val elevenLabsConfig = ElevenLabsConfig(apiKey = elevenLabsApiKey)
    val speechService = ElevenLabsService(elevenLabsConfig, httpClient)

    // Initialize agents
    val conversationAgent = ConversationAgent(openAiApiKey, communicator)
    val emotionAnalysisAgent = EmotionAnalysisAgent(openAiApiKey, communicator)
    val storyGenerationAgent = StoryGenerationAgent(geminiApiKey, communicator)

    val webSocketService = NovelWebSocketService(
        conversationAgent,
        emotionAnalysisAgent,
        storyGenerationAgent,
        speechService,
        communicator
    )

    // Routes
    routing {
        // User routes
        userRoutes()
        
        // WebSocket endpoint - Authentication handled inside WebSocket connection
        webSocket("/ws/novel") {
            webSocketService.handleWebSocketSession(this)
        }

        get("/") {
            call.respondText("Novel MVP API is running!")
        }
        
        get("/health") {
            call.respondText("OK")
        }
    }
}
