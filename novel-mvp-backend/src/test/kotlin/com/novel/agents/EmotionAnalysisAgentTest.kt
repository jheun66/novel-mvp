package com.novel.agents

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.core.Role
import com.aallam.openai.client.OpenAI
import com.novel.agents.base.AgentCommunicator
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest

class EmotionAnalysisAgentTest : DescribeSpec({
    
    describe("EmotionAnalysisAgent") {
        lateinit var agent: EmotionAnalysisAgent
        lateinit var openAI: OpenAI
        lateinit var communicator: AgentCommunicator
        
        beforeEach {
            openAI = mockk<OpenAI>()
            communicator = mockk<AgentCommunicator>()
            
            // OpenAI 생성자를 모킹
            mockkConstructor(OpenAI::class)
            every { anyConstructed<OpenAI>() } returns openAI
            
            agent = EmotionAnalysisAgent("test-api-key", communicator)
        }
        
        afterEach {
            unmockkAll()
        }
        
        context("when analyzing emotions") {
            
            context("with happy text") {
                it("should detect happiness with high confidence") {
                    // Arrange
                    val input = EmotionAnalysisInput(
                        text = "오늘 정말 행복한 하루였어요! 오랜만에 가족들과 함께 시간을 보냈거든요.",
                        context = "사용자가 가족 모임에 대해 이야기하고 있음"
                    )
                    
                    val mockJsonResponse = """
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
                    
                    val mockMessage = ChatMessage(
                        role = Role.Assistant,
                        content = mockJsonResponse
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockMessage
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            primaryEmotion shouldBe "HAPPY"
                            confidence shouldBeGreaterThan 0.9f
                            emotionalIntensity shouldBeGreaterThan 0.8f
                            subEmotions shouldContain "GRATEFUL"
                            subEmotions shouldContain "EXCITED"
                            keywords shouldContain "행복한"
                            keywords shouldContain "가족"
                            sentiment shouldBe "긍정적"
                            emotionalProgression shouldBe "일관되게 행복한 감정을 유지"
                            sentenceEmotions shouldHaveSize 2
                        }
                    }
                }
            }
            
            context("with sad text") {
                it("should detect sadness appropriately") {
                    // Arrange
                    val input = EmotionAnalysisInput(
                        text = "오늘은 정말 힘든 하루였어요. 아무것도 하고 싶지 않네요."
                    )
                    
                    val mockJsonResponse = """
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
                    
                    val mockMessage = ChatMessage(
                        role = Role.Assistant,
                        content = mockJsonResponse
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockMessage
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            primaryEmotion shouldBe "SAD"
                            confidence shouldBeGreaterThan 0.8f
                            emotionalIntensity shouldBeGreaterThan 0.7f
                            subEmotions.shouldNotBeEmpty()
                            sentiment shouldBe "부정적"
                        }
                    }
                }
            }
            
            context("with complex emotions") {
                it("should detect mixed emotions") {
                    // Arrange
                    val input = EmotionAnalysisInput(
                        text = "졸업식이 끝났어요. 기쁘면서도 아쉬워요. 친구들과 헤어져야 한다니...",
                        previousEmotions = listOf("HAPPY", "NOSTALGIC")
                    )
                    
                    val mockJsonResponse = """
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
                    
                    val mockMessage = ChatMessage(
                        role = Role.Assistant,
                        content = mockJsonResponse
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockMessage
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            primaryEmotion shouldBe "NOSTALGIC"
                            subEmotions shouldHaveSize 3
                            subEmotions shouldContain "HAPPY"
                            subEmotions shouldContain "SAD"
                            sentiment shouldBe "복합적"
                            emotionalProgression shouldBe "기쁨에서 아쉬움으로 전환되는 감정"
                            sentenceEmotions shouldHaveSize 3
                        }
                    }
                }
            }
        }
        
        context("when parsing JSON response") {
            
            context("with invalid JSON") {
                it("should return default values gracefully") {
                    // Arrange
                    val input = EmotionAnalysisInput(text = "테스트 텍스트")
                    
                    val mockMessage = ChatMessage(
                        role = Role.Assistant,
                        content = "This is not a valid JSON response"
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockMessage
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            primaryEmotion shouldBe "NEUTRAL"
                            confidence shouldBe 0.5f
                            emotionalIntensity shouldBe 0.5f
                            subEmotions.size shouldBe 0
                            keywords.size shouldBe 0
                            sentiment shouldBe "중립적"
                        }
                    }
                }
            }
            
            context("with partial JSON") {
                it("should parse available fields and use defaults for missing ones") {
                    // Arrange
                    val input = EmotionAnalysisInput(text = "부분적인 감정 분석")
                    
                    val mockJsonResponse = """
                        {
                            "primaryEmotion": "CALM",
                            "confidence": 0.75,
                            "sentiment": "긍정적"
                        }
                    """.trimIndent()
                    
                    val mockMessage = ChatMessage(
                        role = Role.Assistant,
                        content = mockJsonResponse
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockMessage
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            primaryEmotion shouldBe "CALM"
                            confidence shouldBe 0.75f
                            emotionalIntensity shouldBe 0.5f  // 기본값
                            sentiment shouldBe "긍정적"
                            subEmotions.size shouldBe 0  // 기본값
                        }
                    }
                }
            }
        }
        
        context("when building prompts") {
            it("should include context when provided") {
                // Arrange
                val input = EmotionAnalysisInput(
                    text = "오늘 시험 결과가 나왔어요",
                    context = "학생이 중요한 시험 결과를 기다리고 있었음",
                    previousEmotions = listOf("ANXIOUS", "EXCITED")
                )
                
                val mockJsonResponse = """
                    {
                        "primaryEmotion": "ANXIOUS",
                        "confidence": 0.85,
                        "intensity": 0.78,
                        "subEmotions": ["EXCITED", "HOPEFUL"],
                        "keywords": ["시험", "결과"],
                        "sentiment": "긴장된"
                    }
                """.trimIndent()
                
                val mockMessage = ChatMessage(
                    role = Role.Assistant,
                    content = mockJsonResponse
                )
                
                val mockCompletion = mockk<ChatCompletion> {
                    every { choices } returns listOf(
                        mockk<ChatChoice> {
                            every { message } returns mockMessage
                        }
                    )
                }
                
                coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                
                // Act
                runTest {
                    val output = agent.process(input)
                    
                    // Assert
                    coVerify {
                        openAI.chatCompletion(match { request ->
                            val userMessage = request.messages.firstOrNull { it.role == Role.User }
                            userMessage?.content?.contains("맥락: 학생이 중요한 시험 결과를 기다리고 있었음") == true &&
                            userMessage.content?.contains("이전 감정들: ANXIOUS, EXCITED") == true
                        })
                    }
                }
            }
        }
        
        context("when emotion values are out of range") {
            it("should handle edge cases gracefully") {
                // Arrange
                val input = EmotionAnalysisInput(text = "엣지 케이스 테스트")
                
                val mockJsonResponse = """
                    {
                        "primaryEmotion": "HAPPY",
                        "confidence": 1.5,
                        "intensity": -0.5,
                        "subEmotions": [],
                        "keywords": [],
                        "sentiment": "긍정적"
                    }
                """.trimIndent()
                
                val mockMessage = ChatMessage(
                    role = Role.Assistant,
                    content = mockJsonResponse
                )
                
                val mockCompletion = mockk<ChatCompletion> {
                    every { choices } returns listOf(
                        mockk<ChatChoice> {
                            every { message } returns mockMessage
                        }
                    )
                }
                
                coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                
                // Act
                runTest {
                    val output = agent.process(input)
                    
                    // Assert
                    assertSoftly(output) {
                        // 값들이 유효한 범위 내에 있어야 함
                        confidence shouldBeLessThanOrEqual 1.0f
                        confidence shouldBeGreaterThan 0.0f
                        emotionalIntensity shouldBeGreaterThan 0.0f
                        emotionalIntensity shouldBeLessThanOrEqual 1.0f
                    }
                }
            }
        }
    }
}) 