package com.novel.mocks

import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.google.genai.Client
import com.google.genai.Models
import com.google.genai.types.GenerateContentResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.mockk.*
import kotlinx.coroutines.delay

/**
 * OpenAI API Mock Service
 */
class MockOpenAIService {
    companion object {
        fun createMock(): OpenAI {
            val openAI = mockk<OpenAI>()
            
            // 기본 대화 응답 설정
            coEvery { 
                openAI.chatCompletion(any()) 
            } answers {
                val request = firstArg<ChatCompletionRequest>()
                val userMessage = request.messages.lastOrNull { it.role == Role.User }?.content ?: ""
                
                val responseContent = when {
                    userMessage.contains("행복") -> 
                        "[EMOTION: HAPPY] 정말 행복하신 것 같아요! 어떤 일이 있으셨는지 더 자세히 들려주시겠어요?"
                    userMessage.contains("슬픔") || userMessage.contains("힘든") -> 
                        "[EMOTION: SAD] 힘드신 일이 있으셨군요. 제가 들어드릴게요. 천천히 이야기해주세요."
                    userMessage.contains("화남") || userMessage.contains("짜증") ->
                        "[EMOTION: ANGRY] 화가 나셨군요. 무엇이 그렇게 속상하셨나요?"
                    userMessage.contains("완성") || userMessage.contains("마무리") ->
                        "[READY_FOR_STORY] [CONTEXT: 사용자의 하루 이야기가 충분히 수집되었습니다] 오늘 하루 정말 의미 있으셨네요!"
                    else -> 
                        "네, 더 자세히 들려주세요. 어떤 기분이 드셨나요?"
                }
                
                mockk<ChatCompletion> {
                    every { choices } returns listOf(
                        mockk<ChatChoice> {
                            every { message } returns ChatMessage(
                                role = Role.Assistant,
                                content = responseContent
                            )
                        }
                    )
                    every { id } returns "chat-${System.currentTimeMillis()}"
                    every { created } returns System.currentTimeMillis() / 1000
                    every { model } returns ModelId("gpt-4.1")
                }
            }
            
            // 감정 분석 응답 설정 (JSON 형식)
            coEvery {
                openAI.chatCompletion(match { 
                    it.responseFormat != null && it.messages.any { msg -> 
                        msg.content?.contains("감정 분석") == true 
                    }
                })
            } returns mockk<ChatCompletion> {
                every { choices } returns listOf(
                    mockk<ChatChoice> {
                        every { message } returns ChatMessage(
                            role = Role.Assistant,
                            content = """
                                {
                                    "primaryEmotion": "HAPPY",
                                    "confidence": 0.85,
                                    "intensity": 0.8,
                                    "subEmotions": ["GRATEFUL", "EXCITED"],
                                    "keywords": ["가족", "함께", "시간"],
                                    "sentiment": "긍정적",
                                    "emotionalProgression": "점점 더 행복해지는 감정",
                                    "sentenceEmotions": [
                                        {"sentence": "오늘 가족과 함께했어요", "emotion": "HAPPY", "intensity": 0.9}
                                    ]
                                }
                            """.trimIndent()
                        )
                    }
                )
                every { id } returns "chat-${System.currentTimeMillis()}"
                every { created } returns System.currentTimeMillis() / 1000
                every { model } returns ModelId("gpt-4.1")
            }
            
            return openAI
        }
    }
}

/**
 * Gemini API Mock Service
 */
class MockGeminiService {
    companion object {
        fun createMock(): Client {
            val client = mockk<Client>()
            val models = mockk<Models>()
            
            every { client.models } returns models
            
            // 스토리 생성 응답 설정
            coEvery {
                models.generateContent(any(), any<String>(), any())
            } answers {
                val prompt = secondArg<String>()
                
                val emotion = when {
                    prompt.contains("행복") -> "행복한"
                    prompt.contains("슬픔") -> "슬픈"
                    prompt.contains("그리움") -> "그리운"
                    else -> "평온한"
                }
                
                val response = mockk<GenerateContentResponse>()
                every { response.text() } returns """
                    제목: $emotion 하루의 기록
                    
                    장르: 일상
                    
                    감정곡선: 잔잔하게 시작해서 깊은 감동으로 마무리되는 여정
                    
                    ---
                    
                    오늘은 특별한 하루였다. 아침 햇살이 창문을 통해 들어올 때부터 무언가 다른 느낌이었다.
                    가족과 함께한 시간은 그 무엇과도 바꿀 수 없는 소중한 순간이었다.
                    이런 평범한 일상 속에서 행복을 발견하는 것, 그것이 진정한 삶의 의미가 아닐까.
                    
                    ---
                    
                    핵심순간:
                    - 가족과 함께 아침을 먹는 순간
                    - 오후의 따뜻한 대화
                    - 하루를 마무리하며 느낀 감사함
                """.trimIndent()
                
                response
            }
            
            return client
        }
        
        fun createMockBuilder(): Client.Builder {
            val builder = mockk<Client.Builder>()
            val client = createMock()
            
            every { builder.apiKey(any()) } returns builder
            every { builder.build() } returns client
            
            return builder
        }
    }
}

/**
 * ElevenLabs API Mock Service
 */
class MockElevenLabsService {
    companion object {
        fun createMockHttpClient(): HttpClient {
            val httpClient = mockk<HttpClient>()
            
            // TTS 음성 변환 응답
            coEvery {
                httpClient.post(any<String>()) {
                    any()
                }
            } returns mockk<HttpResponse> {
                every { status.value } returns 200
                coEvery { readRawBytes() } returns "Mock audio data".toByteArray()
            }
            
            // 음성 목록 조회
            coEvery {
                httpClient.get(any<String>()) {
                    any()
                }
            } returns mockk<HttpResponse> {
                every { status.value } returns 200
                coEvery { bodyAsText() } returns """
                    {
                        "voices": [
                            {
                                "voice_id": "21m00Tcm4TlvDq8ikWAM",
                                "name": "Rachel",
                                "category": "premade",
                                "settings": {
                                    "stability": 0.75,
                                    "similarity_boost": 0.75
                                }
                            }
                        ]
                    }
                """.trimIndent()
            }
            
            return httpClient
        }
    }
}

/**
 * 통합 테스트를 위한 Mock Factory
 */
object MockServiceFactory {
    fun setupAllMocks() {
        // OpenAI Mock 설정
        mockkConstructor(OpenAI::class)
        every { anyConstructed<OpenAI>() } returns MockOpenAIService.createMock()
        
        // Gemini Mock 설정
        mockkStatic(Client::class)
        every { Client.builder() } returns MockGeminiService.createMockBuilder()
        
        // HttpClient Mock 설정 (ElevenLabs용)
        mockkConstructor(HttpClient::class)
        every { anyConstructed<HttpClient>() } returns MockElevenLabsService.createMockHttpClient()
    }
    
    fun teardownAllMocks() {
        unmockkAll()
    }
    
    /**
     * 실패 시나리오를 위한 Mock 설정
     */
    fun setupFailureMocks() {
        val failingOpenAI = mockk<OpenAI>()
        coEvery { failingOpenAI.chatCompletion(any()) } throws Exception("OpenAI API Error")
        
        mockkConstructor(OpenAI::class)
        every { anyConstructed<OpenAI>() } returns failingOpenAI
        
        val failingClient = mockk<Client>()
        val failingModels = mockk<Models>()
        every { failingClient.models } returns failingModels
        coEvery { failingModels.generateContent(any(), any<String>(), any()) } throws Exception("Gemini API Error")
        
        val failingBuilder = mockk<Client.Builder>()
        every { failingBuilder.apiKey(any()) } returns failingBuilder
        every { failingBuilder.build() } returns failingClient
        
        mockkStatic(Client::class)
        every { Client.builder() } returns failingBuilder
    }
    
    /**
     * 지연 응답을 위한 Mock 설정
     */
    fun setupDelayedMocks(delayMillis: Long = 1000) {
        val delayedOpenAI = mockk<OpenAI>()
        coEvery { delayedOpenAI.chatCompletion(any()) } coAnswers {
            delay(delayMillis)
            MockOpenAIService.createMock().chatCompletion(firstArg())
        }
        
        mockkConstructor(OpenAI::class)
        every { anyConstructed<OpenAI>() } returns delayedOpenAI
    }
} 