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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.*
import kotlinx.coroutines.test.runTest

class ConversationAgentTest : DescribeSpec({
    
    describe("ConversationAgent") {
        lateinit var agent: ConversationAgent
        lateinit var openAI: OpenAI
        lateinit var communicator: AgentCommunicator
        
        beforeEach {
            openAI = mockk<OpenAI>()
            communicator = mockk<AgentCommunicator>()
            
            // OpenAI 생성자를 모킹하여 목 객체를 반환하도록 설정
            mockkConstructor(OpenAI::class)
            every { anyConstructed<OpenAI>() } returns openAI
            
            agent = ConversationAgent("test-api-key", communicator)
        }
        
        afterEach {
            unmockkAll()
        }
        
        context("when processing user input") {
            
            context("with a new conversation") {
                it("should create a new conversation context") {
                    // Arrange
                    val input = ConversationInput(
                        userId = "user123",
                        message = "안녕하세요, 오늘 정말 좋은 일이 있었어요!",
                        conversationId = "conv123"
                    )
                    
                    val mockResponse = ChatMessage(
                        role = Role.Assistant,
                        content = "안녕하세요! 정말 좋은 일이 있으셨군요! 어떤 일이었는지 자세히 들려주시겠어요?"
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockResponse
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            response shouldBe "안녕하세요! 정말 좋은 일이 있으셨군요! 어떤 일이었는지 자세히 들려주시겠어요?"
                            emotion shouldBe null
                            shouldGenerateStory shouldBe false
                            collectedContext shouldBe null
                            suggestedQuestions shouldHaveSize 3
                        }
                    }
                }
            }
            
            context("when emotion is detected") {
                it("should extract emotion tag and send to emotion agent") {
                    // Arrange
                    val input = ConversationInput(
                        userId = "user123",
                        message = "너무 행복해서 눈물이 날 것 같아요",
                        conversationId = "conv123"
                    )
                    
                    val mockResponse = ChatMessage(
                        role = Role.Assistant,
                        content = "[EMOTION: HAPPY] 정말 행복하신 순간이군요! 눈물이 날 정도로 기쁘신 일이 무엇이었나요?"
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockResponse
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    coEvery { communicator.send<Any>(any()) } just Runs
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            response shouldNotContain "[EMOTION:"
                            response shouldContain "정말 행복하신 순간이군요"
                            emotion shouldBe "HAPPY"
                        }
                        
                        // Verify emotion agent notification
                        coVerify {
                            communicator.send<Any>(match {
                                it.to == "emotion-analysis"
                            })
                        }
                    }
                }
            }
            
            context("when ready for story generation") {
                it("should indicate story readiness and provide collected context") {
                    // Arrange
                    val input = ConversationInput(
                        userId = "user123",
                        message = "그래서 오늘은 정말 의미 있는 하루였어요",
                        conversationId = "conv123"
                    )
                    
                    val mockResponse = ChatMessage(
                        role = Role.Assistant,
                        content = "[READY_FOR_STORY] [CONTEXT: 사용자는 오늘 특별한 경험을 했고, 가족과 함께 소중한 시간을 보냈다] 오늘 하루 정말 의미 있으셨네요. 이 이야기를 아름다운 이야기로 만들어볼까요?"
                    )
                    
                    val mockCompletion = mockk<ChatCompletion> {
                        every { choices } returns listOf(
                            mockk<ChatChoice> {
                                every { message } returns mockResponse
                            }
                        )
                    }
                    
                    coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                    
                    // Act
                    runTest {
                        val output = agent.process(input)
                        
                        // Assert
                        assertSoftly(output) {
                            response shouldNotContain "[READY_FOR_STORY]"
                            response shouldNotContain "[CONTEXT:"
                            shouldGenerateStory shouldBe true
                            collectedContext.shouldNotBeNull()
                            collectedContext shouldContain "사용자는 오늘 특별한 경험을 했고"
                        }
                    }
                }
            }
        }
        
        context("when managing conversation context") {
            it("should maintain conversation history") {
                // Arrange
                val conversationId = "conv123"
                val input1 = ConversationInput(
                    userId = "user123",
                    message = "첫 번째 메시지",
                    conversationId = conversationId
                )
                val input2 = ConversationInput(
                    userId = "user123",
                    message = "두 번째 메시지",
                    conversationId = conversationId
                )
                
                val mockResponse = ChatMessage(
                    role = Role.Assistant,
                    content = "응답"
                )
                
                val mockCompletion = mockk<ChatCompletion> {
                    every { choices } returns listOf(
                        mockk<ChatChoice> {
                            every { message } returns mockResponse
                        }
                    )
                }
                
                coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                
                // Act & Assert
                runTest {
                    agent.process(input1)
                    agent.process(input2)
                    
                    // Verify that conversation history is maintained
                    coVerify(exactly = 2) {
                        openAI.chatCompletion(match { request ->
                            request.messages.any { it.content == "첫 번째 메시지" } &&
                            (request.messages.size > 2 || request.messages.any { it.content == "두 번째 메시지" })
                        })
                    }
                }
            }
            
            it("should clear context when requested") {
                // Arrange
                val conversationId = "conv123"
                val input = ConversationInput(
                    userId = "user123",
                    message = "메시지",
                    conversationId = conversationId
                )
                
                val mockResponse = ChatMessage(
                    role = Role.Assistant,
                    content = "응답"
                )
                
                val mockCompletion = mockk<ChatCompletion> {
                    every { choices } returns listOf(
                        mockk<ChatChoice> {
                            every { message } returns mockResponse
                        }
                    )
                }
                
                coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                
                // Act
                runTest {
                    agent.process(input)
                    agent.clearContext(conversationId)
                    agent.process(input)
                    
                    // Assert - 두 번째 호출에서는 이전 대화 내용이 없어야 함
                    coVerify(exactly = 2) {
                        openAI.chatCompletion(match { request ->
                            // 첫 번째와 두 번째 호출 모두 같은 수의 메시지를 가져야 함 (컨텍스트가 초기화되었으므로)
                            request.messages.size == 2 // System + User
                        })
                    }
                }
            }
        }
        
        context("when generating suggested questions") {
            it("should provide contextual questions based on turn count") {
                // Arrange
                val input = ConversationInput(
                    userId = "user123",
                    message = "오늘 친구를 만났어요",
                    conversationId = "conv123"
                )
                
                val mockResponse = ChatMessage(
                    role = Role.Assistant,
                    content = "친구를 만나셨군요! 오랜만에 만나신 건가요?"
                )
                
                val mockCompletion = mockk<ChatCompletion> {
                    every { choices } returns listOf(
                        mockk<ChatChoice> {
                            every { message } returns mockResponse
                        }
                    )
                }
                
                coEvery { openAI.chatCompletion(any()) } returns mockCompletion
                
                // Act
                runTest {
                    val output = agent.process(input)
                    
                    // Assert
                    assertSoftly {
                        output.suggestedQuestions shouldHaveSize 3
                        output.suggestedQuestions shouldContain "그 때 어떤 기분이 드셨나요?"
                    }
                }
            }
        }
    }
}) 