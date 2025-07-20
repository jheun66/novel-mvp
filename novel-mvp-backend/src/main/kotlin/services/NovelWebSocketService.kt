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
        val format: String = "wav",
        val sampleRate: Int = 16000,
        val conversationId: String = UUID.randomUUID().toString(),
        val isStreaming: Boolean = false  // For real-time streaming
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
        val format: String = "wav",
        val sampleRate: Int = 22050,
        val emotion: String? = null,
        val duration: Float? = null,  // Audio duration in seconds
        val audioType: String = "chat"  // "chat" or "story"
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
        val emotionalArc: String,
        val audioData: String? = null,  // Base64 encoded audio for story narration
        val audioFormat: String = "wav",
        val audioSampleRate: Int = 22050
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
    
    @Serializable
    @SerialName("AudioEchoTest")
    data class AudioEchoTest(
        val audioData: String,  // Base64 encoded audio to echo back
        val conversationId: String = UUID.randomUUID().toString()
    ) : WebSocketMessage()
}

class NovelWebSocketService(
    private val conversationAgent: ConversationAgent,
    private val emotionAnalysisAgent: EmotionAnalysisAgent,
    private val storyGenerationAgent: StoryGenerationAgent,
    private val fishSpeechService: FishSpeechService,
    private val whisperSTTService: WhisperSTTService,
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
                        is WebSocketMessage.AudioEchoTest -> {
                            handleAudioEchoTest(message, outgoing, user)
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
        try {
            // Convert Base64 audio data to ByteArray
            val audioBytes = try {
                java.util.Base64.getDecoder().decode(message.audioData)
            } catch (e: Exception) {
                logger.error("Failed to decode Base64 audio data", e)
                outgoing.send(WebSocketMessage.Error("오디오 데이터 형식이 올바르지 않습니다."))
                return
            }
            
            logger.info("Processing audio message - Size: ${audioBytes.size} bytes, Format: ${message.format}, Sample Rate: ${message.sampleRate}")
            
            // Validate audio data
            if (audioBytes.isEmpty()) {
                logger.error("Decoded audio data is empty")
                outgoing.send(WebSocketMessage.Error("오디오 데이터가 비어있습니다."))
                return
            }
            
            // Log audio header for debugging
            val audioHeader = audioBytes.take(12).joinToString(" ") { "%02x".format(it) }
            logger.debug("Audio header: $audioHeader")
            
            // Transcribe audio using Whisper STT service
            val transcriptionResult = try {
                whisperSTTService.transcribeAudio(
                    audioBytes = audioBytes,
                    language = "ko",
                    enableTimestamps = false
                )
            } catch (e: Exception) {
                logger.error("Whisper transcription failed: ${e.message}", e)
                outgoing.send(WebSocketMessage.Error("음성 인식 서비스에 오류가 발생했습니다: ${e.message}"))
                return
            }
            
            val transcribedText = transcriptionResult.text
            logger.info("Transcription result: '$transcribedText' (${transcribedText.length} chars)")
            
            if (transcribedText.isBlank()) {
                outgoing.send(WebSocketMessage.Error("음성을 인식할 수 없습니다. 더 명확하게 말씀해주세요. (오디오가 너무 짧거나 조용할 수 있습니다)"))
                return
            }
            
            // Process the transcribed text as a regular text input
            val textInput = WebSocketMessage.TextInput(
                text = transcribedText,
                conversationId = message.conversationId
            )
            
            handleTextInput(textInput, outgoing, user)
            
        } catch (e: Exception) {
            logger.error("Error processing audio input", e)
            outgoing.send(WebSocketMessage.Error("음성 처리 중 오류가 발생했습니다: ${e.message}"))
        }
    }
    
    private suspend fun handleAudioEchoTest(
        message: WebSocketMessage.AudioEchoTest,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        try {
            logger.info("Audio echo test requested by user ${user.userId} - Audio size: ${message.audioData.length} chars (Base64)")
            
            // Simply echo back the audio data as an AudioOutput message
            val audioOutput = WebSocketMessage.AudioOutput(
                audioData = message.audioData,
                format = "wav",
                sampleRate = 16000,
                emotion = "neutral",
                duration = 2.0f, // Estimate 2 seconds
                audioType = "echo_test"
            )
            
            outgoing.send(audioOutput)
            logger.info("Audio echo test completed - sent back ${message.audioData.length} chars of audio data")
            
        } catch (e: Exception) {
            logger.error("Error processing audio echo test", e)
            outgoing.send(WebSocketMessage.Error("오디오 에코 테스트 중 오류가 발생했습니다: ${e.message}"))
        }
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
        
        // 4. Generate TTS with emotion using Fish Speech
        try {
            val ttsResult = fishSpeechService.generateChatTTS(
                text = conversationResult.response,
                emotion = conversationResult.emotion ?: "NEUTRAL",
                speed = 1.2f
            )
            
            if (ttsResult.isSuccess) {
                val audioData = ttsResult.getOrNull()
                if (audioData != null) {
                    outgoing.send(WebSocketMessage.AudioOutput(
                        audioData = Base64.getEncoder().encodeToString(audioData),
                        emotion = conversationResult.emotion,
                        audioType = "chat"
                    ))
                }
            } else {
                logger.error("Failed to generate chat TTS: ${ttsResult.exceptionOrNull()?.message}")
            }
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
        
        // 4. Generate story narration TTS
        var storyAudioData: String? = null
        try {
            val storyTTSResult = fishSpeechService.generateStoryTTS(
                text = storyResult.story,
                emotion = emotionResult.primaryEmotion,
                speed = 1.0f,
                addPauses = true
            )
            
            if (storyTTSResult.isSuccess) {
                val audioData = storyTTSResult.getOrNull()
                if (audioData != null) {
                    storyAudioData = Base64.getEncoder().encodeToString(audioData)
                }
            } else {
                logger.error("Failed to generate story TTS: ${storyTTSResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            logger.error("Failed to generate story TTS", e)
        }

        // 5. Send story with optional audio
        outgoing.send(WebSocketMessage.StoryOutput(
            title = storyResult.title,
            content = storyResult.story,
            emotion = emotionResult.primaryEmotion,
            genre = storyResult.genre,
            emotionalArc = storyResult.emotionalArc,
            audioData = storyAudioData
        ))
        
        // 6. Publish domain event for story generation
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
