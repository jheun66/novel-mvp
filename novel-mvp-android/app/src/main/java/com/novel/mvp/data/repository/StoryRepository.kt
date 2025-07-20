package com.novel.mvp.data.repository

import com.novel.mvp.data.model.WebSocketMessage
import com.novel.mvp.data.websocket.ConnectionState
import com.novel.mvp.data.websocket.StoryWebSocketService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class StoryRepository(
    private val webSocketService: StoryWebSocketService
) {
    
    val connectionState: StateFlow<ConnectionState> = webSocketService.connectionState
    
    val textMessages: Flow<WebSocketMessage.TextOutput> = webSocketService.messages
        .filter { it is WebSocketMessage.TextOutput }
        .map { it as WebSocketMessage.TextOutput }
    
    val storyMessages: Flow<WebSocketMessage.StoryOutput> = webSocketService.messages
        .filter { it is WebSocketMessage.StoryOutput }
        .map { it as WebSocketMessage.StoryOutput }
    
    val audioMessages: Flow<WebSocketMessage.AudioOutput> = webSocketService.messages
        .filter { it is WebSocketMessage.AudioOutput }
        .map { it as WebSocketMessage.AudioOutput }
    
    val errorMessages: Flow<WebSocketMessage.Error> = webSocketService.messages
        .filter { it is WebSocketMessage.Error }
        .map { it as WebSocketMessage.Error }
    
    val authMessages: Flow<WebSocketMessage.AuthResponse> = webSocketService.messages
        .filter { it is WebSocketMessage.AuthResponse }
        .map { it as WebSocketMessage.AuthResponse }
    
    suspend fun connect(): Boolean {
        return webSocketService.connect()
    }
    
    suspend fun sendMessage(text: String, conversationId: String) {
        webSocketService.sendTextMessage(text, conversationId)
    }
    
    suspend fun sendAudioMessage(audioData: ByteArray, conversationId: String) {
        webSocketService.sendAudioMessage(audioData, conversationId)
    }
    
    suspend fun sendAudioEchoTest(audioData: ByteArray, conversationId: String) {
        webSocketService.sendAudioEchoTest(audioData, conversationId)
    }
    
    suspend fun generateStory(conversationId: String) {
        webSocketService.generateStory(conversationId)
    }
    
    suspend fun disconnect() {
        webSocketService.disconnect()
    }
}