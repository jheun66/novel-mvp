package com.novel.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.net.ConnectException
import java.io.IOException
import java.io.EOFException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.network.sockets.SocketTimeoutException

@Serializable
data class ElevenLabsConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.elevenlabs.io",
    val model: String = "eleven_multilingual_v2", // 다국어 모델
    val outputFormat: String = "mp3_44100_128", // mp3_44100_128, pcm_16000, pcm_22050, pcm_24000, pcm_44100
    val defaultVoice: String = "JBFqnCBsd6RMkjVDRZzb", // George - 기본 음성
    val maxRetries: Int = 2,
    val timeoutMs: Long = 30000, // 30 seconds
    val streamingLatencyOptimization: Int = 2 // 0-4 (4가 가장 빠름, 0이 가장 높은 품질)
)

@Serializable
data class ElevenLabsTTSRequest(
    val text: String,
    val model_id: String = "eleven_multilingual_v2",
    val voice_settings: ElevenLabsVoiceSettings? = null,
    val optimize_streaming_latency: Int? = null,
    val output_format: String? = null,
    val language_code: String? = null
)

@Serializable
data class ElevenLabsVoiceSettings(
    val stability: Float = 0.75f, // 0.0 - 1.0
    val similarity_boost: Float = 0.75f, // 0.0 - 1.0
    val style: Float = 0.0f, // 0.0 - 1.0 (감정 강도)
    val use_speaker_boost: Boolean = true
)

/**
 * ElevenLabs TTS Service - 고품질 다국어 음성 합성
 * OpenAI TTS를 대체하여 더 자연스러운 음성과 감정 표현 제공
 * 
 * 주요 특징:
 * - 실시간 스트리밍 TTS (대화용)
 * - 고품질 TTS (스토리 내레이션용)
 * - 감정별 최적화된 음성 매핑
 * - 다국어 지원 (한국어 포함)
 * - 유연한 음성 설정 (stability, similarity_boost, style)
 */
class ElevenLabsTTSService(
    private val config: ElevenLabsConfig,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(ElevenLabsTTSService::class.java)

    // ElevenLabs 음성별 감정 매핑 (실제 음성 ID로 교체 필요)
    private val emotionVoiceMapping = mapOf(
        // 기본 감정 - 다양한 ElevenLabs 음성 사용
        "HAPPY" to "EXAVITQu4vr4xnSDxMaL", // Bella - 밝고 활기찬 여성 음성
        "SAD" to "TxGEqnHWrfWFTfGW9XjX", // Josh - 깊고 따뜻한 남성 음성  
        "EXCITED" to "pNInz6obpgDQGcFmaJgB", // Adam - 에너지 넘치는 남성 음성
        "CALM" to "21m00Tcm4TlvDq8ikWAM", // Rachel - 차분하고 안정적인 여성 음성
        "ANGRY" to "ErXwobaYiN019PkySvjV", // Antoni - 강렬한 남성 음성
        "GRATEFUL" to "VR6AewLTigWG4xSOukaG", // Arnold - 따뜻한 남성 음성
        "ANXIOUS" to "oWAxZDx7w5VEj9dCyTzz", // Grace - 부드러운 여성 음성
        "NEUTRAL" to config.defaultVoice, // George - 기본 중성적 남성 음성
        
        // 고급 감정
        "SURPRISED" to "pNInz6obpgDQGcFmaJgB", // Adam - 활발한 표현
        "CONFIDENT" to "ErXwobaYiN019PkySvjV", // Antoni - 자신감 있는 음성
        "EMBARRASSED" to "oWAxZDx7w5VEj9dCyTzz", // Grace - 부드러운 음성
        "PROUD" to "VR6AewLTigWG4xSOukaG", // Arnold - 자랑스러운 음성
        "WORRIED" to "TxGEqnHWrfWFTfGW9XjX", // Josh - 걱정스러운 음성
        "FRUSTRATED" to "ErXwobaYiN019PkySvjV", // Antoni - 답답한 음성
        "DELIGHTED" to "EXAVITQu4vr4xnSDxMaL", // Bella - 기쁜 음성
        "NERVOUS" to "oWAxZDx7w5VEj9dCyTzz", // Grace - 긴장된 음성
        "RELAXED" to "21m00Tcm4TlvDq8ikWAM", // Rachel - 편안한 음성
        
        // 맥락별 음성
        "POLITE" to "21m00Tcm4TlvDq8ikWAM", // Rachel - 정중한 음성
        "FRIENDLY" to "EXAVITQu4vr4xnSDxMaL", // Bella - 친근한 음성
        "FORMAL" to "JBFqnCBsd6RMkjVDRZzb", // George - 격식 있는 음성
        "CASUAL" to "pNInz6obpgDQGcFmaJgB" // Adam - 편한 음성
    )

    // 감정별 음성 설정 (감정 강도 조절)
    private val emotionVoiceSettingsMapping = mapOf(
        "HAPPY" to ElevenLabsVoiceSettings(stability = 0.8f, similarity_boost = 0.8f, style = 0.3f),
        "SAD" to ElevenLabsVoiceSettings(stability = 0.9f, similarity_boost = 0.7f, style = 0.2f),
        "EXCITED" to ElevenLabsVoiceSettings(stability = 0.6f, similarity_boost = 0.8f, style = 0.4f),
        "CALM" to ElevenLabsVoiceSettings(stability = 0.9f, similarity_boost = 0.75f, style = 0.1f),
        "ANGRY" to ElevenLabsVoiceSettings(stability = 0.7f, similarity_boost = 0.8f, style = 0.5f),
        "NEUTRAL" to ElevenLabsVoiceSettings(stability = 0.75f, similarity_boost = 0.75f, style = 0.0f)
    )

    /**
     * 간단한 재시도 로직
     */
    private suspend fun <T> executeWithRetry(
        operation: String,
        block: suspend () -> T
    ): T {
        repeat(config.maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt == config.maxRetries - 1) {
                    logger.error("$operation failed after ${config.maxRetries} attempts: ${e.message}", e)
                    throw e
                }
                logger.warn("$operation failed on attempt ${attempt + 1}, retrying: ${e.message}")
                delay(1000) // 1초 지연
            }
        }
        throw Exception("Unexpected retry loop exit")
    }

    /**
     * 실시간 대화용 스트리밍 TTS
     * ElevenLabs Stream API 사용으로 낮은 지연시간 제공
     */
    suspend fun generateChatTTS(
        text: String,
        emotion: String = "NEUTRAL",
        speed: Float = 1.0f // ElevenLabs는 stability/similarity로 제어
    ): Result<ByteArray> {
        return try {
            logger.info("=== Chat TTS Request ===")
            logger.info("Original text: '$text' (length: ${text.length} chars)")
            logger.info("Emotion: $emotion, Speed: $speed")
            
            val voiceId = emotionVoiceMapping[emotion.uppercase()] ?: config.defaultVoice
            val voiceSettings = emotionVoiceSettingsMapping[emotion.uppercase()] 
                ?: ElevenLabsVoiceSettings()
            
            val enhancedText = enhanceTextForTTS(text, emotion, isChat = true)
            logger.info("Enhanced text: '$enhancedText' (length: ${enhancedText.length} chars)")
            if (text != enhancedText) {
                logger.info("Text was modified during enhancement: '${text}' -> '${enhancedText}'")
            }
            
            val request = ElevenLabsTTSRequest(
                text = enhancedText,
                model_id = config.model,
                voice_settings = voiceSettings,
                optimize_streaming_latency = config.streamingLatencyOptimization,
                output_format = config.outputFormat
            )
            
            val audioData = executeWithRetry("Chat TTS (Streaming)") {
                logger.info("Sending Chat TTS request to ElevenLabs - Voice: $voiceId")
                logger.info("Request details - Model: ${config.model}, Output format: ${config.outputFormat}")
                logger.info("Voice settings: $voiceSettings")
                
                val response = httpClient.post("${config.baseUrl}/v1/text-to-speech/$voiceId") {
                    contentType(ContentType.Application.Json)
                    header("xi-api-key", config.apiKey)
                    setBody(request)
                    timeout {
                        requestTimeoutMillis = config.timeoutMs
                    }
                }
                
                logger.info("=== ElevenLabs API Response ===")
                logger.info("Status: ${response.status}")
                logger.info("Content-Type: ${response.headers["Content-Type"]}")
                logger.info("Content-Length: ${response.headers["Content-Length"] ?: "unknown"}")
                logger.info("Transfer-Encoding: ${response.headers["Transfer-Encoding"] ?: "none"}")
                
                if (response.status.isSuccess()) {
                    val audioBytes = response.readRawBytes()
                    logger.info("Successfully received audio: ${audioBytes.size} bytes")
                    
                    // Calculate estimated duration
                    val estimatedDurationSeconds = (audioBytes.size * 8.0) / 128000.0 // Assume 128kbps
                    logger.info("Estimated audio duration: ${String.format("%.2f", estimatedDurationSeconds)} seconds")
                    
                    // Log audio header for format verification
                    if (audioBytes.size >= 16) {
                        val headerHex = audioBytes.take(16).joinToString(" ") { "%02x".format(it) }
                        logger.info("Audio header (first 16 bytes): $headerHex")
                        
                        // Check if it's a valid MP3
                        if (audioBytes.size >= 3) {
                            val isValidMp3 = (audioBytes[0] == 'I'.code.toByte() && 
                                             audioBytes[1] == 'D'.code.toByte() && 
                                             audioBytes[2] == '3'.code.toByte()) ||
                                           ((audioBytes[0].toInt() and 0xFF) == 0xFF && 
                                            (audioBytes[1].toInt() and 0xE0) == 0xE0)
                            logger.info("Valid MP3 format: $isValidMp3")
                        }
                    }
                    
                    audioBytes
                } else {
                    val errorBody = response.bodyAsText()
                    logger.error("ElevenLabs Chat TTS failed: ${response.status} - $errorBody")
                    throw Exception("ElevenLabs Chat TTS failed: ${response.status} - $errorBody")
                }
            }
            
            logger.debug("Chat TTS generated successfully: ${audioData.size} bytes, voice: $voiceId, emotion: $emotion")
            Result.success(audioData)
        } catch (e: Exception) {
            logger.error("Chat TTS error", e)
            Result.failure(e)
        }
    }

    /**
     * 스토리 내레이션용 고품질 TTS
     * ElevenLabs 일반 Convert API 사용으로 최고 품질 제공
     */
    suspend fun generateStoryTTS(
        text: String,
        emotion: String = "NEUTRAL",
        speed: Float = 1.0f,
        addPauses: Boolean = true
    ): Result<ByteArray> {
        return try {
            val voiceId = emotionVoiceMapping[emotion.uppercase()] ?: config.defaultVoice
            // 스토리용은 더 안정적인 설정 사용
            val voiceSettings = emotionVoiceSettingsMapping[emotion.uppercase()]?.copy(
                stability = 0.85f, // 더 안정적
                style = 0.15f // 감정을 약간만 적용
            ) ?: ElevenLabsVoiceSettings(stability = 0.85f, similarity_boost = 0.8f, style = 0.15f)
            
            val enhancedText = enhanceTextForTTS(
                text = text,
                emotion = emotion,
                isChat = false,
                addPauses = addPauses
            )
            
            val request = ElevenLabsTTSRequest(
                text = enhancedText,
                model_id = config.model,
                voice_settings = voiceSettings,
                output_format = config.outputFormat
            )
            
            val audioData = executeWithRetry("Story TTS (High Quality)") {
                logger.debug("Sending Story TTS request to ElevenLabs - Voice: $voiceId, Text length: ${enhancedText.length}")
                
                val response = httpClient.post("${config.baseUrl}/v1/text-to-speech/$voiceId") {
                    contentType(ContentType.Application.Json)
                    header("xi-api-key", config.apiKey)
                    setBody(request)
                    timeout {
                        requestTimeoutMillis = config.timeoutMs * 2 // 스토리는 더 긴 시간 허용
                    }
                }
                
                logger.debug("ElevenLabs Story TTS response: ${response.status}")
                logger.debug("Response headers: ${response.headers}")
                
                if (response.status.isSuccess()) {
                    val audioBytes = response.readRawBytes()
                    logger.debug("Received Story TTS audio: ${audioBytes.size} bytes")
                    
                    // Log audio header for verification
                    if (audioBytes.size >= 16) {
                        val headerHex = audioBytes.take(16).joinToString(" ") { "%02x".format(it) }
                        logger.debug("Story TTS audio header: $headerHex")
                    }
                    
                    audioBytes
                } else {
                    val errorBody = response.bodyAsText()
                    logger.error("ElevenLabs Story TTS failed: ${response.status} - $errorBody")
                    throw Exception("ElevenLabs Story TTS failed: ${response.status} - $errorBody")
                }
            }
            
            logger.debug("Story TTS generated successfully: ${audioData.size} bytes, voice: $voiceId, emotion: $emotion")
            Result.success(audioData)
        } catch (e: Exception) {
            logger.error("Story TTS error", e)
            Result.failure(e)
        }
    }

    /**
     * 호환성을 위한 레거시 메서드
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
     * 스트리밍 TTS (문장 단위로 분할하여 스트리밍)
     */
    fun textToSpeechStream(
        text: String,
        emotion: String = "NEUTRAL",
        voice: String = config.defaultVoice
    ): Flow<ByteArray> = flow {
        try {
            // 문장 단위로 분할하여 스트리밍
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
     * 텍스트 전처리 (자연스러운 읽기를 위한)
     */
    private fun enhanceTextForTTS(
        text: String, 
        emotion: String,
        isChat: Boolean = true,
        addPauses: Boolean = false
    ): String {
        var enhancedText = text.trim()
        
        // 한국어 텍스트 최적화
        enhancedText = enhancedText
            .replace("...", if (addPauses) "… " else "…") // 적절한 ellipsis
            .replace("  ", " ") // 이중 공백 제거
        
        if (addPauses && !isChat) {
            // 스토리 내레이션용 자연스러운 일시정지 추가
            enhancedText = enhancedText
                .replace(".", ". ") // 문장 끝 일시정지
                .replace("!", "! ") // 느낌표 후 일시정지
                .replace("?", "? ") // 물음표 후 일시정지
                .replace(",", ", ") // 쉼표 후 짧은 일시정지
        }
        
        // 문장 끝 정리 (자연스러운 억양을 위해)
        if (!enhancedText.endsWith(".") && !enhancedText.endsWith("!") && !enhancedText.endsWith("?")) {
            enhancedText += "."
        }
        
        return enhancedText.trim()
    }

    /**
     * 사용 가능한 감정 목록 반환
     */
    fun getAvailableEmotions(): Map<String, String> {
        return emotionVoiceMapping.toMap()
    }

    /**
     * 사용 가능한 음성 ID 목록 반환
     */
    fun getAvailableVoices(): List<String> {
        return emotionVoiceMapping.values.distinct()
    }

    /**
     * ElevenLabs API 상태 확인
     */
    suspend fun getHealthStatus(): String {
        return try {
            executeWithRetry("Health Check") {
                // 간단한 텍스트로 API 상태 확인
                val testRequest = ElevenLabsTTSRequest(
                    text = "test",
                    model_id = config.model
                )
                
                val response = httpClient.post("${config.baseUrl}/v1/text-to-speech/${config.defaultVoice}") {
                    contentType(ContentType.Application.Json)
                    header("xi-api-key", config.apiKey)
                    setBody(testRequest)
                    timeout {
                        requestTimeoutMillis = config.timeoutMs / 2
                    }
                }
                
                if (response.status.isSuccess()) {
                    "ElevenLabs TTS service is healthy"
                } else {
                    throw Exception("Health check failed: ${response.status} - ${response.bodyAsText()}")
                }
            }
        } catch (e: Exception) {
            "ElevenLabs TTS service health check error: ${e.message}"
        }
    }

    /**
     * 서비스 정보 반환
     */
    fun getServiceInfo(): String {
        return buildString {
            appendLine("ElevenLabs TTS Service Information:")
            appendLine("- Base URL: ${config.baseUrl}")
            appendLine("- Model: ${config.model}")
            appendLine("- Default Voice: ${config.defaultVoice}")
            appendLine("- Output Format: ${config.outputFormat}")
            appendLine("- Streaming Latency Optimization: ${config.streamingLatencyOptimization}")
            appendLine("- Max Retries: ${config.maxRetries}")
            appendLine("- Available Emotions: ${emotionVoiceMapping.keys.joinToString(", ")}")
            appendLine("- Available Voices: ${getAvailableVoices().joinToString(", ")}")
        }
    }

    /**
     * 제목과 함께 스토리 오디오 생성
     */
    suspend fun generateStoryAudio(
        storyContent: String,
        title: String,
        emotion: String = "NEUTRAL"
    ): Result<ByteArray> {
        return try {
            // 내레이션 형식으로 제목 포함
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
     * 리소스 정리
     */
    fun close() {
        logger.info("ElevenLabsTTSService closed")
    }
}