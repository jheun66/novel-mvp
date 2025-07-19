package com.novel.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

@Serializable
data class WhisperConfig(
    val baseUrl: String = "http://localhost:5001",
    val model: String = "base",
    val language: String = "ko",
    val responseFormat: String = "json",
    val temperature: Float = 0.0f,
    val enableTimestamps: Boolean = false
)

@Serializable
data class TranscriptionRequest(
    val model: String,
    val language: String? = null,
    val response_format: String = "json",
    val temperature: Float = 0.0f,
    val timestamp_granularities: List<String>? = null
)

@Serializable
data class TranscriptionResponse(
    val text: String,
    val language: String? = null,
    val duration: Float? = null,
    val segments: List<TranscriptionSegment>? = null
)

@Serializable
data class TranscriptionSegment(
    val id: Int,
    val seek: Int,
    val start: Float,
    val end: Float,
    val text: String,
    val temperature: Float,
    val avg_logprob: Float,
    val compression_ratio: Float,
    val no_speech_prob: Float
)

@Serializable
data class LanguageDetectionResponse(
    val detected_language: String,
    val confidence: Float
)

@Serializable
data class StreamingTranscriptionChunk(
    val text: String,
    val isPartial: Boolean,
    val timestamp: Float? = null
)

/**
 * Whisper STT Service for real-time speech recognition
 * Provides Korean language support with streaming capabilities
 */
class WhisperSTTService(
    private val config: WhisperConfig,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(WhisperSTTService::class.java)

    companion object {
        private const val MAX_AUDIO_SIZE = 25 * 1024 * 1024 // 25MB limit for Whisper
        private const val STREAMING_CHUNK_DURATION = 2.0f // 2 seconds chunks for streaming
    }

    /**
     * Transcribe audio bytes to text
     */
    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        language: String = config.language,
        enableTimestamps: Boolean = config.enableTimestamps
    ): TranscriptionResponse {
        return try {
            if (audioBytes.size > MAX_AUDIO_SIZE) {
                logger.warn("Audio size ${audioBytes.size} exceeds maximum ${MAX_AUDIO_SIZE}")
                return TranscriptionResponse(text = "", language = language)
            }

            val response = httpClient.submitFormWithBinaryData(
                url = "${config.baseUrl}/v1/audio/transcriptions",
                formData = formData {
                    append("file", audioBytes, Headers.build {
                        append(HttpHeaders.ContentType, "audio/wav")
                        append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                    })
                    append("model", config.model)
                    append("language", language)
                    append("response_format", config.responseFormat)
                    append("temperature", config.temperature.toString())
                    if (enableTimestamps) {
                        append("timestamp_granularities[]", "segment")
                    }
                }
            )

            if (response.status.isSuccess()) {
                val result = response.body<TranscriptionResponse>()
                logger.info("Transcription successful: '${result.text.take(50)}...'")
                result
            } else {
                logger.error("Transcription failed with status: ${response.status}")
                TranscriptionResponse(text = "", language = language)
            }
        } catch (e: Exception) {
            logger.error("Transcription error", e)
            TranscriptionResponse(text = "", language = language)
        }
    }

    /**
     * Stream audio chunks for real-time transcription
     */
    fun transcribeStream(audioStream: Flow<ByteArray>): Flow<StreamingTranscriptionChunk> = flow {
        var audioBuffer = ByteArray(0)
        var chunkCount = 0

        audioStream.collect { chunk ->
            audioBuffer += chunk
            chunkCount++

            // Process accumulated audio every few chunks for streaming effect
            if (chunkCount >= 3 || audioBuffer.size > 64 * 1024) { // ~64KB chunks
                try {
                    val result = transcribeAudio(audioBuffer, enableTimestamps = true)
                    if (result.text.isNotBlank()) {
                        emit(StreamingTranscriptionChunk(
                            text = result.text,
                            isPartial = true,
                            timestamp = System.currentTimeMillis() / 1000.0f
                        ))
                    }
                    
                    // Keep last portion for context in next chunk
                    if (audioBuffer.size > 32 * 1024) {
                        audioBuffer = audioBuffer.takeLast(16 * 1024).toByteArray()
                    }
                    chunkCount = 0
                } catch (e: Exception) {
                    logger.error("Streaming transcription error", e)
                }
            }
        }

        // Process final chunk
        if (audioBuffer.isNotEmpty()) {
            try {
                val result = transcribeAudio(audioBuffer, enableTimestamps = true)
                if (result.text.isNotBlank()) {
                    emit(StreamingTranscriptionChunk(
                        text = result.text,
                        isPartial = false,
                        timestamp = System.currentTimeMillis() / 1000.0f
                    ))
                }
            } catch (e: Exception) {
                logger.error("Final chunk transcription error", e)
            }
        }
    }

    /**
     * Detect language from audio
     */
    suspend fun detectLanguage(audioBytes: ByteArray): LanguageDetectionResponse {
        return try {
            val response = httpClient.submitFormWithBinaryData(
                url = "${config.baseUrl}/v1/audio/detect-language",
                formData = formData {
                    append("file", audioBytes, Headers.build {
                        append(HttpHeaders.ContentType, "audio/wav")
                        append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                    })
                    append("model", config.model)
                }
            )

            if (response.status.isSuccess()) {
                response.body<LanguageDetectionResponse>()
            } else {
                logger.error("Language detection failed with status: ${response.status}")
                LanguageDetectionResponse(detected_language = "ko", confidence = 0.5f)
            }
        } catch (e: Exception) {
            logger.error("Language detection error", e)
            LanguageDetectionResponse(detected_language = "ko", confidence = 0.5f)
        }
    }

    /**
     * Quick transcription for short audio clips (conversation)
     */
    suspend fun quickTranscribe(audioBytes: ByteArray): String {
        val result = transcribeAudio(
            audioBytes = audioBytes,
            language = config.language,
            enableTimestamps = false
        )
        return result.text.trim()
    }

    /**
     * Enhanced transcription with speaker emotion detection
     */
    suspend fun transcribeWithEmotion(audioBytes: ByteArray): Pair<String, String> {
        // First get the transcription
        val transcription = quickTranscribe(audioBytes)
        
        // TODO: Implement emotion detection from audio
        // For now, return neutral emotion
        val detectedEmotion = "NEUTRAL"
        
        return Pair(transcription, detectedEmotion)
    }

    /**
     * Validate audio format and quality
     */
    fun validateAudioFormat(audioBytes: ByteArray): Boolean {
        return try {
            // Basic validation - check if it's not empty and has reasonable size
            audioBytes.isNotEmpty() && 
            audioBytes.size > 1000 && // At least 1KB
            audioBytes.size < MAX_AUDIO_SIZE
        } catch (e: Exception) {
            logger.error("Audio validation error", e)
            false
        }
    }

    /**
     * Get service health status
     */
    suspend fun getHealthStatus(): Boolean {
        return try {
            val response = httpClient.get("${config.baseUrl}/health")
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            false
        }
    }

    /**
     * Get available models from Whisper service
     */
    suspend fun getAvailableModels(): List<String> {
        return try {
            val response = httpClient.get("${config.baseUrl}/v1/models")
            if (response.status.isSuccess()) {
                response.body<Map<String, List<Map<String, String>>>>()["data"]
                    ?.mapNotNull { it["id"] } ?: listOf("base")
            } else {
                listOf("base", "small", "medium", "large")
            }
        } catch (e: Exception) {
            logger.error("Failed to get available models", e)
            listOf("base", "small", "medium", "large")
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        // Any cleanup if needed
        logger.info("WhisperSTTService closed")
    }
}