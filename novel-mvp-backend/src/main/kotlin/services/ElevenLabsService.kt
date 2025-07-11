package com.novel.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class ElevenLabsConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.elevenlabs.io/v1",
    val defaultVoiceId: String = "4JJwo477JUAx3HV0T7n7", // YohanKoo (Korean)
    val model: String = "eleven_multilingual_v2"
)

@Serializable
data class TTSRequest(
    val text: String,
    val model_id: String,
    val voice_settings: VoiceSettings? = null
)

@Serializable
data class VoiceSettings(
    val stability: Float = 0.5f,
    val similarity_boost: Float = 0.8f,
    val style: Float = 0.2f,
    val use_speaker_boost: Boolean = true
)

@Serializable
data class Voice(
    val voice_id: String,
    val name: String,
    val category: String? = null,
    val description: String? = null
)

@Serializable
data class VoicesResponse(
    val voices: List<Voice>
)

@Serializable
data class EmotionParameters(
    val emotion: String,
    val intensity: Float = 0.5f,
    val voiceSettings: VoiceSettings
)

/**
 * ElevenLabs integration for high-quality TTS with emotion support
 */
class ElevenLabsService(
    private val config: ElevenLabsConfig,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(ElevenLabsService::class.java)

    // 감정별 미리 정의된 목소리 설정
    private val emotionVoiceSettings = mapOf(
        "HAPPY" to VoiceSettings(
            stability = 0.3f,
            similarity_boost = 0.8f,
            style = 0.4f,
            use_speaker_boost = true
        ),
        "SAD" to VoiceSettings(
            stability = 0.7f,
            similarity_boost = 0.9f,
            style = 0.1f,
            use_speaker_boost = false
        ),
        "EXCITED" to VoiceSettings(
            stability = 0.2f,
            similarity_boost = 0.7f,
            style = 0.6f,
            use_speaker_boost = true
        ),
        "CALM" to VoiceSettings(
            stability = 0.8f,
            similarity_boost = 0.9f,
            style = 0.0f,
            use_speaker_boost = false
        ),
        "ANGRY" to VoiceSettings(
            stability = 0.4f,
            similarity_boost = 0.8f,
            style = 0.5f,
            use_speaker_boost = true
        ),
        "GRATEFUL" to VoiceSettings(
            stability = 0.6f,
            similarity_boost = 0.9f,
            style = 0.2f,
            use_speaker_boost = true
        ),
        "ANXIOUS" to VoiceSettings(
            stability = 0.3f,
            similarity_boost = 0.8f,
            style = 0.3f,
            use_speaker_boost = true
        )
    )

    // 한국어 지원 목소리 ID들
    private val koreanCompatibleVoices = mapOf(
        "korean-female-1" to "ksaI0TCD9BstzEzlxj4q", // Seulki
        "korean-male-1" to "4JJwo477JUAx3HV0T7n7", // YohanKoo
        "korean-child-1" to "xi3rF0t7dg7uN2M0WUhr", // Yuna
        "narrator" to "uyVNoMrnUku1dZyVEXwD" // Anna Kim
    )
    
    /**
     * Convert text to speech with emotion using ElevenLabs
     */
    suspend fun textToSpeech(
        text: String,
        emotion: String = "NEUTRAL",
        voice: String = "korean-female-1"
    ): ByteArray {
        try {
            val voiceId = koreanCompatibleVoices[voice] ?: config.defaultVoiceId
            val voiceSettings = getVoiceSettingsForEmotion(emotion)
            
            // 감정 마커를 텍스트에 추가
            val enhancedText = enhanceTextWithEmotionMarkers(text, emotion)
            
            val request = TTSRequest(
                text = enhancedText,
                model_id = config.model,
                voice_settings = voiceSettings
            )
            
            val response = httpClient.post("${config.baseUrl}/text-to-speech/$voiceId") {
                header("xi-api-key", config.apiKey)
                header("Accept", "audio/mpeg")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            return if (response.status.isSuccess()) {
                response.readBytes()
            } else {
                logger.error("TTS failed with status: ${response.status}, body: ${response.bodyAsText()}")
                ByteArray(0)
            }
        } catch (e: Exception) {
            logger.error("TTS error", e)
            return ByteArray(0)
        }
    }
    
    /**
     * Stream TTS with real-time emotion modulation using ElevenLabs streaming API
     */
    suspend fun textToSpeechStream(
        text: String,
        emotion: String = "NEUTRAL",
        voice: String = "korean-female-1"
    ): ByteArray {
        try {
            val voiceId = koreanCompatibleVoices[voice] ?: config.defaultVoiceId
            val voiceSettings = getVoiceSettingsForEmotion(emotion)
            val enhancedText = enhanceTextWithEmotionMarkers(text, emotion)
            
            val request = TTSRequest(
                text = enhancedText,
                model_id = config.model,
                voice_settings = voiceSettings
            )
            
            val response = httpClient.post("${config.baseUrl}/text-to-speech/$voiceId/stream") {
                header("xi-api-key", config.apiKey)
                header("Accept", "audio/mpeg")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            return if (response.status.isSuccess()) {
                response.readBytes()
            } else {
                logger.error("Streaming TTS failed with status: ${response.status}")
                ByteArray(0)
            }
        } catch (e: Exception) {
            logger.error("Streaming TTS error", e)
            return ByteArray(0)
        }
    }
    
    /**
     * Flow 버전의 스트리밍 (문장별 분할)
     */
    fun textToSpeechStreamFlow(
        text: String,
        emotion: String = "NEUTRAL",
        voice: String = "korean-female-1"
    ): Flow<ByteArray> = flow {
        val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }
        
        sentences.forEach { sentence ->
            val audioChunk = textToSpeech(
                text = sentence.trim(),
                emotion = emotion,
                voice = voice
            )
            if (audioChunk.isNotEmpty()) {
                emit(audioChunk)
            }
        }
    }
    
    /**
     * Get available voices from ElevenLabs
     */
    suspend fun getAvailableVoices(): List<Voice> {
        return try {
            val response = httpClient.get("${config.baseUrl}/voices") {
                header("xi-api-key", config.apiKey)
            }
            
            if (response.status.isSuccess()) {
                val voicesResponse = response.body<VoicesResponse>()
                voicesResponse.voices
            } else {
                logger.error("Failed to get voices: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Error getting voices", e)
            emptyList()
        }
    }
    
    /**
     * Get voice settings optimized for specific emotion
     */
    private fun getVoiceSettingsForEmotion(emotion: String): VoiceSettings {
        return emotionVoiceSettings[emotion.uppercase()] ?: VoiceSettings()
    }
    
    /**
     * Enhance text with emotion markers for better ElevenLabs processing
     */
    private fun enhanceTextWithEmotionMarkers(text: String, emotion: String): String {
        return when (emotion.uppercase()) {
            "HAPPY" -> text // ElevenLabs가 자동으로 톤 조절
            "SAD" -> text
            "EXCITED" -> "!$text!" // 흥분감을 위한 느낌표
            "CALM" -> text
            "ANGRY" -> text.uppercase() // 화남을 표현하기 위해 대문자 사용하지 않음 (자연스럽지 않을 수 있음)
            "GRATEFUL" -> text
            "ANXIOUS" -> text
            else -> text
        }
    }
    
    /**
     * Get emotion parameters for compatibility with existing code
     */
    private fun getEmotionParameters(emotion: String): EmotionParameters {
        val voiceSettings = getVoiceSettingsForEmotion(emotion)
        return EmotionParameters(
            emotion = emotion,
            intensity = when (emotion.uppercase()) {
                "HAPPY", "EXCITED" -> 0.8f
                "SAD", "CALM" -> 0.6f
                "ANGRY", "ANXIOUS" -> 0.7f
                "GRATEFUL" -> 0.6f
                else -> 0.5f
            },
            voiceSettings = voiceSettings
        )
    }
    
    /**
     * Advanced emotion mixing for complex emotional states
     */
    fun mixEmotions(
        primaryEmotion: String,
        subEmotions: List<String>,
        weights: List<Float> = emptyList()
    ): EmotionParameters {
        val primary = getEmotionParameters(primaryEmotion)
        if (subEmotions.isEmpty()) return primary
        
        // ElevenLabs는 단일 감정 설정을 사용하므로 primary emotion을 기준으로 함
        // 향후 더 복잡한 믹싱이 필요하면 여러 번의 API 호출을 통해 구현 가능
        logger.info("Emotion mixing: Using primary emotion $primaryEmotion with hints from $subEmotions")
        
        return primary.copy(
            emotion = "$primaryEmotion-mixed",
            voiceSettings = primary.voiceSettings.copy(
                style = minOf(primary.voiceSettings.style + 0.1f, 1.0f) // 약간의 스타일 강화
            )
        )
    }
    
    /**
     * Check API quota and usage
     */
    suspend fun getUsageInfo(): String {
        return try {
            val response = httpClient.get("${config.baseUrl}/user") {
                header("xi-api-key", config.apiKey)
            }
            
            if (response.status.isSuccess()) {
                "API quota check successful"
            } else {
                "Failed to check quota: ${response.status}"
            }
        } catch (e: Exception) {
            "Error checking quota: ${e.message}"
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        httpClient.close()
    }
}
