package com.novel.mvp.presentation.story

import com.novel.mvp.base.MviIntent
import com.novel.mvp.base.MviSideEffect
import com.novel.mvp.base.MviViewState
import com.novel.mvp.data.model.WebSocketMessage
import com.novel.mvp.data.websocket.ConnectionState

// Intent
sealed class StoryIntent : MviIntent {
    object ConnectWebSocket : StoryIntent()
    object DisconnectWebSocket : StoryIntent()
    data class SendMessage(val text: String) : StoryIntent()
    data class SendAudioMessage(val audioData: ByteArray, val conversationId: String) : StoryIntent()
    data class GenerateStory(val conversationId: String) : StoryIntent()
    object ClearMessages : StoryIntent()
    object StartRecording : StoryIntent()
    object StopRecording : StoryIntent()
    object RequestAudioPermission : StoryIntent()
    
    
    // Streaming audio intents
    object StartAudioStreaming : StoryIntent()
    object StopAudioStreaming : StoryIntent()
    
    // Permission related intents
    object CheckAudioPermission : StoryIntent()
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

    val transcriptionResult: String? = null,

    val isAudioStreaming: Boolean = false,
    val streamingTranscriptionResult: String? = null,
    val streamingTranscriptionIsPartial: Boolean = false
) : MviViewState

// SideEffect
sealed class StorySideEffect : MviSideEffect {
    data class ShowError(val message: String) : StorySideEffect()
    data class PlayAudio(val audioData: String, val format: String = "mp3") : StorySideEffect()
    data class PlayStoryAudio(val audioData: String, val format: String = "mp3") : StorySideEffect()
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
    VOICE,
    WHISPER_STREAMING
}