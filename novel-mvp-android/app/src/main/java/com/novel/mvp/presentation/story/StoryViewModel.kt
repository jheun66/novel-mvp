package com.novel.mvp.presentation.story

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novel.mvp.data.model.*
import com.novel.mvp.data.repository.StoryRepository
import com.novel.mvp.data.websocket.ConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class StoryViewModel(
    private val storyRepository: StoryRepository,
    private val context: Context
) : ViewModel() {

    private val _viewState = MutableStateFlow(StoryViewState())
    val viewState: StateFlow<StoryViewState> = _viewState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<StorySideEffect>()
    val sideEffect: SharedFlow<StorySideEffect> = _sideEffect.asSharedFlow()

    private var currentConversationId: String = UUID.randomUUID().toString()

    init {
        checkAudioPermission()
        observeWebSocketState()
        observeWebSocketMessages()
        connectWebSocket()
    }

    fun handleIntent(intent: StoryIntent) {
        when (intent) {
            is StoryIntent.ConnectWebSocket -> connectWebSocket()
            is StoryIntent.DisconnectWebSocket -> disconnectWebSocket()
            is StoryIntent.SendMessage -> sendMessage(intent.text)
            is StoryIntent.SendAudioMessage -> sendAudioMessage(intent.audioData, intent.conversationId)
            is StoryIntent.GenerateStory -> generateStory(intent.conversationId)
            is StoryIntent.ClearMessages -> clearMessages()
            is StoryIntent.StartRecording -> startRecording()
            is StoryIntent.StopRecording -> stopRecording()
            is StoryIntent.RequestAudioPermission -> requestAudioPermission()
            is StoryIntent.SendAudioEchoTest -> sendAudioEchoTest(intent.audioData, intent.conversationId)
        }
    }

    private fun observeWebSocketState() {
        viewModelScope.launch {
            storyRepository.connectionState.collect { connectionState ->
                _viewState.value = _viewState.value.copy(
                    connectionState = connectionState,
                    isLoading = connectionState == ConnectionState.CONNECTING
                )
            }
        }
    }

    private fun observeWebSocketMessages() {
        viewModelScope.launch {
            // Observe text messages
            launch {
                storyRepository.textMessages.collect { message ->
                    // Check if this is a transcription result
                    if (_viewState.value.transcribingAudio) {
                        // Add the transcribed voice message to conversation
                        addConversationMessage(
                            ConversationMessage(
                                id = UUID.randomUUID().toString(),
                                text = message.text,
                                isFromUser = true,
                                inputType = MessageInputType.VOICE,
                                transcriptionSource = message.text
                            )
                        )
                        
                        // Reset transcribing state and show transcription
                        _viewState.value = _viewState.value.copy(
                            transcribingAudio = false,
                            pendingTranscription = message.text,
                            isLoading = true
                        )
                    } else {
                        // Regular AI response
                        addConversationMessage(
                            ConversationMessage(
                                id = UUID.randomUUID().toString(),
                                text = message.text,
                                isFromUser = false,
                                emotion = message.emotion,
                                suggestedQuestions = message.suggestedQuestions
                            )
                        )

                        _viewState.value = _viewState.value.copy(
                            isReadyForStory = message.readyForStory,
                            isLoading = false,
                            pendingTranscription = null
                        )

                        _sideEffect.emit(StorySideEffect.ScrollToBottom)
                    }
                }
            }
            
            // Observe story messages
            launch {
                storyRepository.storyMessages.collect { message ->
                    _viewState.value = _viewState.value.copy(
                        currentStory = message,
                        isLoading = false
                    )
                }
            }
            
            // Observe audio messages
            launch {
                storyRepository.audioMessages.collect { message ->
                    _sideEffect.emit(StorySideEffect.PlayAudio(message.audioData))
                }
            }
            
            // Observe error messages
            launch {
                storyRepository.errorMessages.collect { message ->
                    _viewState.value = _viewState.value.copy(
                        error = message.message,
                        isLoading = false
                    )
                    _sideEffect.emit(StorySideEffect.ShowError(message.message))
                }
            }
        }
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                val success = storyRepository.connect()
                if (!success) {
                    _viewState.value = _viewState.value.copy(
                        error = "WebSocket 연결에 실패했습니다"
                    )
                    _sideEffect.emit(StorySideEffect.ShowError("WebSocket 연결에 실패했습니다"))
                }
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    error = e.message ?: "알 수 없는 오류가 발생했습니다"
                )
                _sideEffect.emit(StorySideEffect.ShowError(e.message ?: "알 수 없는 오류가 발생했습니다"))
            }
        }
    }

    private fun disconnectWebSocket() {
        viewModelScope.launch {
            storyRepository.disconnect()
        }
    }

    private fun sendMessage(text: String) {
        if (text.isBlank()) return

        addConversationMessage(
            ConversationMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                isFromUser = true
            )
        )

        _viewState.value = _viewState.value.copy(
            conversationId = currentConversationId,
            isReadyForStory = false,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                storyRepository.sendMessage(text, currentConversationId)
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    error = e.message ?: "메시지 전송에 실패했습니다",
                    isLoading = false
                )
                _sideEffect.emit(StorySideEffect.ShowError(e.message ?: "메시지 전송에 실패했습니다"))
            }
        }
    }

    private fun generateStory(conversationId: String) {
        _viewState.value = _viewState.value.copy(
            isLoading = true,
            currentStory = null
        )

        viewModelScope.launch {
            try {
                storyRepository.generateStory(conversationId)
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    error = e.message ?: "스토리 생성에 실패했습니다",
                    isLoading = false
                )
                _sideEffect.emit(StorySideEffect.ShowError(e.message ?: "스토리 생성에 실패했습니다"))
            }
        }
    }

    private fun clearMessages() {
        currentConversationId = UUID.randomUUID().toString()
        _viewState.value = _viewState.value.copy(
            conversations = emptyList(),
            currentStory = null,
            error = null,
            conversationId = currentConversationId,
            isReadyForStory = false
        )
    }

    private fun addConversationMessage(message: ConversationMessage) {
        val currentMessages = _viewState.value.conversations.toMutableList()
        currentMessages.add(message)
        _viewState.value = _viewState.value.copy(
            conversations = currentMessages
        )
    }

    private fun checkAudioPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        _viewState.value = _viewState.value.copy(hasAudioPermission = hasPermission)
    }

    private fun requestAudioPermission() {
        viewModelScope.launch {
            _sideEffect.emit(StorySideEffect.RequestAudioPermission)
        }
    }

    private fun startRecording() {
        if (!_viewState.value.hasAudioPermission) {
            viewModelScope.launch {
                _sideEffect.emit(StorySideEffect.RequestAudioPermission)
            }
            return
        }

        _viewState.value = _viewState.value.copy(isRecording = true)
        viewModelScope.launch {
            _sideEffect.emit(StorySideEffect.StartAudioRecording)
        }
    }

    private fun stopRecording() {
        _viewState.value = _viewState.value.copy(isRecording = false)
        viewModelScope.launch {
            _sideEffect.emit(StorySideEffect.StopAudioRecording)
        }
    }

    private fun sendAudioMessage(audioData: ByteArray, conversationId: String) {
        // Set transcribing state
        _viewState.value = _viewState.value.copy(
            transcribingAudio = true,
            pendingTranscription = null,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                storyRepository.sendAudioMessage(audioData, conversationId)
            } catch (e: Exception) {
                _viewState.value = _viewState.value.copy(
                    error = e.message ?: "음성 메시지 전송에 실패했습니다",
                    transcribingAudio = false,
                    isLoading = false
                )
                _sideEffect.emit(StorySideEffect.ShowError(e.message ?: "음성 메시지 전송에 실패했습니다"))
            }
        }
    }

    private fun sendAudioEchoTest(audioData: ByteArray, conversationId: String) {
        viewModelScope.launch {
            try {
                storyRepository.sendAudioEchoTest(audioData, conversationId)
                _sideEffect.emit(StorySideEffect.ShowError("오디오 에코 테스트 전송됨 (${audioData.size} bytes)"))
            } catch (e: Exception) {
                _sideEffect.emit(StorySideEffect.ShowError("에코 테스트 실패: ${e.message}"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            storyRepository.disconnect()
        }
    }
}