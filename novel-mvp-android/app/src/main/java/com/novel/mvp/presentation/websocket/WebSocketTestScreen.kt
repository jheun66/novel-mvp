package com.novel.mvp.presentation.websocket

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novel.mvp.data.websocket.ConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSocketTestScreen(
    viewModel: WebSocketTestViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var inputText by remember { mutableStateOf("") }
    
    // Handle side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { sideEffect ->
            when (sideEffect) {
                is WebSocketTestSideEffect.ShowError -> {
                    Toast.makeText(context, sideEffect.message, Toast.LENGTH_SHORT).show()
                }
                is WebSocketTestSideEffect.ShowSuccess -> {
                    Toast.makeText(context, sideEffect.message, Toast.LENGTH_SHORT).show()
                }
                is WebSocketTestSideEffect.ScrollToBottom -> {
                    coroutineScope.launch {
                        if (state.conversations.isNotEmpty()) {
                            listState.animateScrollToItem(state.conversations.size - 1)
                        }
                    }
                }
                is WebSocketTestSideEffect.ClearInput -> {
                    inputText = ""
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with connection status
        ConnectionStatusHeader(
            connectionState = state.connectionState,
            isLoading = state.isLoading,
            onConnect = { viewModel.processIntent(WebSocketTestIntent.Connect) },
            onDisconnect = { viewModel.processIntent(WebSocketTestIntent.Disconnect) },
            onClear = { viewModel.processIntent(WebSocketTestIntent.ClearMessages) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.conversations) { message ->
                MessageItem(message = message)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Input section
        InputSection(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSendMessage = { 
                viewModel.processIntent(WebSocketTestIntent.SendMessage(inputText))
            },
            onGenerateStory = {
                viewModel.processIntent(WebSocketTestIntent.GenerateStory(state.conversationId))
            },
            isConnected = state.connectionState == ConnectionState.AUTHENTICATED,
            isReadyForStory = state.isReadyForStory,
            isLoading = state.isLoading
        )
    }
}

@Composable
private fun ConnectionStatusHeader(
    connectionState: ConnectionState,
    isLoading: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "WebSocket 테스트",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when (connectionState) {
                                    ConnectionState.DISCONNECTED -> Color.Red
                                    ConnectionState.CONNECTING -> Color.Yellow
                                    ConnectionState.CONNECTED -> Color.Blue
                                    ConnectionState.AUTHENTICATED -> Color.Green
                                },
                                shape = RoundedCornerShape(50)
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = when (connectionState) {
                            ConnectionState.DISCONNECTED -> "연결 안됨"
                            ConnectionState.CONNECTING -> "연결 중..."
                            ConnectionState.CONNECTED -> "연결됨 (인증 중)"
                            ConnectionState.AUTHENTICATED -> "연결됨 (인증 완료)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnect,
                    enabled = connectionState == ConnectionState.DISCONNECTED && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("연결")
                }
                
                Button(
                    onClick = onDisconnect,
                    enabled = connectionState != ConnectionState.DISCONNECTED,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("연결 해제")
                }
                
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("초기화")
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: TestConversationMessage) {
    val backgroundColor = when {
        message.isFromUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        message.messageType == "system" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
        message.messageType == "error" -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        message.messageType == "story" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(if (message.isFromUser) 0.8f else 1f),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Message type indicator
                if (message.messageType != "text") {
                    Text(
                        text = when (message.messageType) {
                            "system" -> "💻 SYSTEM"
                            "error" -> "❌ ERROR"
                            "story" -> "📖 STORY"
                            else -> message.messageType.uppercase()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Emotion and suggested questions
                if (message.emotion != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "감정: ${message.emotion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                if (message.suggestedQuestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "제안 질문:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    message.suggestedQuestions.forEach { question ->
                        Text(
                            text = "• $question",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputSection(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onGenerateStory: () -> Unit,
    isConnected: Boolean,
    isReadyForStory: Boolean,
    isLoading: Boolean
) {
    Column {
        // Story generation button
        if (isReadyForStory) {
            Button(
                onClick = onGenerateStory,
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("✨ 스토리 생성하기")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Message input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { 
                    Text(
                        if (isConnected) "메시지를 입력하세요..." 
                        else "먼저 WebSocket에 연결하세요"
                    ) 
                },
                enabled = isConnected && !isLoading,
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(56.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "메시지 전송"
                )
            }
        }
        
        if (!isConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "WebSocket 연결이 필요합니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}