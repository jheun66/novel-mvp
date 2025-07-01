package com.novel.services

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.Duration


// Data Classes
@Serializable
data class StoryRequest(
    val content: String,      // 일상 내용
    val emotion: String,      // 감정
    val isVoiceInput: Boolean = false  // 음성 입력 여부
)

@Serializable
data class StoryResponse(
    val title: String,
    val content: String,
    val emotion: String,
    val createdAt: Long = System.currentTimeMillis()
)

// Story Service
class StoryService(private val apiKey: String) {
    private val logger = LoggerFactory.getLogger(StoryService::class.java)
    
    private val model = try {
        GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gemini-2.5-flash-preview-04-17")
            .temperature(0.8)
            .maxOutputTokens(1500)
            .timeout(Duration.ofSeconds(30))
            .build()
    } catch (e: Exception) {
        logger.error("Failed to initialize Gemini model", e)
        throw IllegalStateException("Failed to initialize Gemini model: ${e.message}")
    }

    fun generateStory(request: StoryRequest): StoryResponse {
        // 입력 검증
        if (request.content.isBlank()) {
            throw IllegalArgumentException("Content cannot be empty")
        }
        
        logger.info("Generating story for emotion: ${request.emotion}")
        
        val prompt = """
            당신은 감성적인 소설가입니다. 
            사용자의 일상 기록을 바탕으로 짧고 감동적인 이야기를 만들어주세요.
            
            일상 내용: ${request.content}
            오늘의 감정: ${translateEmotion(request.emotion)}
            
            다음 형식으로 작성해주세요:
            - 300-500자 분량
            - 1인칭 시점
            - 일상에서 특별한 의미 찾기
            - 따뜻하고 희망적인 톤
            
            제목: [간단한 제목]
            
            [본문 내용]
        """.trimIndent()

        try {
            logger.debug("Sending prompt to Gemini: $prompt")
            val response = model.chat(prompt)
            logger.debug("Generated response: $response")
            
            if (response.isNullOrBlank()) {
                logger.error("Received empty response from Gemini API")
                throw RuntimeException("Received empty response from Gemini API")
            }
            
            val lines = response.lines()

            val title = lines.find { it.startsWith("제목:") }
                ?.substring(3)?.trim() 
                ?: lines.firstOrNull()?.take(20) 
                ?: "무제"

            val content = if (lines.any { it.startsWith("제목:") }) {
                lines
                    .dropWhile { !it.startsWith("제목:") }
                    .drop(1)
                    .joinToString("\n")
                    .trim()
            } else {
                // 제목이 없는 경우 전체 응답을 본문으로 사용
                response
            }

            return StoryResponse(
                title = title,
                content = content.ifBlank { response }, // 본문이 비어있으면 전체 응답 사용
                emotion = request.emotion
            )
        } catch (e: Exception) {
            logger.error("Failed to generate story with Gemini API", e)
            logger.error("Error details: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Failed to generate story: ${e.message}", e)
        }
    }

    private fun translateEmotion(emotion: String): String {
        return when(emotion.uppercase()) {
            "HAPPY" -> "행복"
            "SAD" -> "슬픔"
            "EXCITED" -> "설렘"
            "CALM" -> "평온"
            "ANGRY" -> "화남"
            "GRATEFUL" -> "감사"
            else -> "보통"
        }
    }
}
