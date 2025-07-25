package com.novel.mvp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed class WebSocketMessage {
    @Serializable
    @SerialName("AudioInput")
    data class AudioInput(
        val audioData: String,  // Base64 encoded audio
        val format: String = "wav",
        val sampleRate: Int = 16000,
        val conversationId: String,
        val isStreaming: Boolean = false
    ) : WebSocketMessage()

    @Serializable
    @SerialName("TextInput")
    data class TextInput(
        val text: String,
        val conversationId: String
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
        val format: String = "mp3",  // ElevenLabs default: MP3
        val sampleRate: Int = 44100,  // ElevenLabs default: 44.1kHz
        val emotion: String? = null,
        val duration: Float? = null,
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
        val audioFormat: String = "mp3",  // ElevenLabs audio format
        val audioSampleRate: Int = 44100  // ElevenLabs sample rate
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
    @SerialName("TranscriptionResult")
    data class TranscriptionResult(
        val conversationId: String,
        val text: String,
        val confidence: Float? = null,
        val timestamp: Long = System.currentTimeMillis()
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