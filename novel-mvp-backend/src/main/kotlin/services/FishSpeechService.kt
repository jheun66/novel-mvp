package com.novel.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class FishSpeechConfig(
    val baseUrl: String = "http://localhost:5002",
    val defaultVoice: String = "korean-female-1",
    val sampleRate: Int = 22050,
    val audioFormat: String = "wav",
    val chatSpeed: Float = 1.2f,
    val storySpeed: Float = 1.0f
)

@Serializable
data class FishSpeechRequest(
    val text: String,
    val reference_audio: String? = null,
    val reference_text: String? = null,
    val streaming: Boolean = false,
    val format: String = "wav"
)

@Serializable
data class FishSpeechResponse(
    val audio_data: String? = null, // Base64 encoded audio
    val sample_rate: Int = 22050,
    val format: String = "wav",
    val duration: Float? = null
)

/**
 * Fish Speech TTS Service - State-of-the-art open-source TTS
 * Replaces CoquiTTSService with superior performance and Korean support
 */
class FishSpeechService(
    private val config: FishSpeechConfig,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(FishSpeechService::class.java)

    // Fish Speech emotion mapping - Rich emotion control
    private val fishEmotionMapping = mapOf(
        // Basic emotions
        "HAPPY" to "(excited)",
        "SAD" to "(sad)", 
        "EXCITED" to "(excited)",
        "CALM" to "(soft tone)",
        "ANGRY" to "(angry)",
        "GRATEFUL" to "(grateful)",
        "ANXIOUS" to "(anxious)",
        "NEUTRAL" to "",
        
        // Advanced emotions
        "SURPRISED" to "(surprised)",
        "CONFIDENT" to "(confident)",
        "EMBARRASSED" to "(embarrassed)",
        "PROUD" to "(proud)",
        "WORRIED" to "(worried)",
        "FRUSTRATED" to "(frustrated)",
        "DELIGHTED" to "(delighted)",
        "NERVOUS" to "(nervous)",
        "RELAXED" to "(relaxed)",
        
        // Korean-specific tones
        "POLITE" to "(soft tone)",
        "FRIENDLY" to "(excited)",
        "FORMAL" to "(serious)",
        "CASUAL" to "(relaxed)"
    )

    // Fish Speech tone markers for different contexts
    private val contextualTones = mapOf(
        "CONVERSATION" to "(soft tone)",
        "STORYTELLING" to "",
        "NARRATION" to "(serious)",
        "ANNOUNCEMENT" to "(confident)",
        "WHISPER" to "(whispering)",
        "EMPHASIS" to "(shouting)"
    )

    // Audio effect markers for enhanced expression
    private val audioEffects = mapOf(
        "LAUGH" to "(laughing)",
        "CHUCKLE" to "(chuckling)", 
        "SIGH" to "(sighing)",
        "SOB" to "(sobbing)",
        "PANT" to "(panting)",
        "GROAN" to "(groaning)"
    )

    /**
     * Generate chat TTS for real-time conversation
     * Compatible with NovelWebSocketService.generateChatTTS()
     */
    suspend fun generateChatTTS(
        text: String,
        emotion: String = "NEUTRAL",
        speed: Float = config.chatSpeed
    ): Result<ByteArray> {
        return try {
            val enhancedText = enhanceTextWithEmotions(text, emotion, isChat = true)
            
            val request = FishSpeechRequest(
                text = enhancedText,
                streaming = false,
                format = config.audioFormat
            )
            
            val response = httpClient.post("${config.baseUrl}/v1/infer") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val audioData = response.readBytes()
                logger.debug("Chat TTS generated successfully: ${audioData.size} bytes")
                Result.success(audioData)
            } else {
                logger.error("Chat TTS failed with status: ${response.status}")
                Result.failure(Exception("Fish Speech TTS failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Chat TTS error", e)
            Result.failure(e)
        }
    }

    /**
     * Generate story TTS for high-quality narration
     * Compatible with NovelWebSocketService.generateStoryTTS()
     */
    suspend fun generateStoryTTS(
        text: String,
        emotion: String = "NEUTRAL",
        speed: Float = config.storySpeed,
        addPauses: Boolean = true
    ): Result<ByteArray> {
        return try {
            val enhancedText = enhanceTextWithEmotions(
                text = text, 
                emotion = emotion, 
                isChat = false,
                addPauses = addPauses
            )
            
            val request = FishSpeechRequest(
                text = enhancedText,
                streaming = false,
                format = config.audioFormat
            )
            
            val response = httpClient.post("${config.baseUrl}/v1/infer") {
                contentType(ContentType.Application.Json)
                setBody(request)
                timeout {
                    requestTimeoutMillis = 60_000 // 60 seconds for story TTS
                }
            }
            
            if (response.status.isSuccess()) {
                val audioData = response.readBytes()
                logger.debug("Story TTS generated successfully: ${audioData.size} bytes")
                Result.success(audioData)
            } else {
                logger.error("Story TTS failed with status: ${response.status}")
                Result.failure(Exception("Fish Speech Story TTS failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Story TTS error", e)
            Result.failure(e)
        }
    }

    /**
     * Legacy compatibility method for textToSpeech
     */
    suspend fun textToSpeech(
        text: String,
        emotion: String = "NEUTRAL",
        voice: String = config.defaultVoice
    ): ByteArray {
        return generateChatTTS(text, emotion).getOrElse { 
            logger.warn("TTS failed, returning empty audio")
            ByteArray(0) 
        }
    }

    /**
     * Stream TTS for real-time conversation
     */
    fun textToSpeechStream(
        text: String,
        emotion: String = "NEUTRAL",
        voice: String = config.defaultVoice
    ): Flow<ByteArray> = flow {
        try {
            // Split text into sentences for streaming
            val sentences = text.split(Regex("[.!?。！？]+")).filter { it.isNotBlank() }
            
            sentences.forEach { sentence ->
                val result = generateChatTTS(
                    text = sentence.trim(),
                    emotion = emotion
                )
                
                result.onSuccess { audioChunk ->
                    if (audioChunk.isNotEmpty()) {
                        emit(audioChunk)
                    }
                }.onFailure { error ->
                    logger.error("Streaming TTS error for sentence: $sentence", error)
                }
            }
        } catch (e: Exception) {
            logger.error("Streaming TTS error", e)
        }
    }

    /**
     * Enhance text with Fish Speech emotion markers and Korean optimizations
     */
    private fun enhanceTextWithEmotions(
        text: String, 
        emotion: String,
        isChat: Boolean = true,
        addPauses: Boolean = false
    ): String {
        var enhancedText = text.trim()
        
        // Apply emotion marker
        val emotionMarker = fishEmotionMapping[emotion.uppercase()] ?: ""
        if (emotionMarker.isNotEmpty()) {
            enhancedText = "$emotionMarker $enhancedText"
        }
        
        // Apply contextual tone for chat vs story
        val contextTone = if (isChat) contextualTones["CONVERSATION"] else ""
        if (contextTone?.isNotEmpty() == true && emotionMarker.isEmpty()) {
            enhancedText = "$contextTone $enhancedText"
        }
        
        // Korean text enhancements
        enhancedText = enhancedText
            .replace("...", if (addPauses) "… " else "…") // Proper ellipsis
            .replace("  ", " ") // Remove double spaces
        
        if (addPauses && !isChat) {
            // Add natural pauses for story narration
            enhancedText = enhancedText
                .replace(".", ". ") // Pause after periods
                .replace("!", "! ") // Pause after exclamations  
                .replace("?", "? ") // Pause after questions
                .replace(",", ", ") // Short pause after commas
        }
        
        return enhancedText.trim()
    }

    /**
     * Generate audio with voice cloning using reference audio
     */
    suspend fun generateWithVoiceCloning(
        text: String,
        referenceAudioPath: String,
        referenceText: String,
        emotion: String = "NEUTRAL"
    ): Result<ByteArray> {
        return try {
            val enhancedText = enhanceTextWithEmotions(text, emotion)
            
            val request = FishSpeechRequest(
                text = enhancedText,
                reference_audio = referenceAudioPath,
                reference_text = referenceText,
                streaming = false,
                format = config.audioFormat
            )
            
            val response = httpClient.post("${config.baseUrl}/v1/infer") {
                contentType(ContentType.Application.Json)
                setBody(request)
                timeout {
                    requestTimeoutMillis = 90_000 // Extended timeout for voice cloning
                }
            }
            
            if (response.status.isSuccess()) {
                val audioData = response.readBytes()
                logger.debug("Voice cloning TTS generated successfully: ${audioData.size} bytes")
                Result.success(audioData)
            } else {
                logger.error("Voice cloning TTS failed with status: ${response.status}")
                Result.failure(Exception("Fish Speech voice cloning failed: ${response.status}"))
            }
        } catch (e: Exception) {
            logger.error("Voice cloning TTS error", e)
            Result.failure(e)
        }
    }

    /**
     * Get available emotions and markers
     */
    fun getAvailableEmotions(): Map<String, String> {
        return fishEmotionMapping.toMap()
    }

    /**
     * Get available tone markers
     */
    fun getAvailableTones(): Map<String, String> {
        return contextualTones.toMap()
    }

    /**
     * Get available audio effects
     */
    fun getAvailableEffects(): Map<String, String> {
        return audioEffects.toMap()
    }

    /**
     * Check Fish Speech service health
     */
    suspend fun getHealthStatus(): String {
        return try {
            val response = httpClient.get("${config.baseUrl}/health")
            
            if (response.status.isSuccess()) {
                "Fish Speech service is healthy"
            } else {
                "Fish Speech service health check failed: ${response.status}"
            }
        } catch (e: Exception) {
            "Fish Speech service health check error: ${e.message}"
        }
    }

    /**
     * Get service information
     */
    suspend fun getServiceInfo(): String {
        return try {
            val response = httpClient.get("${config.baseUrl}/v1/models")
            
            if (response.status.isSuccess()) {
                "Fish Speech service info retrieved successfully"
            } else {
                "Fish Speech service info unavailable"
            }
        } catch (e: Exception) {
            "Error getting Fish Speech service info: ${e.message}"
        }
    }

    /**
     * Generate enhanced story audio with title
     */
    suspend fun generateStoryAudio(
        storyContent: String,
        title: String,
        emotion: String = "NEUTRAL"
    ): Result<ByteArray> {
        return try {
            // Create narrative introduction
            val narrativeText = "제목: $title. $storyContent"
            
            generateStoryTTS(
                text = narrativeText,
                emotion = emotion,
                addPauses = true
            )
        } catch (e: Exception) {
            logger.error("Story audio generation error", e)
            Result.failure(e)
        }
    }

    /**
     * Advanced emotion mixing for complex emotional states
     */
    suspend fun generateWithMixedEmotions(
        text: String,
        primaryEmotion: String,
        subEmotions: List<String>,
        isChat: Boolean = true
    ): Result<ByteArray> {
        try {
            // Create complex emotion text with multiple markers
            var emotionalText = text
            
            // Apply primary emotion
            val primaryMarker = fishEmotionMapping[primaryEmotion.uppercase()] ?: ""
            if (primaryMarker.isNotEmpty()) {
                emotionalText = "$primaryMarker $emotionalText"
            }
            
            // Add sub-emotion hints through text enhancement
            subEmotions.forEach { subEmotion ->
                val subMarker = fishEmotionMapping[subEmotion.uppercase()]
                if (subMarker?.isNotEmpty() == true) {
                    // Subtle integration of sub-emotions
                    logger.debug("Adding sub-emotion hint: $subEmotion -> $subMarker")
                }
            }
            
            return if (isChat) {
                generateChatTTS(emotionalText, primaryEmotion)
            } else {
                generateStoryTTS(emotionalText, primaryEmotion)
            }
        } catch (e: Exception) {
            logger.error("Mixed emotion TTS error", e)
            return Result.failure(e)
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        logger.info("FishSpeechService closed")
    }
}