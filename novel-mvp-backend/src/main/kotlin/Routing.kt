package com.novel

import com.novel.services.StoryRequest
import com.novel.services.StoryService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")
    
    // Load environment variables from .env file
    val dotenv = dotenv {
        ignoreIfMissing = true // 개발 환경에서 .env 파일이 없어도 에러 발생하지 않음
    }
    
    // Gemini API Key - .env 파일 또는 시스템 환경변수에서 읽기
    val geminiApiKey = dotenv["GEMINI_API_KEY"] 
        ?: System.getenv("GEMINI_API_KEY")
        ?: throw IllegalStateException("GEMINI_API_KEY not set in .env file or environment variables")

    logger.info("Gemini API Key loaded successfully")

    // Services
    val storyService = StoryService(geminiApiKey)

    // Routes
    routing {
        get("/") {
            call.respondText("Novel MVP API is running!")
        }

        route("/api") {
            // 스토리 생성 (핵심 기능)
            post("/generate-story") {
                try {
                    val request = call.receive<StoryRequest>()
                    logger.info("Received story request: $request")
                    
                    val story = storyService.generateStory(request)
                    logger.info("Story generated successfully: ${story.title}")
                    
                    call.respond(story)
                } catch (e: Exception) {
                    logger.error("Error generating story", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf(
                            "error" to "Failed to generate story",
                            "message" to (e.message ?: "Unknown error"),
                            "type" to e.javaClass.simpleName
                        )
                    )
                }
            }

            // 건강 체크
            get("/health") {
                call.respond(mapOf(
                    "status" to "healthy",
                    "geminiApiKeySet" to geminiApiKey.isNotEmpty().toString()
                ))
            }
        }
    }
}
