package com.novel.mvp.presentation.story

import com.novel.mvp.data.model.WebSocketMessage
import com.novel.mvp.data.websocket.ConnectionState

// Intent
sealed class StoryIntent {
    object ConnectWebSocket : StoryIntent()
    object DisconnectWebSocket : StoryIntent()
    data class SendMessage(val text: String) : StoryIntent()
    data class GenerateStory(val conversationId: String) : StoryIntent()
    object ClearMessages : StoryIntent()
}

// ViewState
data class StoryViewState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isLoading: Boolean = false,
    val conversations: List<ConversationMessage> = emptyList(),
    val currentStory: WebSocketMessage.StoryOutput? = null,
    val error: String? = null,
    val conversationId: String = "",
    val isReadyForStory: Boolean = false
)

// SideEffect
sealed class StorySideEffect {
    data class ShowError(val message: String) : StorySideEffect()
    data class PlayAudio(val audioData: String) : StorySideEffect()
    object ScrollToBottom : StorySideEffect()
}

// Helper data classes
data class ConversationMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val emotion: String? = null,
    val suggestedQuestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)