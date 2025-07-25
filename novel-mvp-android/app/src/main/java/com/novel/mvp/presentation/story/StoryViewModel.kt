package com.novel.mvp.presentation.story

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.novel.mvp.base.BaseViewModel
import com.novel.mvp.data.repository.StoryRepository
import com.novel.mvp.data.websocket.ConnectionState
import com.novel.mvp.utils.AudioStreamRecorder
import com.novel.mvp.utils.AudioStreamState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class StoryViewModel(
    private val storyRepository: StoryRepository,
    private val context: Context
) : BaseViewModel<StoryIntent, StoryViewState, StorySideEffect>() {
    override fun createInitialState(): StoryViewState = StoryViewState()

    override fun handleIntent(intent: StoryIntent) {
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
            is StoryIntent.StartAudioStreaming -> startAudioStreaming()
            is StoryIntent.StopAudioStreaming -> stopAudioStreaming()
            is StoryIntent.CheckAudioPermission -> checkAudioPermission()
        }
    }

    private var currentConversationId: String = UUID.randomUUID().toString()
    
    private val audioStreamRecorder = AudioStreamRecorder(context)
    
    // Current jobs for voice processing
    private var audioStreamingJob: Job? = null

    init {
        checkAudioPermission()
        observeWebSocketState()
        observeWebSocketMessages()
        connectWebSocket()
    }

    private fun observeWebSocketState() {
        viewModelScope.launch {
            storyRepository.connectionState.collect { connectionState ->
                setState {
                    copy(
                        connectionState = connectionState,
                        isLoading = connectionState == ConnectionState.CONNECTING
                    )
                }
            }
        }
    }

    private fun observeWebSocketMessages() {
        viewModelScope.launch {
            // Observe text messages
            launch {
                storyRepository.textMessages.collect { message ->
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

                    setState {
                        copy(
                            isReadyForStory = message.readyForStory,
                            isLoading = false,
                            transcribingAudio = false  // Ensure transcribingAudio is also reset
                        )
                    }

                    sendSideEffect(StorySideEffect.ScrollToBottom)
                }
            }
            
            // Observe story messages
            launch {
                storyRepository.storyMessages.collect { message ->
                    setState {
                        copy(
                            currentStory = message,
                            isLoading = false
                        )
                    }
                    
                    // Play story audio if available
                    message.audioData?.let { audioData ->
                        sendSideEffect(StorySideEffect.PlayStoryAudio(audioData, message.audioFormat))
                    }
                }
            }
            
            // Observe audio messages
            launch {
                storyRepository.audioMessages.collect { message ->
                    sendSideEffect(StorySideEffect.PlayAudio(message.audioData, message.format))
                }
            }
            
            // Observe error messages
            launch {
                storyRepository.errorMessages.collect { message ->
                    setState {
                        copy(
                            error = message.message,
                            isLoading = false
                        )
                    }
                    sendSideEffect(StorySideEffect.ShowError(message.message))
                }
            }

            // Observe transcription results
            launch {
                storyRepository.transcriptionMessages.collect { message ->
                    setState {
                        copy(
                            transcriptionResult = message.text,
                        )
                    }

                    if (message.text.isNotBlank()) {
                        sendTranscriptionMessage(message.text)
                    }
                }
            }
            
            // Observe streaming transcription results
            launch {
                storyRepository.streamingTranscriptionMessages.collect { message ->
                    setState {
                        copy(
                            streamingTranscriptionResult = message.text,
                            streamingTranscriptionIsPartial = message.isPartial
                        )
                    }

                    // If it's a final result, send as a text message with WHISPER_STREAMING input type
                    if (!message.isPartial && message.text.isNotBlank()) {
                        sendWhisperStreamingMessage(message.text)
                    }
                }
            }
        }
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                val success = storyRepository.connect()
                if (!success) {
                    setState {
                        copy(
                            error = "WebSocket 연결에 실패했습니다"
                        )
                    }
                    sendSideEffect(StorySideEffect.ShowError("WebSocket 연결에 실패했습니다"))
                }
            } catch (e: Exception) {
                setState {
                    copy(
                        error = e.message ?: "알 수 없는 오류가 발생했습니다"
                    )
                }
                sendSideEffect(StorySideEffect.ShowError(e.message ?: "알 수 없는 오류가 발생했습니다"))
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

        setState {
            copy(
                conversationId = currentConversationId,
                isReadyForStory = false,
                isLoading = true
            )
        }

        viewModelScope.launch {
            try {
                storyRepository.sendMessage(text, currentConversationId)
            } catch (e: Exception) {
                setState {
                    copy(
                        error = e.message ?: "메시지 전송에 실패했습니다",
                        isLoading = false
                    )
                }
                sendSideEffect(StorySideEffect.ShowError(e.message ?: "메시지 전송에 실패했습니다"))
            }
        }
    }

    private fun sendTranscriptionMessage(text: String) {
        if (text.isBlank()) return

        addConversationMessage(
            ConversationMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                isFromUser = true,
                inputType = MessageInputType.VOICE
            )
        )

        setState {
            copy(
                conversationId = currentConversationId,
                transcriptionResult = null,
                isReadyForStory = false,
                isLoading = true
            )
        }
    }

    private fun sendWhisperStreamingMessage(text: String) {
        if (text.isBlank()) return

        addConversationMessage(
            ConversationMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                isFromUser = false,
                inputType = MessageInputType.WHISPER_STREAMING
            )
        )

        setState {
            copy(
                conversationId = currentConversationId,
                isReadyForStory = false,
                isLoading = true
            )
        }

        viewModelScope.launch {
            try {
                storyRepository.sendMessage(text, currentConversationId)
            } catch (e: Exception) {
                setState {
                    copy(
                        error = e.message ?: "메시지 전송에 실패했습니다",
                        isLoading = false
                    )
                }
                sendSideEffect(StorySideEffect.ShowError(e.message ?: "메시지 전송에 실패했습니다"))
            }
        }
    }

    private fun generateStory(conversationId: String) {
        setState {
            copy(
                isLoading = true,
                currentStory = null
            )
        }

        viewModelScope.launch {
            try {
                storyRepository.generateStory(conversationId)
            } catch (e: Exception) {
                setState {
                    copy(
                        error = e.message ?: "스토리 생성에 실패했습니다",
                        isLoading = false
                    )
                }
                sendSideEffect(StorySideEffect.ShowError(e.message ?: "스토리 생성에 실패했습니다"))
            }
        }
    }

    private fun clearMessages() {
        currentConversationId = UUID.randomUUID().toString()
        setState {
            copy(
                conversations = emptyList(),
                currentStory = null,
                error = null,
                conversationId = currentConversationId,
                isReadyForStory = false
            )
        }
    }

    private fun addConversationMessage(message: ConversationMessage) {
        val currentMessages = currentState.conversations.toMutableList()
        currentMessages.add(message)
        setState {
            copy(
                conversations = currentMessages
            )
        }
    }

    private fun checkAudioPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setState {
            copy(
                hasAudioPermission = hasPermission
            )
        }
    }

    private fun requestAudioPermission() {
        viewModelScope.launch {
            sendSideEffect(StorySideEffect.RequestAudioPermission)
        }
    }
    
    // Public method to refresh permission status after permission dialog
    fun refreshAudioPermission() {
        checkAudioPermission()
    }

    private fun startRecording() {
        // Always check permission before starting recording
        checkAudioPermission()

        if (!currentState.hasAudioPermission) {
            viewModelScope.launch {
                sendSideEffect(StorySideEffect.RequestAudioPermission)
            }
            return
        }

        setState {
            copy(
                isRecording = true
            )
        }
        viewModelScope.launch {
            sendSideEffect(StorySideEffect.StartAudioRecording)
        }
    }

    private fun stopRecording() {
        setState {
            copy(
                isRecording = false,
                transcribingAudio = true,  // Start transcribing when recording stops
                isLoading = false  // Don't show general loading during transcription
            )
        }
        viewModelScope.launch {
            sendSideEffect(StorySideEffect.StopAudioRecording)
        }
    }

    private fun sendAudioMessage(audioData: ByteArray, conversationId: String) {
        // Set transcribing state - don't set isLoading to true during transcription
        setState {
            copy(
                transcribingAudio = true,
                isLoading = false  // Keep loading false during transcription
            )
        }

        viewModelScope.launch {
            try {
                storyRepository.sendAudioMessage(audioData, conversationId)
            } catch (e: Exception) {
                setState {
                    copy(
                        error = e.message ?: "음성 메시지 전송에 실패했습니다",
                        transcribingAudio = false,
                        isLoading = false,
                    )
                }
                sendSideEffect(StorySideEffect.ShowError(e.message ?: "음성 메시지 전송에 실패했습니다"))
            }
        }
    }

    // Audio streaming methods
    private fun startAudioStreaming() {
        // Always check permission before starting audio streaming
        checkAudioPermission()
        
        if (!currentState.hasAudioPermission) {
            requestAudioPermission()
            return
        }
        

        setState {
            copy(
                isAudioStreaming = true,
                streamingTranscriptionResult = null,
                streamingTranscriptionIsPartial = false,
                isRecording = true  // Set recording state for UI
            )
        }
        
        audioStreamingJob = viewModelScope.launch {
            audioStreamRecorder.startStreaming(currentConversationId).collect { state ->
                when (state) {
                    is AudioStreamState.StreamStarted -> {
                        // Send stream start message to server
                        storyRepository.sendAudioStreamStart(
                            conversationId = state.conversationId,
                            sampleRate = state.sampleRate,
                            format = state.format,
                            channels = state.channels
                        )
                    }
                    is AudioStreamState.ChunkReady -> {
                        // Send chunk to server
                        storyRepository.sendAudioStreamChunk(
                            conversationId = state.conversationId,
                            audioData = state.audioData,
                            sequenceNumber = state.sequenceNumber
                        )
                    }
                    is AudioStreamState.StreamEnded -> {
                        // Send stream end message to server
                        storyRepository.sendAudioStreamEnd(
                            conversationId = state.conversationId,
                            totalChunks = state.totalChunks
                        )

                        setState {
                            copy(
                                isAudioStreaming = false
                            )
                        }
                    }
                    is AudioStreamState.Amplitude -> {
                        // Update amplitude for UI feedback (could be used for visualization)
                    }
                    is AudioStreamState.Error -> {
                        setState {
                            copy(
                                isAudioStreaming = false
                            )
                        }
                        
                        viewModelScope.launch {
                            sendSideEffect(StorySideEffect.ShowError("스트리밍 오류: ${state.message}"))
                        }
                    }
                }
            }
        }
    }
    
    private fun stopAudioStreaming() {
        audioStreamingJob?.cancel()
        audioStreamRecorder.stopStreaming()
        setState {
            copy(
                isAudioStreaming = false,
                streamingTranscriptionResult = null,
                isRecording = false  // Reset recording state
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        
        // Clean up voice recognition services
        stopAudioStreaming()
        
        viewModelScope.launch {
            storyRepository.disconnect()
        }
    }
}