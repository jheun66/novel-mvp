package com.novel.services

import com.novel.agents.*
import com.novel.agents.base.AgentCommunicator
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

@Serializable
sealed class WebSocketMessage {
    @Serializable
    data class AudioInput(
        val audioData: String,  // Base64 encoded audio
        val format: String = "pcm16"
    ) : WebSocketMessage()
    
    @Serializable
    data class TextInput(
        val text: String,
        val conversationId: String = UUID.randomUUID().toString()
    ) : WebSocketMessage()
    
    @Serializable
    data class GenerateStory(
        val conversationId: String
    ) : WebSocketMessage()
    
    @Serializable
    data class AudioOutput(
        val audioData: String,  // Base64 encoded audio
        val format: String = "pcm16",
        val emotion: String? = null
    ) : WebSocketMessage()
    
    @Serializable
    data class TextOutput(
        val text: String,
        val emotion: String? = null,
        val suggestedQuestions: List<String> = emptyList(),
        val readyForStory: Boolean = false
    ) : WebSocketMessage()
    
    @Serializable
    data class StoryOutput(
        val title: String,
        val content: String,
        val emotion: String,
        val genre: String,
        val emotionalArc: String
    ) : WebSocketMessage()
    
    @Serializable
    data class Error(
        val message: String,
        val code: String? = null
    ) : WebSocketMessage()
}

class NovelWebSocketService(
    private val conversationAgent: ConversationAgent,
    private val emotionAnalysisAgent: EmotionAnalysisAgent,
    private val storyGenerationAgent: StoryGenerationAgent,
    private val speechService: ElevenLabsService,
    private val communicator: AgentCommunicator
) {
    private val logger = LoggerFactory.getLogger(NovelWebSocketService::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val conversationContexts = mutableMapOf<String, ConversationContext>()
    
    suspend fun handleWebSocketSession(session: DefaultWebSocketSession) {
        val userId = "user-${UUID.randomUUID()}"
        logger.info("WebSocket connected: $userId")
        
        try {
            // Create channels for bidirectional communication
            val incoming = Channel<WebSocketMessage>(Channel.BUFFERED)
            val outgoing = Channel<WebSocketMessage>(Channel.BUFFERED)
            
            // Launch coroutines for handling messages
            coroutineScope {
                launch { handleIncomingMessages(incoming, outgoing, userId) }
                launch { sendOutgoingMessages(session, outgoing) }
            }
            
            // Process incoming WebSocket frames
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val text = frame.readText()
                            val message = json.decodeFromString<WebSocketMessage>(text)
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
        } catch (e: Exception) {
            logger.error("WebSocket error", e)
        } finally {
            logger.info("WebSocket disconnected: $userId")
        }
    }
    
    private suspend fun handleIncomingMessages(
        incoming: Channel<WebSocketMessage>,
        outgoing: Channel<WebSocketMessage>,
        userId: String
    ) {
        for (message in incoming) {
            try {
                when (message) {
                    is WebSocketMessage.AudioInput -> {
                        handleAudioInput(message, outgoing, userId)
                    }
                    is WebSocketMessage.TextInput -> {
                        handleTextInput(message, outgoing, userId)
                    }
                    is WebSocketMessage.GenerateStory -> {
                        handleGenerateStory(message, outgoing, userId)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                logger.error("Error handling message", e)
                outgoing.send(WebSocketMessage.Error("처리 중 오류가 발생했습니다: ${e.message}"))
            }
        }
    }
    
    private suspend fun handleAudioInput(
        message: WebSocketMessage.AudioInput,
        outgoing: Channel<WebSocketMessage>,
        userId: String
    ) {
        // ElevenLabs는 STT를 지원하지 않으므로 에러 메시지 반환
        outgoing.send(WebSocketMessage.Error("음성 입력은 현재 지원되지 않습니다. 텍스트로 입력해주세요."))
        return
    }
    
    private suspend fun handleTextInput(
        message: WebSocketMessage.TextInput,
        outgoing: Channel<WebSocketMessage>,
        userId: String
    ) {
        // 1. Conversation processing
        val conversationResult = conversationAgent.process(
            ConversationInput(
                userId = userId,
                message = message.text,
                conversationId = message.conversationId
            )
        )
        
        // 2. Store context for story generation
        val context = conversationContexts.getOrPut(message.conversationId) {
            ConversationContext(userId, message.conversationId)
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
        val audioData = speechService.textToSpeech(
            text = conversationResult.response,
            emotion = conversationResult.emotion ?: "NEUTRAL",
            voice = "korean-female-1"
        )
        
        outgoing.send(WebSocketMessage.AudioOutput(
            audioData = Base64.getEncoder().encodeToString(audioData),
            emotion = conversationResult.emotion
        ))
    }
    
    private suspend fun handleGenerateStory(
        message: WebSocketMessage.GenerateStory,
        outgoing: Channel<WebSocketMessage>,
        userId: String
    ) {
        val context = conversationContexts[message.conversationId]
        if (context == null) {
            outgoing.send(WebSocketMessage.Error("대화 내용을 찾을 수 없습니다"))
            return
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
        
        // 3. Story generation
        val storyResult = storyGenerationAgent.process(
            StoryGenerationInput(
                conversationContext = conversationText,
                emotionAnalysis = emotionResult,
                userId = userId,
                conversationHighlights = extractHighlights(context)
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
        for (message in outgoing) {
            val jsonMessage = json.encodeToString(message)
            session.send(Frame.Text(jsonMessage))
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
