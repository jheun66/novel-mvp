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

    @Serializable
    @SerialName("TranscriptionResult")
    data class TranscriptionResult(
        val conversationId: String,
        val text: String,
        val confidence: Float? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketMessage()
    
    // Real-time streaming message types
    @Serializable
    @SerialName("AudioStreamStart")
    data class AudioStreamStart(
        val conversationId: String = UUID.randomUUID().toString(),
        val sampleRate: Int = 16000,
        val format: String = "pcm16",
        val channels: Int = 1
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("AudioStreamChunk")
    data class AudioStreamChunk(
        val conversationId: String,
        val audioData: String,  // Base64 encoded raw PCM chunk
        val sequenceNumber: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("AudioStreamEnd")
    data class AudioStreamEnd(
        val conversationId: String,
        val totalChunks: Int
    ) : WebSocketMessage()
    
    @Serializable
    @SerialName("StreamingTranscriptionResult")
    data class StreamingTranscriptionResult(
        val conversationId: String,
        val text: String,
        val isPartial: Boolean,
        val confidence: Float? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketMessage()
}

class NovelWebSocketService(
    private val conversationAgent: ConversationAgent,
    private val emotionAnalysisAgent: EmotionAnalysisAgent,
    private val storyGenerationAgent: StoryGenerationAgent,
    private val elevenLabsTTSService: ElevenLabsTTSService,
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
    private val audioStreamBuffers = mutableMapOf<String, AudioStreamBuffer>()
    
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
            // Clean up any remaining conversation contexts and audio buffers for this user
            authenticatedUser?.let { user ->
                conversationContexts.entries.removeIf { it.value.userId == user.userId }
                audioStreamBuffers.entries.removeIf { it.value.userId == user.userId }
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
                        is WebSocketMessage.AudioStreamStart -> {
                            handleAudioStreamStart(message, outgoing, user)
                        }
                        is WebSocketMessage.AudioStreamChunk -> {
                            handleAudioStreamChunk(message, outgoing, user)
                        }
                        is WebSocketMessage.AudioStreamEnd -> {
                            handleAudioStreamEnd(message, outgoing, user)
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
                Base64.getDecoder().decode(message.audioData)
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
            } else {
                outgoing.send(WebSocketMessage.TranscriptionResult(
                    conversationId = message.conversationId,
                    text = transcribedText,
                    confidence = 0.0f
                ))
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
    
    private suspend fun handleAudioStreamStart(
        message: WebSocketMessage.AudioStreamStart,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        try {
            logger.info("Starting audio stream for conversation: ${message.conversationId}, user: ${user.userId}")
            
            // Create or reset audio stream buffer for this conversation
            audioStreamBuffers[message.conversationId] = AudioStreamBuffer(
                conversationId = message.conversationId,
                userId = user.userId,
                sampleRate = message.sampleRate,
                format = message.format,
                channels = message.channels
            )
            
            logger.info("Audio stream buffer created for conversation: ${message.conversationId}")
            
        } catch (e: Exception) {
            logger.error("Error starting audio stream", e)
            outgoing.send(WebSocketMessage.Error("오디오 스트림 시작 중 오류가 발생했습니다: ${e.message}"))
        }
    }
    
    private suspend fun handleAudioStreamChunk(
        message: WebSocketMessage.AudioStreamChunk,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        try {
            val buffer = audioStreamBuffers[message.conversationId]
            if (buffer == null) {
                logger.error("Audio stream buffer not found for conversation: ${message.conversationId}")
                outgoing.send(WebSocketMessage.Error("오디오 스트림이 시작되지 않았습니다."))
                return
            }
            
            // Decode chunk data
            val chunkData = try {
                Base64.getDecoder().decode(message.audioData)
            } catch (e: Exception) {
                logger.error("Failed to decode audio chunk", e)
                outgoing.send(WebSocketMessage.Error("오디오 청크 디코딩 실패: ${e.message}"))
                return
            }
            
            // Add chunk to buffer
            buffer.addChunk(message.sequenceNumber, chunkData)
            
            logger.debug("Added audio chunk ${message.sequenceNumber} to buffer (${chunkData.size} bytes)")
            
            // Check if we have enough data for partial transcription (e.g., every 2 seconds worth of data)
            if (buffer.shouldProcessPartialTranscription()) {
                val audioData = buffer.getAccumulatedAudio()
                if (audioData.isNotEmpty()) {
                    // Process partial transcription
                    try {
                        val transcriptionResult = whisperSTTService.transcribeAudio(
                            audioBytes = audioData,
                            language = "ko",
                            enableTimestamps = false
                        )
                        
                        if (transcriptionResult.text.isNotBlank()) {
                            outgoing.send(WebSocketMessage.StreamingTranscriptionResult(
                                conversationId = message.conversationId,
                                text = transcriptionResult.text,
                                isPartial = true,
                                confidence = 0.8f // Placeholder confidence
                            ))
                            
                            logger.debug("Sent partial transcription: '${transcriptionResult.text}'")
                        }
                    } catch (e: Exception) {
                        logger.error("Partial transcription failed", e)
                        // Continue processing without failing the stream
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error processing audio chunk", e)
            outgoing.send(WebSocketMessage.Error("오디오 청크 처리 중 오류가 발생했습니다: ${e.message}"))
        }
    }
    
    private suspend fun handleAudioStreamEnd(
        message: WebSocketMessage.AudioStreamEnd,
        outgoing: Channel<WebSocketMessage>,
        user: AuthenticatedUser
    ) {
        try {
            val buffer = audioStreamBuffers.remove(message.conversationId)
            if (buffer == null) {
                logger.error("Audio stream buffer not found for conversation: ${message.conversationId}")
                outgoing.send(WebSocketMessage.Error("오디오 스트림 버퍼를 찾을 수 없습니다."))
                return
            }
            
            logger.info("Ending audio stream for conversation: ${message.conversationId}, total chunks: ${message.totalChunks}")
            
            // Get final accumulated audio
            val finalAudioData = buffer.getFinalAudio()
            
            if (finalAudioData.isEmpty()) {
                outgoing.send(WebSocketMessage.Error("최종 오디오 데이터가 비어있습니다."))
                return
            }
            
            // Final transcription
            val transcriptionResult = whisperSTTService.transcribeAudio(
                audioBytes = finalAudioData,
                language = "ko",
                enableTimestamps = false
            )
            
            val transcribedText = transcriptionResult.text
            logger.info("Final transcription result: '$transcribedText' (${transcribedText.length} chars)")
            
            if (transcribedText.isBlank()) {
                outgoing.send(WebSocketMessage.StreamingTranscriptionResult(
                    conversationId = message.conversationId,
                    text = "",
                    isPartial = false,
                    confidence = 0.0f
                ))
                outgoing.send(WebSocketMessage.Error("음성을 인식할 수 없습니다. 더 명확하게 말씀해주세요."))
                return
            }
            
            // Send final transcription result
            outgoing.send(WebSocketMessage.StreamingTranscriptionResult(
                conversationId = message.conversationId,
                text = transcribedText,
                isPartial = false,
                confidence = 0.9f // Higher confidence for final result
            ))
            
            // Process the transcribed text as a regular text input
            val textInput = WebSocketMessage.TextInput(
                text = transcribedText,
                conversationId = message.conversationId
            )
            
            handleTextInput(textInput, outgoing, user)
            
        } catch (e: Exception) {
            logger.error("Error ending audio stream", e)
            outgoing.send(WebSocketMessage.Error("오디오 스트림 종료 중 오류가 발생했습니다: ${e.message}"))
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
        
        // 4. Generate TTS with emotion using ElevenLabs TTS
        try {
            logger.info("=== WebSocket Chat TTS Generation ===")
            logger.info("User ID: ${user.userId}")
            logger.info("Full AI response text: '${conversationResult.response}' (${conversationResult.response.length} chars)")
            logger.info("Detected emotion: ${conversationResult.emotion}")
            logger.info("Should generate story: ${conversationResult.shouldGenerateStory}")
            
            val ttsResult = elevenLabsTTSService.generateChatTTS(
                text = conversationResult.response,
                emotion = conversationResult.emotion ?: "NEUTRAL",
                speed = 1.1f
            )
            
            if (ttsResult.isSuccess) {
                val audioData = ttsResult.getOrNull()
                if (audioData != null) {
                    logger.info("Chat TTS generation successful:")
                    logger.info("- Raw audio bytes: ${audioData.size}")
                    logger.info("- Estimated size in KB: ${String.format("%.2f", audioData.size / 1024.0)}")
                    
                    val base64Audio = Base64.getEncoder().encodeToString(audioData)
                    logger.info("Base64 encoding:")
                    logger.info("- Base64 string length: ${base64Audio.length} chars")
                    logger.info("- Encoding efficiency: ${String.format("%.2f", (base64Audio.length.toDouble() / audioData.size) * 100)}%")
                    
                    // Log first and last few chars for debugging
                    if (base64Audio.length > 20) {
                        logger.info("- Base64 preview: ${base64Audio.take(10)}...${base64Audio.takeLast(10)}")
                    }
                    
                    val audioOutput = WebSocketMessage.AudioOutput(
                        audioData = base64Audio,
                        emotion = conversationResult.emotion,
                        audioType = "chat"
                    )
                    
                    outgoing.send(audioOutput)
                    
                    logger.info("Chat audio successfully sent to WebSocket client")
                    logger.info("Final AudioOutput message size: ~${base64Audio.length + 100} chars (approx)")
                } else {
                    logger.warn("Chat TTS result was successful but audio data is null")
                }
            } else {
                logger.error("Failed to generate chat TTS: ${ttsResult.exceptionOrNull()?.message}")
                ttsResult.exceptionOrNull()?.printStackTrace()
            }
        } catch (e: Exception) {
            logger.error("Failed to generate TTS", e)
            e.printStackTrace()
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
            logger.debug("Generating story TTS for: '${storyResult.story.take(50)}...' with emotion: ${emotionResult.primaryEmotion}")
            
            val storyTTSResult = elevenLabsTTSService.generateStoryTTS(
                text = storyResult.story,
                emotion = emotionResult.primaryEmotion,
                speed = 0.9f,
                addPauses = true
            )
            
            if (storyTTSResult.isSuccess) {
                val audioData = storyTTSResult.getOrNull()
                if (audioData != null) {
                    logger.debug("Story TTS generated successfully - Audio size: ${audioData.size} bytes")
                    
                    storyAudioData = Base64.getEncoder().encodeToString(audioData)
                    logger.debug("Story audio encoded to Base64 - Length: ${storyAudioData?.length} chars")
                } else {
                    logger.warn("Story TTS result was successful but audio data is null")
                }
            } else {
                logger.error("Failed to generate story TTS: ${storyTTSResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            logger.error("Failed to generate story TTS", e)
        }

        // 5. Send story with optional audio
        val storyOutput = WebSocketMessage.StoryOutput(
            title = storyResult.title,
            content = storyResult.story,
            emotion = emotionResult.primaryEmotion,
            genre = storyResult.genre,
            emotionalArc = storyResult.emotionalArc,
            audioData = storyAudioData
        )
        
        logger.debug("Sending story to WebSocket client - Title: '${storyResult.title}', Content length: ${storyResult.story.length}, Audio: ${if (storyAudioData != null) "included (${storyAudioData.length} chars)" else "none"}")
        
        outgoing.send(storyOutput)
        
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

// Audio stream buffer for real-time processing
data class AudioStreamBuffer(
    val conversationId: String,
    val userId: String,
    val sampleRate: Int,
    val format: String,
    val channels: Int,
    private val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
    private var lastProcessedChunk: Int = -1,
    private var totalBytes: Int = 0,
    private var startTime: Long = System.currentTimeMillis()
) {
    companion object {
        // Process partial transcription every 2 seconds worth of audio
        private const val PARTIAL_TRANSCRIPTION_INTERVAL_MS = 2000
        // Minimum audio size for processing (1 second of 16kHz 16-bit mono audio)
        private const val MIN_AUDIO_SIZE_FOR_PROCESSING = 32000
    }

    fun addChunk(sequenceNumber: Int, data: ByteArray) {
        chunks[sequenceNumber] = data
        totalBytes += data.size
    }

    fun shouldProcessPartialTranscription(): Boolean {
        val elapsedTime = System.currentTimeMillis() - startTime
        if (elapsedTime >= PARTIAL_TRANSCRIPTION_INTERVAL_MS) {
            startTime = elapsedTime
        } else return false
        return totalBytes >= MIN_AUDIO_SIZE_FOR_PROCESSING
                && chunks.keys.maxOrNull()?.let { it > lastProcessedChunk } == true
    }
    
    fun getAccumulatedAudio(): ByteArray {
        val sortedChunks = chunks.toSortedMap()
        val result = mutableListOf<Byte>()

        for ((sequenceNumber, data) in sortedChunks) {
            if (sequenceNumber > lastProcessedChunk) {
                result.addAll(data.toList())
            }
        }
        // Update last processed chunk
        lastProcessedChunk = chunks.keys.maxOrNull() ?: lastProcessedChunk
        
        return if (result.size >= MIN_AUDIO_SIZE_FOR_PROCESSING) {
            // Convert to WAV format for Whisper
            convertPCMToWav(result.toByteArray(), sampleRate, channels)
        } else {
            byteArrayOf()
        }
    }
    
    fun getFinalAudio(): ByteArray {
        val sortedChunks = chunks.toSortedMap()
        val result = mutableListOf<Byte>()
        
        for ((_, data) in sortedChunks) {
            result.addAll(data.toList())
        }
        
        return if (result.isNotEmpty()) {
            // Convert to WAV format for Whisper
            convertPCMToWav(result.toByteArray(), sampleRate, channels)
        } else {
            byteArrayOf()
        }
    }
    
    private fun convertPCMToWav(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * 16 / 8
        
        val header = ByteArray(44)
        
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        
        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // fmt subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // subchunk1size
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // audio format (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        
        // data subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()
        
        return header + pcmData
    }
}
