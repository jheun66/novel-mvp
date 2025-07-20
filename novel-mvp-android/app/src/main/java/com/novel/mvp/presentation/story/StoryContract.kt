package com.novel.mvp.presentation.story

import com.novel.mvp.data.model.WebSocketMessage
import com.novel.mvp.data.websocket.ConnectionState

// Intent
sealed class StoryIntent {
    object ConnectWebSocket : StoryIntent()
    object DisconnectWebSocket : StoryIntent()
    data class SendMessage(val text: String) : StoryIntent()
    data class SendAudioMessage(val audioData: ByteArray, val conversationId: String) : StoryIntent()
    data class GenerateStory(val conversationId: String) : StoryIntent()
    object ClearMessages : StoryIntent()
    object StartRecording : StoryIntent()
    object StopRecording : StoryIntent()
    object RequestAudioPermission : StoryIntent()
    data class SendAudioEchoTest(val audioData: ByteArray, val conversationId: String) : StoryIntent()
}

// ViewState
data class StoryViewState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isLoading: Boolean = false,
    val conversations: List<ConversationMessage> = emptyList(),
    val currentStory: WebSocketMessage.StoryOutput? = null,
    val error: String? = null,
    val conversationId: String = "",
    val isReadyForStory: Boolean = false,
    val isRecording: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val transcribingAudio: Boolean = false,
    val pendingTranscription: String? = null
)

// SideEffect
sealed class StorySideEffect {
    data class ShowError(val message: String) : StorySideEffect()
    data class PlayAudio(val audioData: String) : StorySideEffect()
    object ScrollToBottom : StorySideEffect()
    object RequestAudioPermission : StorySideEffect()
    object StartAudioRecording : StorySideEffect()
    object StopAudioRecording : StorySideEffect()
}

// Helper data classes
data class ConversationMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val emotion: String? = null,
    val suggestedQuestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val inputType: MessageInputType = MessageInputType.TEXT,
    val audioData: String? = null,
    val transcriptionSource: String? = null // Original voice input text before sending
)

enum class MessageInputType {
    TEXT,
    VOICE
}