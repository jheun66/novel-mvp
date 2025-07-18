package com.novel.mvp.presentation.websocket

import com.novel.mvp.base.MviIntent
import com.novel.mvp.base.MviSideEffect
import com.novel.mvp.base.MviViewState
import com.novel.mvp.data.model.WebSocketMessage
import com.novel.mvp.data.websocket.ConnectionState

// Intent
sealed class WebSocketTestIntent : MviIntent {
    object Connect : WebSocketTestIntent()
    object Disconnect : WebSocketTestIntent()
    data class SendMessage(val text: String) : WebSocketTestIntent()
    data class GenerateStory(val conversationId: String) : WebSocketTestIntent()
    object ClearMessages : WebSocketTestIntent()
    object ClearError : WebSocketTestIntent()
}

// ViewState
data class WebSocketTestViewState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isLoading: Boolean = false,
    val conversations: List<TestConversationMessage> = emptyList(),
    val currentStory: WebSocketMessage.StoryOutput? = null,
    val error: String? = null,
    val conversationId: String = "",
    val isReadyForStory: Boolean = false,
    val inputText: String = ""
) : MviViewState

// SideEffect
sealed class WebSocketTestSideEffect : MviSideEffect {
    data class ShowError(val message: String) : WebSocketTestSideEffect()
    data class ShowSuccess(val message: String) : WebSocketTestSideEffect()
    object ScrollToBottom : WebSocketTestSideEffect()
    object ClearInput : WebSocketTestSideEffect()
}

// Helper data classes
data class TestConversationMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val emotion: String? = null,
    val suggestedQuestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: String = "text" // "text", "story", "error", "auth"
)