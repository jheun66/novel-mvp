package com.novel.agents

import com.google.genai.Client
import com.google.genai.Models
import com.google.genai.types.GenerateContentResponse
import com.novel.agents.base.AgentCommunicator
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.*
import kotlinx.coroutines.test.runTest

class StoryGenerationAgentTest : DescribeSpec({

    describe("StoryGenerationAgent") {
        lateinit var agent: StoryGenerationAgent
        lateinit var geminiClient: Client
        lateinit var mockModels: Models
        lateinit var communicator: AgentCommunicator

        beforeEach {
            geminiClient = mockk<Client>()
            mockModels = mockk<Models>()
            communicator = mockk<AgentCommunicator>()

            // Client.builder() 체인을 모킹
            mockkStatic(Client::class)
            val mockBuilder = mockk<Client.Builder>()
            every { Client.builder() } returns mockBuilder
            every { mockBuilder.apiKey(any()) } returns mockBuilder
            every { mockBuilder.build() } returns geminiClient

            // final 필드인 models를 우회하는 방법
            // 1. Reflection 사용
            val modelsField = Client::class.java.getDeclaredField("models")
            modelsField.isAccessible = true
            modelsField.set(geminiClient, mockModels)

            agent = StoryGenerationAgent("test-api-key", communicator)
        }

        afterEach {
            unmockkAll()
        }

        context("when generating story") {

            context("with happy conversation context") {
                it("should generate a heartwarming story") {
                    // Arrange
                    val emotionAnalysis = EmotionAnalysisOutput(
                        primaryEmotion = "HAPPY",
                        confidence = 0.95f,
                        emotionalIntensity = 0.85f,
                        subEmotions = listOf("GRATEFUL", "EXCITED"),
                        keywords = listOf("가족", "생일", "선물"),
                        sentiment = "긍정적",
                        emotionalProgression = "점점 더 행복해지는 감정"
                    )

                    val input = StoryGenerationInput(
                        conversationContext = "오늘은 엄마의 생일이었어요. 가족들이 모두 모여 깜짝 파티를 준비했습니다.",
                        emotionAnalysis = emotionAnalysis,
                        userId = "user123",
                        conversationHighlights = listOf("엄마의 생일", "깜짝 파티", "가족 모임")
                    )

                    val mockStoryResponse = """
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

                    val mockResponse = mockk<GenerateContentResponse>()
                    every { mockResponse.text() } returns mockStoryResponse

                    coEvery {
                        mockModels.generateContent(any(), any<String>(), any())
                    } returns mockResponse

                    // Act
                    runTest {
                        val output = agent.process(input)

                        // Assert
                        assertSoftly(output) {
                            title shouldBe "엄마의 특별한 하루"
                            genre shouldBe "가족 일상"
                            emotionalArc shouldBe "기대감에서 시작해 감동과 행복으로 마무리되는 따뜻한 여정"
                            story.shouldNotBeEmpty()
                            story shouldContain "엄마의 생일"
                            story shouldContain "깜짝 파티"
                            narrativeStyle shouldBe "감성적이고 서정적인"
                            keyMoments shouldHaveSize 3
                            keyMoments shouldContain "엄마가 문을 열고 들어오시는 순간"
                        }
                    }
                }
            }

            context("with nostalgic conversation context") {
                it("should generate a reflective story") {
                    // Arrange
                    val emotionAnalysis = EmotionAnalysisOutput(
                        primaryEmotion = "NOSTALGIC",
                        confidence = 0.88f,
                        emotionalIntensity = 0.92f,
                        subEmotions = listOf("HAPPY", "SAD"),
                        keywords = listOf("졸업", "친구", "추억"),
                        sentiment = "복합적"
                    )

                    val input = StoryGenerationInput(
                        conversationContext = "고등학교 졸업식 날, 친구들과 마지막 사진을 찍었어요.",
                        emotionAnalysis = emotionAnalysis,
                        userId = "user123"
                    )

                    val mockStoryResponse = """
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

                    val mockResponse = mockk<GenerateContentResponse>()
                    every { mockResponse.text() } returns mockStoryResponse

                    coEvery {
                        mockModels.generateContent(any(), any<String>(), any())
                    } returns mockResponse

                    // Act
                    runTest {
                        val output = agent.process(input)

                        // Assert
                        assertSoftly(output) {
                            title shouldBe "마지막 교실의 햇살"
                            genre shouldBe "성장"
                            emotionalArc shouldContain "아쉬움과 그리움"
                            story shouldContain "고등학교"
                            story shouldContain "친구들"
                            keyMoments.size shouldBe 3
                        }
                    }
                }
            }
        }

        context("when parsing story response") {

            context("with malformed response") {
                it("should handle gracefully and return fallback values") {
                    // Arrange
                    val emotionAnalysis = EmotionAnalysisOutput(
                        primaryEmotion = "HAPPY",
                        confidence = 0.8f,
                        emotionalIntensity = 0.7f,
                        subEmotions = emptyList(),
                        keywords = emptyList(),
                        sentiment = "긍정적"
                    )

                    val input = StoryGenerationInput(
                        conversationContext = "테스트 컨텍스트",
                        emotionAnalysis = emotionAnalysis,
                        userId = "user123"
                    )

                    val mockResponse = mockk<GenerateContentResponse>()
                    every { mockResponse.text() } returns "This is not a properly formatted story"

                    coEvery {
                        mockModels.generateContent(any(), any<String>(), any())
                    } returns mockResponse

                    // Act
                    runTest {
                        val output = agent.process(input)

                        // Assert
                        assertSoftly(output) {
                            title shouldBe "무제"
                            genre shouldBe "일상"
                            story shouldBe "이야기를 생성하는 중 오류가 발생했습니다."
                            emotionalArc shouldBe "감정의 여정"
                            keyMoments shouldHaveSize 3
                            keyMoments shouldContain "순간의 발견"
                        }
                    }
                }
            }

            context("with partial response") {
                it("should extract available parts") {
                    // Arrange
                    val emotionAnalysis = EmotionAnalysisOutput(
                        primaryEmotion = "CALM",
                        confidence = 0.75f,
                        emotionalIntensity = 0.6f,
                        subEmotions = emptyList(),
                        keywords = emptyList(),
                        sentiment = "평온한"
                    )

                    val input = StoryGenerationInput(
                        conversationContext = "조용한 오후의 산책",
                        emotionAnalysis = emotionAnalysis,
                        userId = "user123"
                    )

                    val mockStoryResponse = """
                        제목: 오후의 산책
                        
                        ---
                        
                        조용한 오후, 나는 혼자 공원을 걸었다. 바람이 살랑살랑 불어왔다.
                        
                        ---
                    """.trimIndent()

                    val mockResponse = mockk<GenerateContentResponse>()
                    every { mockResponse.text() } returns mockStoryResponse

                    coEvery {
                        mockModels.generateContent(any(), any<String>(), any())
                    } returns mockResponse

                    // Act
                    runTest {
                        val output = agent.process(input)

                        // Assert
                        assertSoftly(output) {
                            title shouldBe "오후의 산책"
                            genre shouldBe "일상"  // 기본값
                            story shouldContain "조용한 오후"
                            story shouldContain "공원을 걸었다"
                            narrativeStyle shouldBe "감성적이고 서정적인"
                        }
                    }
                }
            }
        }

        context("when API fails") {
            it("should throw RuntimeException with appropriate message") {
                // Arrange
                val emotionAnalysis = EmotionAnalysisOutput(
                    primaryEmotion = "HAPPY",
                    confidence = 0.8f,
                    emotionalIntensity = 0.7f,
                    subEmotions = emptyList(),
                    keywords = emptyList(),
                    sentiment = "긍정적"
                )

                val input = StoryGenerationInput(
                    conversationContext = "테스트",
                    emotionAnalysis = emotionAnalysis,
                    userId = "user123"
                )

                coEvery {
                    mockModels.generateContent(any(), any<String>(), any())
                } throws Exception("API error")

                // Act & Assert
                runTest {
                    val exception = shouldThrow<RuntimeException> {
                        agent.process(input)
                    }
                    exception.message shouldContain "Story generation failed"
                }
            }

            it("should handle null response gracefully") {
                // Arrange
                val emotionAnalysis = EmotionAnalysisOutput(
                    primaryEmotion = "HAPPY",
                    confidence = 0.8f,
                    emotionalIntensity = 0.7f,
                    subEmotions = emptyList(),
                    keywords = emptyList(),
                    sentiment = "긍정적"
                )

                val input = StoryGenerationInput(
                    conversationContext = "테스트",
                    emotionAnalysis = emotionAnalysis,
                    userId = "user123"
                )

                val mockResponse = mockk<GenerateContentResponse>()
                every { mockResponse.text() } returns null

                coEvery {
                    mockModels.generateContent(any(), any<String>(), any())
                } returns mockResponse

                // Act & Assert
                runTest {
                    val exception = shouldThrow<RuntimeException> {
                        agent.process(input)
                    }
                    exception.message shouldBe "Story generation failed: Failed to generate story"
                }
            }
        }

        context("when building prompts") {
            it("should properly translate emotions to Korean") {
                // Arrange
                val testCases = listOf(
                    "HAPPY" to "행복",
                    "SAD" to "슬픔",
                    "EXCITED" to "설렘",
                    "CALM" to "평온",
                    "ANGRY" to "화남",
                    "GRATEFUL" to "감사",
                    "ANXIOUS" to "불안",
                    "NOSTALGIC" to "그리움",
                    "UNKNOWN" to "중립"
                )

                testCases.forEach { (emotion, expectedKorean) ->
                    val emotionAnalysis = EmotionAnalysisOutput(
                        primaryEmotion = emotion,
                        confidence = 0.8f,
                        emotionalIntensity = 0.7f,
                        subEmotions = emptyList(),
                        keywords = emptyList(),
                        sentiment = "중립적"
                    )

                    val input = StoryGenerationInput(
                        conversationContext = "테스트",
                        emotionAnalysis = emotionAnalysis,
                        userId = "user123"
                    )

                    val mockResponse = mockk<GenerateContentResponse>()
                    every { mockResponse.text() } returns "제목: 테스트\n---\n스토리\n---"

                    coEvery {
                        mockModels.generateContent(any(), any<String>(), any())
                    } returns mockResponse

                    // Act
                    runTest {
                        agent.process(input)

                        // Assert
                        coVerify {
                            mockModels.generateContent(
                                any(),
                                match<String> { prompt ->
                                    prompt.contains("주요 감정: $expectedKorean")
                                },
                                any()
                            )
                        }
                    }
                }
            }
        }
    }
})