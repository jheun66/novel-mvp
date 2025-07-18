package com.novel.services

import com.novel.agents.*
import com.novel.agents.base.AgentCommunicator
import com.novel.application.user.CheckStoryGenerationEligibilityUseCase
import com.novel.application.user.GetUserUseCase
import com.novel.auth.AuthenticatedUser
import com.novel.auth.WebSocketAuth
import com.novel.domain.user.UserDomainEvent
import com.novel.globalJson
import com.novel.application.user.DomainEventPublisher
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

@Serializable
sealed class WebSocketMessage {
    @Serializable
    @SerialName("AudioInput")
    data class AudioInput(
        val audioData: String,  // Base64 encoded audio
        val format: String = "pcm16"
    ) : WebSocketMessage()

    @Serializable
    @SerialName("TextInput")
    data class TextInput(
        val text: String,
        val conversationId: String = UUID.randomUUID().toString()
    ) : WebSocketMessage()

    @Serializable
    @SerialName("GenerateStory")
    data class GenerateStory(
        val conversationId: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("AudioOutput")
    data class AudioOutput(
        val audioData: String,  // Base64 encoded audio
        val format: String = "pcm16",
        val emotion: String? = null
    ) : WebSocketMessage()

    @Serializable
    @SerialName("TextOutput")
    data class TextOutput(
        val text: String,
        val emotion: String? = null,
        val suggestedQuestions: List<String> = emptyList(),
        val readyForStory: Boolean = false
    ) : WebSocketMessage()

    @Serializable
    @SerialName("StoryOutput")
    data class StoryOutput(
        val title: String,
        val content: String,
        val emotion: String,
        val genre: String,
        val emotionalArc: String
    ) : WebSocketMessage()

    @Serializable
    @SerialName("Error")
    data class Error(
        val message: String,
        val code: String? = null
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("AuthRequest")
    data class AuthRequest(
        val token: String
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("AuthResponse")
    data class AuthResponse(
        val success: Boolean,
        val message: String? = null
    ) : WebSocketMessage()
}

class NovelWebSocketService(
    private val conversationAgent: ConversationAgent,
    private val emotionAnalysisAgent: EmotionAnalysisAgent,
    private val storyGenerationAgent: StoryGenerationAgent,
    private val speechService: ElevenLabsService,
    private val communicator: AgentCommunicator
) : KoinComponent {
    private val logger = LoggerFactory.getLogger(NovelWebSocketService::class.java)
    
    // Inject use cases
    private val getUserUseCase: GetUserUseCase by inject()
    private val checkStoryGenerationEligibilityUseCase: CheckStoryGenerationEligibilityUseCase by inject()
    private val eventPublisher: DomainEventPublisher by inject()
    
    private val webSocketAuth = WebSocketAuth()
    private val conversationContexts = mutableMapOf<String, ConversationContext>()
    
    suspend fun handleWebSocketSession(session: DefaultWebSocketSession, token: String) {
        // Authenticate immediately with provided token
        var authenticatedUser: AuthenticatedUser? = webSocketAuth.authenticateWebSocket(session, token)
        
        // Check if authentication was successful
        if (authenticatedUser == null) {
            logger.warn("WebSocket authentication failed for token")
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication failed"))
            return
        }
        
        logger.info("WebSocket authenticated for user: ${authenticatedUser.userId}")
        
        // Create channels for bidirectional communication
        val incoming = Channel<WebSocketMessage>(Channel.BUFFERED)
        val outgoing = Channel<WebSocketMessage>(Channel.BUFFERED)
        
        try {
            coroutineScope {
                // Launch coroutine for processing incoming WebSocket frames
                launch {
                    try {
                        for (frame in session.incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    try {
                                        val text = frame.readText()
                                        val message = globalJson.decodeFromString<WebSocketMessage>(text)
                                        
                                        // Process messages directly since authentication is already done
                                        incoming.send(message)
                                    } catch (e: Exception) {
                                        logger.error("Failed to parse message", e)
                                        outgoing.send(WebSocketMessage.Error("Invalid message format"))
                                    }
                                }
                                is Frame.Binary -> {
                                    // Handle binary audio data directly
                                    val audioData = frame.readBytes()
                                    incoming.send(WebSocketMessage.AudioInput(
                                        audioData = Base64.getEncoder().encodeToString(audioData),
                                        format = "pcm16"
                                    ))
                                }
                                else -> {}
                            }
                        }
                    } finally {
                        incoming.close()
                    }
                }
                
                // Launch coroutine for handling incoming messages
                launch { 
                    try {
                        handleIncomingMessages(incoming, outgoing, authenticatedUser)
                    } finally {
                        outgoing.close()
                    }
                }
                
                // Launch coroutine for sending outgoing messages
                launch { 
                    sendOutgoingMessages(session, outgoing) 
                }
            }
        } catch (e: Exception) {
            logger.error("WebSocket error: ${e.message}", e)
        } finally {
            logger.info("WebSocket disconnected: ${authenticatedUser?.userId}")
            // Clean up any remaining conversation contexts for this user
            authenticatedUser?.let { user ->
                conversationContexts.entries.removeIf { it.value.userId == user.userId }
            }
        }
    }
    
    private suspend fun handleIncomingMessages(
        incoming: Channel<WebSocketMessage>,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        try {
            for (message in incoming) {
                try {
                    when (message) {
                        is WebSocketMessage.AudioInput -> {
                            handleAudioInput(message, outgoing, user)
                        }
                        is WebSocketMessage.TextInput -> {
                            handleTextInput(message, outgoing, user)
                        }
                        is WebSocketMessage.GenerateStory -> {
                            handleGenerateStory(message, outgoing, user)
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    logger.error("Error handling message", e)
                    try {
                        outgoing.send(WebSocketMessage.Error("처리 중 오류가 발생했습니다: ${e.message}"))
                    } catch (sendError: Exception) {
                        logger.error("Failed to send error message", sendError)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
            logger.debug("Incoming channel closed for user: ${user.userId}")
        } catch (e: Exception) {
            logger.error("Unexpected error in handleIncomingMessages", e)
        }
    }
    
    private suspend fun handleAudioInput(
        message: WebSocketMessage.AudioInput,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        // ElevenLabs는 STT를 지원하지 않으므로 에러 메시지 반환
        outgoing.send(WebSocketMessage.Error("음성 입력은 현재 지원되지 않습니다. 텍스트로 입력해주세요."))
        return
    }
    
    private suspend fun handleTextInput(
        message: WebSocketMessage.TextInput,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        // Get user profile for personalization
        val userProfile = try {
            getUserUseCase.execute(user.userId)
        } catch (e: Exception) {
            logger.error("Failed to fetch user profile", e)
            null
        }
        
        // 1. Conversation processing with user context
        val conversationResult = conversationAgent.process(
            ConversationInput(
                userId = user.userId,
                message = message.text,
                conversationId = message.conversationId,
                userProfile = userProfile?.let {
                    UserProfile(
                        name = it.displayName,
                        personalityTraits = it.personalityProfile?.traits ?: emptyMap(),
                        preferredGenres = it.personalityProfile?.preferredGenres ?: emptySet()
                    )
                }
            )
        )
        
        // 2. Store context for story generation
        val context = conversationContexts.getOrPut(message.conversationId) {
            ConversationContext(user.userId, message.conversationId)
        }
        context.messages.add(ChatMessage(ChatRole.User, message.text))
        context.messages.add(ChatMessage(ChatRole.Assistant, conversationResult.response))
        
        // 3. Send text response
        outgoing.send(WebSocketMessage.TextOutput(
            text = conversationResult.response,
            emotion = conversationResult.emotion,
            suggestedQuestions = conversationResult.suggestedQuestions,
            readyForStory = conversationResult.shouldGenerateStory
        ))
        
        // 4. Generate TTS with emotion using ElevenLabs
        try {
            val audioData = speechService.textToSpeech(
                text = conversationResult.response,
                emotion = conversationResult.emotion ?: "NEUTRAL",
                voice = userProfile?.let { 
                    // Select voice based on user preference (future feature)
                    "korean-female-1" 
                } ?: "korean-female-1"
            )
            
            outgoing.send(WebSocketMessage.AudioOutput(
                audioData = Base64.getEncoder().encodeToString(audioData),
                emotion = conversationResult.emotion
            ))
        } catch (e: Exception) {
            logger.error("Failed to generate TTS", e)
            // Continue without audio if TTS fails
        }
    }
    
    private suspend fun handleGenerateStory(
        message: WebSocketMessage.GenerateStory,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        // Check if user can generate story
        val canGenerate = try {
            checkStoryGenerationEligibilityUseCase.execute(user.userId)
        } catch (e: Exception) {
            logger.error("Failed to check story generation eligibility", e)
            false
        }
        
        if (!canGenerate) {
            outgoing.send(WebSocketMessage.Error(
                message = "일일 스토리 생성 한도를 초과했습니다. 프리미엄 구독을 고려해보세요.",
                code = "STORY_LIMIT_EXCEEDED"
            ))
            return
        }
        
        val context = conversationContexts[message.conversationId]
        if (context == null) {
            outgoing.send(WebSocketMessage.Error("대화 내용을 찾을 수 없습니다"))
            return
        }
        
        // Get user profile for personalized story generation
        val userProfile = try {
            getUserUseCase.execute(user.userId)
        } catch (e: Exception) {
            logger.error("Failed to fetch user profile for story generation", e)
            null
        }
        
        // 1. Collect all conversation content
        val conversationText = context.messages.joinToString("\n") { msg ->
            when (msg.role) {
                ChatRole.User -> "사용자: ${msg.content}"
                ChatRole.Assistant -> "AI: ${msg.content}"
                else -> msg.content
            }
        }
        
        // 2. Emotion analysis
        val emotionResult = emotionAnalysisAgent.process(
            EmotionAnalysisInput(
                text = conversationText,
                context = "일상 대화"
            )
        )
        
        // 3. Story generation with user preferences
        val storyResult = storyGenerationAgent.process(
            StoryGenerationInput(
                conversationContext = conversationText,
                emotionAnalysis = emotionResult,
                userId = user.userId,
                conversationHighlights = extractHighlights(context),
                userPreferences = userProfile?.personalityProfile?.let {
                    StoryPreferences(
                        preferredGenres = it.preferredGenres.map { genre -> genre },
                        personalityTraits = it.traits
                    )
                }
            )
        )
        
        // 4. Send story
        outgoing.send(WebSocketMessage.StoryOutput(
            title = storyResult.title,
            content = storyResult.story,
            emotion = emotionResult.primaryEmotion,
            genre = storyResult.genre,
            emotionalArc = storyResult.emotionalArc
        ))
        
        // 5. Publish domain event for story generation
        try {
            eventPublisher.publish(
                UserDomainEvent.StoryGenerated(
                    userId = com.novel.domain.user.UserId(UUID.fromString(user.userId)),
                    storyId = UUID.randomUUID()
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to publish story generation event", e)
        }
        
        // Clean up context
        conversationContexts.remove(message.conversationId)
    }
    
    private fun extractHighlights(context: ConversationContext): List<String> {
        // Extract key moments from conversation
        return context.messages
            .filter { it.role == ChatRole.User }
            .map { it.content }
            .filter { it.length > 30 }  // Meaningful content
            .take(5)
    }
    
    private suspend fun sendOutgoingMessages(
        session: DefaultWebSocketSession,
        outgoing: Channel<WebSocketMessage>
    ) {
        try {
            for (message in outgoing) {
                val jsonMessage = globalJson.encodeToString(WebSocketMessage.serializer(), message)
                session.send(Frame.Text(jsonMessage))
            }
        } catch (e: kotlinx.coroutines.channels.ClosedSendChannelException) {
            logger.debug("Outgoing channel closed for WebSocket session.")
        } catch (e: Exception) {
            logger.error("Unexpected error in sendOutgoingMessages", e)
        }
    }
}

// Helper data classes
data class ConversationContext(
    val userId: String,
    val conversationId: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val emotions: MutableList<String> = mutableListOf(),
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val role: ChatRole,
    val content: String
)

enum class ChatRole {
    User, Assistant, System
}
