package com.novel.mocks

import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.google.genai.Client
import com.google.genai.Models
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentConfig
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
                
                // 감정 분석 요청인지 확인
                val isEmotionAnalysis = request.messages.any { msg -> 
                    msg.content?.contains("감정 분석") == true ||
                    msg.content?.contains("JSON 형식") == true
                }
                
                val responseContent = when {
                    // 감정 분석 응답
                    isEmotionAnalysis -> {
                        when {
                            userMessage.contains("행복") || userMessage.contains("좋은") || userMessage.contains("기쁘") -> """
                                {
                                    "primaryEmotion": "HAPPY",
                                    "confidence": 0.95,
                                    "intensity": 0.85,
                                    "subEmotions": ["GRATEFUL", "EXCITED"],
                                    "keywords": ["행복한", "가족", "함께"],
                                    "sentiment": "긍정적",
                                    "emotionalProgression": "일관되게 행복한 감정을 유지",
                                    "sentenceEmotions": [
                                        {"sentence": "오늘 정말 행복한 하루였어요!", "emotion": "HAPPY", "intensity": 0.9},
                                        {"sentence": "오랜만에 가족들과 함께 시간을 보냈거든요.", "emotion": "GRATEFUL", "intensity": 0.8}
                                    ]
                                }
                            """.trimIndent()
                            
                            userMessage.contains("슬픔") || userMessage.contains("힘든") || userMessage.contains("우울") -> """
                                {
                                    "primaryEmotion": "SAD",
                                    "confidence": 0.88,
                                    "intensity": 0.75,
                                    "subEmotions": ["DISAPPOINTED", "ANXIOUS"],
                                    "keywords": ["힘든", "아무것도"],
                                    "sentiment": "부정적",
                                    "emotionalProgression": "점차 무기력해지는 감정 변화"
                                }
                            """.trimIndent()
                            
                            userMessage.contains("졸업") || userMessage.contains("그리움") || userMessage.contains("아쉬") -> """
                                {
                                    "primaryEmotion": "NOSTALGIC",
                                    "confidence": 0.82,
                                    "intensity": 0.88,
                                    "subEmotions": ["HAPPY", "SAD", "GRATEFUL"],
                                    "keywords": ["졸업식", "기쁘면서도", "아쉬워", "친구들", "헤어져야"],
                                    "sentiment": "복합적",
                                    "emotionalProgression": "기쁨에서 아쉬움으로 전환되는 감정",
                                    "sentenceEmotions": [
                                        {"sentence": "졸업식이 끝났어요.", "emotion": "NEUTRAL", "intensity": 0.5},
                                        {"sentence": "기쁘면서도 아쉬워요.", "emotion": "NOSTALGIC", "intensity": 0.9},
                                        {"sentence": "친구들과 헤어져야 한다니...", "emotion": "SAD", "intensity": 0.85}
                                    ]
                                }
                            """.trimIndent()
                            
                            userMessage.contains("시험") || userMessage.contains("불안") -> """
                                {
                                    "primaryEmotion": "ANXIOUS",
                                    "confidence": 0.85,
                                    "intensity": 0.78,
                                    "subEmotions": ["EXCITED", "HOPEFUL"],
                                    "keywords": ["시험", "결과"],
                                    "sentiment": "긴장된"
                                }
                            """.trimIndent()
                            
                            userMessage.contains("평온") || userMessage.contains("조용") -> """
                                {
                                    "primaryEmotion": "CALM",
                                    "confidence": 0.75,
                                    "intensity": 0.5,
                                    "subEmotions": [],
                                    "keywords": [],
                                    "sentiment": "긍정적"
                                }
                            """.trimIndent()
                            
                            else -> """
                                {
                                    "primaryEmotion": "NEUTRAL",
                                    "confidence": 0.5,
                                    "intensity": 0.5,
                                    "subEmotions": [],
                                    "keywords": [],
                                    "sentiment": "중립적"
                                }
                            """.trimIndent()
                        }
                    }
                    
                    // 일반 대화 응답
                    userMessage.contains("안녕") && userMessage.contains("좋은 일") -> 
                        "[EMOTION: HAPPY] 안녕하세요! 정말 좋은 일이 있으셨군요! 어떤 일이었는지 자세히 들려주시겠어요?"
                    
                    userMessage.contains("행복") || userMessage.contains("기쁨") -> 
                        "[EMOTION: HAPPY] 정말 행복하신 순간이군요! 눈물이 날 정도로 기쁘신 일이 무엇이었나요?"
                    
                    userMessage.contains("슬픔") || userMessage.contains("힘든") || userMessage.contains("우울") -> 
                        "[EMOTION: SAD] 힘드신 일이 있으셨군요. 제가 들어드릴게요. 천천히 이야기해주세요."
                    
                    userMessage.contains("화남") || userMessage.contains("짜증") || userMessage.contains("분노") ->
                        "[EMOTION: ANGRY] 화가 나셨군요. 무엇이 그렇게 속상하셨나요?"
                    
                    userMessage.contains("의미 있는") || userMessage.contains("마무리") || userMessage.contains("끝") || userMessage.contains("완성") ->
                        "[READY_FOR_STORY] [CONTEXT: 사용자는 오늘 특별한 경험을 했고, 가족과 함께 소중한 시간을 보냈다] 오늘 하루 정말 의미 있으셨네요. 이 이야기를 아름다운 이야기로 만들어볼까요?"
                    
                    userMessage.contains("친구") && userMessage.contains("만났") ->
                        "친구를 만나셨군요! 오랜만에 만나신 건가요?"
                    
                    else -> 
                        "응답"
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
                    every { model } returns ModelId("gpt-4")
                }
            }
            
            return openAI
        }
        
        /**
         * 특정 응답을 반환하는 Mock 생성
         */
        fun createMockWithResponse(response: String): OpenAI {
            val openAI = mockk<OpenAI>()
            
            coEvery { openAI.chatCompletion(any()) } returns mockk<ChatCompletion> {
                every { choices } returns listOf(
                    mockk<ChatChoice> {
                        every { message } returns ChatMessage(
                            role = Role.Assistant,
                            content = response
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
            
            // final 필드인 models를 우회하는 방법 - Reflection 사용
            val modelsField = Client::class.java.getDeclaredField("models")
            modelsField.isAccessible = true
            modelsField.set(client, models)
            
            // 스토리 생성 응답 설정
            coEvery {
                models.generateContent(any(), any<String>(), any<GenerateContentConfig>())
            } answers {
                val prompt = secondArg<String>()
                
                val (emotion, emotionKr, genre) = when {
                    prompt.contains("행복") || prompt.contains("HAPPY") -> Triple("행복한", "행복", "가족 일상")
                    prompt.contains("슬픔") || prompt.contains("SAD") -> Triple("슬픈", "슬픔", "감성")
                    prompt.contains("그리움") || prompt.contains("NOSTALGIC") -> Triple("그리운", "그리움", "성장")
                    prompt.contains("평온") || prompt.contains("CALM") -> Triple("평온한", "평온", "일상")
                    prompt.contains("불안") || prompt.contains("ANXIOUS") -> Triple("긴장된", "불안", "성장")
                    else -> Triple("일상적인", "중립", "일상")
                }
                
                val storyContent = when {
                    prompt.contains("엄마") || prompt.contains("생일") || prompt.contains("가족") -> """
                        제목: 엄마의 특별한 하루
                        
                        장르: 가족 일상
                        
                        감정곡선: 기대감에서 시작해 감동과 행복으로 마무리되는 따뜻한 여정
                        
                        ---
                        
                        아침 햇살이 창문을 통해 들어오던 날, 나는 평소보다 일찍 눈을 떴다. 오늘은 엄마의 생일. 
                        몇 주 전부터 가족들과 몰래 준비해온 깜짝 파티가 드디어 열리는 날이었다. 엄마가 평소에 
                        좋아하시던 꽃과 케이크를 준비하며, 우리 가족은 하나가 되어 움직였다. 엄마의 놀란 표정과 
                        눈물 섞인 미소를 보며, 나는 가족이라는 것이 얼마나 소중한지 다시 한번 깨달았다.
                        
                        ---
                        
                        핵심순간:
                        - 엄마가 문을 열고 들어오시는 순간
                        - 가족 모두가 함께 "생일 축하합니다"를 부르는 순간
                        - 엄마가 감동의 눈물을 흘리시는 순간
                    """.trimIndent()
                    
                    prompt.contains("졸업") || prompt.contains("친구") || prompt.contains("학교") -> """
                        제목: 마지막 교실의 햇살
                        
                        장르: 성장
                        
                        감정곡선: 아쉬움과 그리움 속에서 새로운 시작을 향한 희망을 발견하는 여정
                        
                        ---
                        
                        교실에 비치는 오후의 햇살이 유독 따뜻했던 그날, 우리는 마지막으로 이 공간에 모였다.
                        3년 동안 함께 웃고 울었던 친구들과 찍는 마지막 사진. 셔터 소리와 함께 우리의 
                        고등학교 시절도 한 장의 사진 속에 영원히 머물게 되었다.
                        
                        ---
                        
                        핵심순간:
                        - 빈 교실에서 추억을 되새기는 순간
                        - 친구들과 마지막 포옹을 나누는 순간
                        - 교문을 나서며 뒤돌아보는 순간
                    """.trimIndent()
                    
                    prompt.contains("산책") || prompt.contains("조용") -> """
                        제목: 오후의 산책
                        
                        장르: 일상
                        
                        감정곡선: 잔잔하게 시작해서 깊은 감동으로 마무리되는 여정
                        
                        ---
                        
                        조용한 오후, 나는 혼자 공원을 걸었다. 바람이 살랑살랑 불어왔다.
                        
                        ---
                        
                        핵심순간:
                        - 순간의 발견
                        - 감정의 전환점
                        - 새로운 깨달음의 순간
                    """.trimIndent()
                    
                    else -> """
                        제목: $emotion 하루의 기록
                        
                        장르: $genre
                        
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
                }
                
                val response = mockk<GenerateContentResponse>()
                every { response.text() } returns storyContent
                
                response
            }
            
            return client
        }
        
        /**
         * 특정 스토리 응답을 반환하는 Mock 생성
         */
        fun createMockWithResponse(storyResponse: String): Client {
            val client = mockk<Client>()
            val models = mockk<Models>()
            
            // final 필드인 models를 우회하는 방법 - Reflection 사용
            val modelsField = Client::class.java.getDeclaredField("models")
            modelsField.isAccessible = true
            modelsField.set(client, models)
            
            coEvery {
                models.generateContent(any(), any<String>(), any<GenerateContentConfig>())
            } returns mockk<GenerateContentResponse> {
                every { text() } returns storyResponse
            }
            
            return client
        }
        
        /**
         * null 응답을 반환하는 Mock 생성 (에러 테스트용)
         */
        fun createMockWithNullResponse(): Client {
            val client = mockk<Client>()
            val models = mockk<Models>()
            
            // final 필드인 models를 우회하는 방법 - Reflection 사용
            val modelsField = Client::class.java.getDeclaredField("models")
            modelsField.isAccessible = true
            modelsField.set(client, models)
            
            coEvery {
                models.generateContent(any(), any<String>(), any<GenerateContentConfig>())
            } returns mockk<GenerateContentResponse> {
                every { text() } returns null
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
        mockkStatic("com.aallam.openai.client.OpenAIKt")
        every { OpenAI(any<OpenAIConfig>()) } returns MockOpenAIService.createMock()
        every { OpenAI(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns MockOpenAIService.createMock()
        
        // Gemini Mock 설정
        mockkStatic(Client::class)
        every { Client.builder() } returns MockGeminiService.createMockBuilder()
        
        // HttpClient constructor mocking은 복잡하고 문제가 많으므로 제거
        // 대신 TestModule에서 ElevenLabsService를 직접 mock으로 설정
    }
    
    /**
     * ElevenLabs 서비스를 위한 별도 Mock 설정
     * HttpClient는 복잡한 설정이 필요하므로 별도로 분리
     */
    fun setupElevenLabsMock(): HttpClient {
        return MockElevenLabsService.createMockHttpClient()
    }
    
    fun teardownAllMocks() {
        unmockkAll()
    }
    
    /**
     * 실패 시나리오를 위한 Mock 설정
     */
    fun setupFailureMocks() {
        // OpenAI 실패 Mock
        val failingOpenAI = mockk<OpenAI>()
        coEvery { failingOpenAI.chatCompletion(any()) } throws Exception("OpenAI API Error")
        
        mockkStatic("com.aallam.openai.client.OpenAIKt")
        every { OpenAI(any<OpenAIConfig>()) } returns failingOpenAI
        every { OpenAI(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns failingOpenAI
        
        // Gemini 실패 Mock
        val failingClient = mockk<Client>()
        val failingModels = mockk<Models>()
        
        // final 필드인 models를 우회하는 방법 - Reflection 사용
        val modelsField = Client::class.java.getDeclaredField("models")
        modelsField.isAccessible = true
        modelsField.set(failingClient, failingModels)
        
        coEvery { failingModels.generateContent(any(), any<String>(), any<GenerateContentConfig>()) } throws Exception("Gemini API Error")
        
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
        // OpenAI 지연 Mock
        val delayedOpenAI = mockk<OpenAI>()
        coEvery { delayedOpenAI.chatCompletion(any()) } coAnswers {
            delay(delayMillis)
            MockOpenAIService.createMock().chatCompletion(firstArg())
        }
        
        mockkStatic("com.aallam.openai.client.OpenAIKt")
        every { OpenAI(any<OpenAIConfig>()) } returns delayedOpenAI
        every { OpenAI(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns delayedOpenAI
        
        // Gemini 지연 Mock
        val delayedClient = mockk<Client>()
        val delayedModels = mockk<Models>()
        
        // final 필드인 models를 우회하는 방법 - Reflection 사용
        val modelsField = Client::class.java.getDeclaredField("models")
        modelsField.isAccessible = true
        modelsField.set(delayedClient, delayedModels)
        
        coEvery { delayedModels.generateContent(any(), any<String>(), any<GenerateContentConfig>()) } coAnswers {
            delay(delayMillis)
            val mockClient = MockGeminiService.createMock()
            mockClient.models.generateContent(firstArg<String>(), secondArg<String>(), thirdArg<GenerateContentConfig>())
        }
        
        val delayedBuilder = mockk<Client.Builder>()
        every { delayedBuilder.apiKey(any()) } returns delayedBuilder
        every { delayedBuilder.build() } returns delayedClient
        
        mockkStatic(Client::class)
        every { Client.builder() } returns delayedBuilder
    }
    
    /**
     * 특정 응답을 반환하는 Mock 설정
     */
    fun setupCustomResponseMocks(openAIResponse: String? = null, geminiResponse: String? = null) {
        // OpenAI 커스텀 응답
        if (openAIResponse != null) {
            val customOpenAI = MockOpenAIService.createMockWithResponse(openAIResponse)
            mockkStatic("com.aallam.openai.client.OpenAIKt")
            every { OpenAI(any<OpenAIConfig>()) } returns customOpenAI
            every { OpenAI(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns customOpenAI
        }
        
        // Gemini 커스텀 응답
        if (geminiResponse != null) {
            val customClient = MockGeminiService.createMockWithResponse(geminiResponse)
            val customBuilder = mockk<Client.Builder>()
            every { customBuilder.apiKey(any()) } returns customBuilder
            every { customBuilder.build() } returns customClient
            
            mockkStatic(Client::class)
            every { Client.builder() } returns customBuilder
        }
    }
} 