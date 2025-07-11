package com.novel.integration

import com.novel.agents.*
import com.novel.agents.base.AgentCommunicator
import com.novel.mocks.MockServiceFactory
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class FullFlowIntegrationTest : DescribeSpec({
    
    describe("Full Conversation to Story Flow") {
        
        lateinit var conversationAgent: ConversationAgent
        lateinit var emotionAnalysisAgent: EmotionAnalysisAgent
        lateinit var storyGenerationAgent: StoryGenerationAgent
        lateinit var communicator: AgentCommunicator
        
        beforeEach {
            MockServiceFactory.setupAllMocks()
            communicator = mockk<AgentCommunicator>(relaxed = true)
            
            conversationAgent = ConversationAgent("test-api-key", communicator)
            emotionAnalysisAgent = EmotionAnalysisAgent("test-api-key", communicator)
            storyGenerationAgent = StoryGenerationAgent("test-api-key", communicator)
        }
        
        afterEach {
            MockServiceFactory.teardownAllMocks()
        }
        
        context("with complete happy conversation flow") {
            
            it("should process conversation, analyze emotions, and generate story") {
                runTest {
                    val conversationId = "test-conv-123"
                    val userId = "test-user-123"
                    
                    // Step 1: Start conversation
                    val input1 = ConversationInput(
                        userId = userId,
                        message = "안녕하세요! 오늘 정말 행복한 일이 있었어요.",
                        conversationId = conversationId
                    )
                    
                    val response1 = conversationAgent.process(input1)
                    
                    assertSoftly(response1) {
                        response.shouldNotBeEmpty()
                        suggestedQuestions shouldHaveSize 3
                        emotion shouldBe "HAPPY"
                    }
                    
                    // Step 2: Continue conversation
                    val input2 = ConversationInput(
                        userId = userId,
                        message = "가족들과 오랜만에 모여서 함께 식사를 했어요. 정말 따뜻했어요.",
                        conversationId = conversationId
                    )
                    
                    val response2 = conversationAgent.process(input2)
                    
                    assertSoftly(response2) {
                        response.shouldNotBeEmpty()
                        shouldGenerateStory shouldBe false
                    }
                    
                    // Step 3: Complete conversation
                    val input3 = ConversationInput(
                        userId = userId,
                        message = "이런 시간들이 정말 소중하다는 걸 느꼈어요. 오늘 하루가 완성된 것 같아요.",
                        conversationId = conversationId
                    )
                    
                    val response3 = conversationAgent.process(input3)
                    
                    assertSoftly(response3) {
                        response.shouldNotBeEmpty()
                        shouldGenerateStory shouldBe true
                        collectedContext.shouldNotBeNull()
                    }
                    
                    // Step 4: Analyze emotions
                    val emotionInput = EmotionAnalysisInput(
                        text = response3.collectedContext!!,
                        context = "가족 모임에 대한 대화"
                    )
                    
                    val emotionOutput = emotionAnalysisAgent.process(emotionInput)
                    
                    assertSoftly(emotionOutput) {
                        // 필수 필드가 존재하고 유효한 범위인지 검증
                        primaryEmotion.shouldNotBeEmpty()
                        confidence shouldBeGreaterThan 0.0f
                        confidence shouldBeLessThanOrEqual 1.0f
                        emotionalIntensity shouldBeGreaterThanOrEqual 0.0f
                        emotionalIntensity shouldBeLessThanOrEqual 1.0f
                        sentiment.shouldNotBeEmpty()
                        
                        // 키워드가 입력 텍스트와 관련이 있는지만 확인
                        keywords.shouldNotBeEmpty()
                    }
                    
                    // Step 5: Generate story
                    val storyInput = StoryGenerationInput(
                        conversationContext = response3.collectedContext,
                        emotionAnalysis = emotionOutput,
                        userId = userId,
                        conversationHighlights = listOf("가족 모임", "따뜻한 시간", "소중한 순간")
                    )
                    
                    val storyOutput = storyGenerationAgent.process(storyInput)
                    
                    assertSoftly(storyOutput) {
                        // 필수 필드들이 존재하고 유효한지 검증
                        title.shouldNotBeEmpty()
                        story.shouldNotBeEmpty()
                        genre.shouldNotBeEmpty()
                        narrativeStyle.shouldNotBeEmpty()
                        emotionalArc.shouldNotBeEmpty()
                        keyMoments.shouldNotBeEmpty()
                        
                        // 스토리가 입력 컨텍스트를 반영하는지 검증
                        story shouldContain "가족"  // 대화 내용이 반영되었는지만 확인
                    }
                }
            }
        }
        
        context("with mixed emotions conversation flow") {
            
            it("should handle complex emotional journey") {
                runTest {
                    val conversationId = "test-conv-456"
                    val userId = "test-user-456"
                    
                    // Conversation with mixed emotions
                    val inputs = listOf(
                        "오늘 회사에서 정말 힘든 일이 있었어요.",
                        "하지만 동료들이 위로해줘서 조금 나아졌어요.",
                        "집에 와서 가족들과 시간을 보내니 마음이 편안해졌어요. 이제 마무리할게요."
                    )
                    
                    var lastResponse = conversationAgent.process(
                        ConversationInput(userId, inputs[0], conversationId)
                    )
                    
                    for (i in 1 until inputs.size) {
                        lastResponse = conversationAgent.process(
                            ConversationInput(userId, inputs[i], conversationId)
                        )
                    }
                    
                    // Should be ready for story
                    lastResponse.shouldGenerateStory shouldBe true
                    lastResponse.collectedContext.shouldNotBeNull()
                    
                    // Safe cast를 위해 변수에 저장
                    val collectedContext = lastResponse.collectedContext ?: throw AssertionError("Collected context should not be null")
                    
                    // Analyze mixed emotions
                    val emotionOutput = emotionAnalysisAgent.process(
                        EmotionAnalysisInput(
                            text = collectedContext,
                            previousEmotions = listOf("SAD", "GRATEFUL", "CALM")
                        )
                    )
                    
                    // Generate story with mixed emotions
                    val storyOutput = storyGenerationAgent.process(
                        StoryGenerationInput(
                            conversationContext = collectedContext,
                            emotionAnalysis = emotionOutput,
                            userId = userId
                        )
                    )
                    
                    assertSoftly(storyOutput) {
                        story.shouldNotBeEmpty()
                        emotionalArc shouldContain "여정"
                        keyMoments.size shouldBe 3
                    }
                }
            }
        }
        
        context("with error scenarios") {
            
            it("should handle API failures gracefully") {
                runTest {
                    // Mock을 먼저 설정
                    MockServiceFactory.teardownAllMocks()
                    MockServiceFactory.setupFailureMocks()
                    
                    // Mock 설정 후 새로운 agent 생성
                    val failingConversationAgent = ConversationAgent("test-api-key", communicator)
                    
                    try {
                        val input = ConversationInput(
                            userId = "user123",
                            message = "테스트 메시지",
                            conversationId = "conv123"
                        )
                        
                        failingConversationAgent.process(input)
                        
                        // Should not reach here
                        throw AssertionError("Expected exception was not thrown")
                    } catch (e: Exception) {
                        e.message shouldContain "OpenAI API Error"
                    }
                }
            }
        }
        
        context("with performance considerations") {
            
            beforeEach {
                MockServiceFactory.setupDelayedMocks(500)
            }
            
            it("should complete full flow within reasonable time") {
                runTest {
                    val startTime = System.currentTimeMillis()
                    
                    // Run abbreviated flow
                    val conversationId = "perf-test"
                    val userId = "perf-user"
                    
                    val convResponse = conversationAgent.process(
                        ConversationInput(
                            userId = userId,
                            message = "빠른 테스트를 위한 메시지입니다. 완성했어요.",
                            conversationId = conversationId
                        )
                    )
                    
                    if (convResponse.shouldGenerateStory && convResponse.collectedContext != null) {
                        val emotionOutput = emotionAnalysisAgent.process(
                            EmotionAnalysisInput(text = convResponse.collectedContext)
                        )
                        
                        storyGenerationAgent.process(
                            StoryGenerationInput(
                                conversationContext = convResponse.collectedContext,
                                emotionAnalysis = emotionOutput,
                                userId = userId
                            )
                        )
                    }
                    
                    val duration = System.currentTimeMillis() - startTime
                    
                    // Should complete within 3 seconds even with delays
                    (duration < 3000) shouldBe true
                }
            }
        }
    }
}) 