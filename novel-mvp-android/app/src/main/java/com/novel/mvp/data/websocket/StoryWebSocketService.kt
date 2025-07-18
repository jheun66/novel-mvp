package com.novel.mvp.data.websocket

import android.util.Log
import com.novel.mvp.data.local.TokenStorage
import com.novel.mvp.data.model.WebSocketMessage
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import java.util.*

class StoryWebSocketService(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage
) {
    companion object {
        private const val TAG = "StoryWebSocketService"
        private const val WS_URL = "ws://10.0.2.2:8080/ws/novel"
    }
    
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _messages = MutableSharedFlow<WebSocketMessage>()
    val messages: SharedFlow<WebSocketMessage> = _messages.asSharedFlow()
    
    suspend fun connect(): Boolean {
        return try {
            _connectionState.value = ConnectionState.CONNECTING
            
            val accessToken = tokenStorage.getAccessToken().first()
            if (accessToken == null) {
                Log.e(TAG, "No access token available for WebSocket connection")
                _connectionState.value = ConnectionState.DISCONNECTED
                return false
            }
            
            // Include token in WebSocket URL for authentication during handshake
            val wsUrlWithToken = "$WS_URL?token=$accessToken"
            webSocketSession = httpClient.webSocketSession(wsUrlWithToken)
            _connectionState.value = ConnectionState.AUTHENTICATED
            
            // Start listening for messages
            serviceScope.launch {
                startListening()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to WebSocket", e)
            _connectionState.value = ConnectionState.DISCONNECTED
            false
        }
    }
    
    
    private suspend fun startListening() {
        webSocketSession?.let { session ->
            try {
                for (frame in session.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            try {
                                val text = frame.readText()
                                val message = json.decodeFromString<WebSocketMessage>(text)
                                _messages.emit(message)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse WebSocket message", e)
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebSocket connection error", e)
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }
    
    suspend fun sendMessage(message: WebSocketMessage) {
        try {
            val jsonMessage = json.encodeToString(WebSocketMessage.serializer(), message)
            webSocketSession?.send(Frame.Text(jsonMessage))
            Log.d(TAG, "Sent WebSocket message: ${message::class.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WebSocket message", e)
        }
    }
    
    suspend fun sendTextMessage(text: String, conversationId: String = UUID.randomUUID().toString()) {
        val message = WebSocketMessage.TextInput(text, conversationId)
        sendMessage(message)
    }
    
    suspend fun generateStory(conversationId: String) {
        val message = WebSocketMessage.GenerateStory(conversationId)
        sendMessage(message)
    }
    
    suspend fun disconnect() {
        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
            webSocketSession = null
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.d(TAG, "WebSocket disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error during WebSocket disconnect", e)
        }
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED
}