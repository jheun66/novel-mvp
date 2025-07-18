package com.novel.mvp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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