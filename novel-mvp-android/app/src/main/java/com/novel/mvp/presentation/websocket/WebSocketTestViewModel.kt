package com.novel.mvp.presentation.websocket

import androidx.lifecycle.viewModelScope
import com.novel.mvp.base.BaseViewModel
import com.novel.mvp.data.model.WebSocketMessage
import com.novel.mvp.data.repository.StoryRepository
import com.novel.mvp.data.websocket.ConnectionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class WebSocketTestViewModel(
    private val storyRepository: StoryRepository
) : BaseViewModel<WebSocketTestIntent, WebSocketTestViewState, WebSocketTestSideEffect>() {

    init {
        observeConnectionState()
        observeMessages()
    }

    override fun createInitialState(): WebSocketTestViewState = WebSocketTestViewState()

    override fun handleIntent(intent: WebSocketTestIntent) {
        when (intent) {
            is WebSocketTestIntent.Connect -> handleConnect()
            is WebSocketTestIntent.Disconnect -> handleDisconnect()
            is WebSocketTestIntent.SendMessage -> handleSendMessage(intent.text)
            is WebSocketTestIntent.GenerateStory -> handleGenerateStory(intent.conversationId)
            is WebSocketTestIntent.ClearMessages -> handleClearMessages()
            is WebSocketTestIntent.ClearError -> handleClearError()
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            storyRepository.connectionState.collectLatest { connectionState ->
                setState { copy(connectionState = connectionState) }
                
                when (connectionState) {
                    ConnectionState.CONNECTING -> {
                        setState { copy(isLoading = true) }
                    }
                    ConnectionState.CONNECTED -> {
                        setState { copy(isLoading = false) }
                        addSystemMessage("WebSocket 연결됨 - 인증 중...")
                    }
                    ConnectionState.AUTHENTICATED -> {
                        setState { copy(isLoading = false) }
                        addSystemMessage("WebSocket 인증 완료! 메시지를 보낼 수 있습니다.")
                        sendSideEffect(WebSocketTestSideEffect.ShowSuccess("WebSocket 연결 및 인증 성공!"))
                    }
                    ConnectionState.DISCONNECTED -> {
                        setState { copy(isLoading = false) }
                        addSystemMessage("WebSocket 연결 해제됨")
                    }
                }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            // Observe text messages
            storyRepository.textMessages.collectLatest { textMessage ->
                addConversationMessage(
                    text = textMessage.text,
                    isFromUser = false,
                    emotion = textMessage.emotion,
                    suggestedQuestions = textMessage.suggestedQuestions
                )
                
                setState { 
                    copy(isReadyForStory = textMessage.readyForStory)
                }
                
                if (textMessage.readyForStory) {
                    addSystemMessage("✨ 스토리 생성 준비 완료! '스토리 생성' 버튼을 눌러보세요.")
                }
                
                sendSideEffect(WebSocketTestSideEffect.ScrollToBottom)
            }
        }
        
        viewModelScope.launch {
            // Observe story messages
            storyRepository.storyMessages.collectLatest { storyMessage ->
                setState { copy(currentStory = storyMessage) }
                
                addStoryMessage(storyMessage)
                sendSideEffect(WebSocketTestSideEffect.ScrollToBottom)
                sendSideEffect(WebSocketTestSideEffect.ShowSuccess("스토리가 생성되었습니다!"))
            }
        }
        
        viewModelScope.launch {
            // Observe error messages
            storyRepository.errorMessages.collectLatest { errorMessage ->
                addErrorMessage(errorMessage.message)
                sendSideEffect(WebSocketTestSideEffect.ShowError(errorMessage.message))
            }
        }
        
        viewModelScope.launch {
            // Observe auth messages
            storyRepository.authMessages.collectLatest { authMessage ->
                if (authMessage.success) {
                    addSystemMessage("✅ 인증 성공: ${authMessage.message ?: "인증 완료"}")
                } else {
                    addErrorMessage("❌ 인증 실패: ${authMessage.message ?: "인증 오류"}")
                }
            }
        }
    }

    private fun handleConnect() {
        if (currentState.connectionState != ConnectionState.DISCONNECTED) {
            sendSideEffect(WebSocketTestSideEffect.ShowError("이미 연결되어 있습니다."))
            return
        }
        
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            
            try {
                val success = storyRepository.connect()
                if (!success) {
                    setState { copy(isLoading = false, error = "WebSocket 연결 실패") }
                    addErrorMessage("WebSocket 연결에 실패했습니다.")
                    sendSideEffect(WebSocketTestSideEffect.ShowError("연결 실패"))
                }
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message) }
                addErrorMessage("연결 오류: ${e.message}")
                sendSideEffect(WebSocketTestSideEffect.ShowError("연결 오류: ${e.message}"))
            }
        }
    }

    private fun handleDisconnect() {
        viewModelScope.launch {
            try {
                storyRepository.disconnect()
                setState { 
                    copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        isLoading = false,
                        error = null,
                        isReadyForStory = false,
                        currentStory = null
                    )
                }
                addSystemMessage("연결을 종료했습니다.")
                sendSideEffect(WebSocketTestSideEffect.ShowSuccess("연결 종료됨"))
            } catch (e: Exception) {
                sendSideEffect(WebSocketTestSideEffect.ShowError("연결 종료 오류: ${e.message}"))
            }
        }
    }

    private fun handleSendMessage(text: String) {
        if (currentState.connectionState != ConnectionState.AUTHENTICATED) {
            sendSideEffect(WebSocketTestSideEffect.ShowError("먼저 WebSocket에 연결하세요."))
            return
        }
        
        if (text.trim().isEmpty()) {
            sendSideEffect(WebSocketTestSideEffect.ShowError("메시지를 입력하세요."))
            return
        }
        
        viewModelScope.launch {
            try {
                // Generate conversation ID if first message
                val conversationId = if (currentState.conversationId.isEmpty()) {
                    UUID.randomUUID().toString().also { newId ->
                        setState { copy(conversationId = newId) }
                    }
                } else {
                    currentState.conversationId
                }
                
                // Add user message to conversation
                addConversationMessage(text = text, isFromUser = true)
                
                // Send message to server
                storyRepository.sendMessage(text, conversationId)
                
                sendSideEffect(WebSocketTestSideEffect.ClearInput)
                sendSideEffect(WebSocketTestSideEffect.ScrollToBottom)
                
            } catch (e: Exception) {
                sendSideEffect(WebSocketTestSideEffect.ShowError("메시지 전송 실패: ${e.message}"))
            }
        }
    }

    private fun handleGenerateStory(conversationId: String) {
        if (currentState.connectionState != ConnectionState.AUTHENTICATED) {
            sendSideEffect(WebSocketTestSideEffect.ShowError("먼저 WebSocket에 연결하세요."))
            return
        }
        
        if (!currentState.isReadyForStory) {
            sendSideEffect(WebSocketTestSideEffect.ShowError("아직 스토리 생성 준비가 되지 않았습니다. 더 대화해보세요."))
            return
        }
        
        viewModelScope.launch {
            try {
                addSystemMessage("🎬 스토리 생성 중...")
                storyRepository.generateStory(conversationId)
                setState { copy(isReadyForStory = false) }
            } catch (e: Exception) {
                sendSideEffect(WebSocketTestSideEffect.ShowError("스토리 생성 실패: ${e.message}"))
            }
        }
    }

    private fun handleClearMessages() {
        setState { 
            copy(
                conversations = emptyList(),
                currentStory = null,
                conversationId = "",
                isReadyForStory = false
            ) 
        }
        addSystemMessage("메시지가 초기화되었습니다.")
    }

    private fun handleClearError() {
        setState { copy(error = null) }
    }

    private fun addConversationMessage(
        text: String,
        isFromUser: Boolean,
        emotion: String? = null,
        suggestedQuestions: List<String> = emptyList()
    ) {
        val message = TestConversationMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isFromUser = isFromUser,
            emotion = emotion,
            suggestedQuestions = suggestedQuestions,
            messageType = "text"
        )
        
        setState { 
            copy(conversations = conversations + message)
        }
    }

    private fun addSystemMessage(text: String) {
        val message = TestConversationMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isFromUser = false,
            messageType = "system"
        )
        
        setState { 
            copy(conversations = conversations + message)
        }
    }

    private fun addErrorMessage(text: String) {
        val message = TestConversationMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isFromUser = false,
            messageType = "error"
        )
        
        setState { 
            copy(conversations = conversations + message)
        }
    }

    private fun addStoryMessage(storyOutput: WebSocketMessage.StoryOutput) {
        val storyText = """
            📖 **${storyOutput.title}**
            
            ${storyOutput.content}
            
            • 장르: ${storyOutput.genre}
            • 감정: ${storyOutput.emotion}
            • 감정 아크: ${storyOutput.emotionalArc}
        """.trimIndent()
        
        val message = TestConversationMessage(
            id = UUID.randomUUID().toString(),
            text = storyText,
            isFromUser = false,
            messageType = "story"
        )
        
        setState { 
            copy(conversations = conversations + message)
        }
    }
}