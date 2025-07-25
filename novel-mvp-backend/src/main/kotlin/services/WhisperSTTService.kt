package com.novel.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

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
            logger.info("Starting transcription - Audio size: ${audioBytes.size} bytes, Language: $language")
            
            if (audioBytes.isEmpty()) {
                logger.error("Audio bytes is empty")
                throw IllegalArgumentException("Audio data is empty")
            }
            
            if (audioBytes.size > MAX_AUDIO_SIZE) {
                logger.error("Audio size ${audioBytes.size} exceeds maximum ${MAX_AUDIO_SIZE}")
                throw IllegalArgumentException("Audio size exceeds maximum allowed size")
            }
            
            // Log first few bytes to debug audio format
            val audioHeader = audioBytes.take(12).joinToString(" ") { "%02x".format(it) }
            logger.debug("Audio header bytes: $audioHeader")

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

            logger.info("Whisper API response status: ${response.status}, Content-Type: ${response.headers[HttpHeaders.ContentType]}")

            if (response.status.isSuccess()) {
                val responseText = response.body<String>()
                logger.debug("Raw Whisper response: $responseText")
                
                val result = response.body<TranscriptionResponse>()
                logger.info("Transcription successful: '${result.text}' (${result.text.length} chars)")
                
                if (result.text.isBlank()) {
                    logger.warn("Whisper returned empty transcription - audio may be silent or unrecognizable")
                }
                
                result
            } else {
                val errorBody = try { response.body<String>() } catch (e: Exception) { "Unable to read error body" }
                logger.error("Transcription failed with status: ${response.status}, body: $errorBody")
                throw RuntimeException("Whisper API returned ${response.status}: $errorBody")
            }
        } catch (e: Exception) {
            logger.error("Transcription error: ${e.message}", e)
            throw e // Re-throw to let caller handle the error properly
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
     * Clean up resources
     */
    fun close() {
        // Any cleanup if needed
        logger.info("WhisperSTTService closed")
    }
}