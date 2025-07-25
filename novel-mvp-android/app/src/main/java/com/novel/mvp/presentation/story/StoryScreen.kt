package com.novel.mvp.presentation.story

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novel.mvp.base.BaseComposeScreen
import com.novel.mvp.data.model.WebSocketMessage
import com.novel.mvp.data.websocket.ConnectionState
import com.novel.mvp.presentation.story.components.VoiceInputButton
import com.novel.mvp.utils.AudioPlayer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    viewModel: StoryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val audioPlayer = remember { AudioPlayer(context) }
    val listState = rememberLazyListState()
    
    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - only update permission status, don't start recording automatically
            viewModel.processIntent(StoryIntent.CheckAudioPermission)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("음성 권한이 허용되었습니다. 이제 음성 버튼을 사용할 수 있습니다.")
            }
        } else {
            // Permission denied
            viewModel.processIntent(StoryIntent.CheckAudioPermission)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("음성 입력을 위해 오디오 권한이 필요합니다.")
            }
        }
    }
    
    // Observe lifecycle events to refresh permissions when returning to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.processIntent(StoryIntent.CheckAudioPermission)
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Cleanup audio player when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.destroy()
        }
    }
    
    BaseComposeScreen<StoryIntent, StoryViewState, StorySideEffect, StoryViewModel>(
        viewModel = viewModel,
        onSideEffect = { sideEffect ->
            when (sideEffect) {
                is StorySideEffect.ShowError -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(sideEffect.message)
                    }
                }
                is StorySideEffect.ScrollToBottom -> {
                    coroutineScope.launch {
                        if (listState.layoutInfo.totalItemsCount > 0) {
                            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        }
                    }
                }
                is StorySideEffect.PlayAudio -> {
                    coroutineScope.launch {
                        try {
                            val result = audioPlayer.playAudioFromBase64(sideEffect.audioData, sideEffect.format)
                            when (result) {
                                is com.novel.mvp.utils.PlaybackResult.Success -> {
                                    snackbarHostState.showSnackbar("대화 오디오 재생 시작 (${sideEffect.format})")
                                }
                                is com.novel.mvp.utils.PlaybackResult.Error -> {
                                    snackbarHostState.showSnackbar("오디오 재생 실패: ${result.message}")
                                }
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("오디오 재생 오류: ${e.message}")
                        }
                    }
                }
                is StorySideEffect.PlayStoryAudio -> {
                    coroutineScope.launch {
                        try {
                            val result = audioPlayer.playAudioFromBase64(sideEffect.audioData, sideEffect.format)
                            when (result) {
                                is com.novel.mvp.utils.PlaybackResult.Success -> {
                                    snackbarHostState.showSnackbar("스토리 내레이션 재생 시작 (${sideEffect.format})")
                                }
                                is com.novel.mvp.utils.PlaybackResult.Error -> {
                                    snackbarHostState.showSnackbar("스토리 오디오 재생 실패: ${result.message}")
                                }
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("스토리 오디오 재생 오류: ${e.message}")
                        }
                    }
                }
                is StorySideEffect.RequestAudioPermission -> {
                    // Always launch permission request when requested
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                is StorySideEffect.StartAudioRecording -> {
                    // Audio recording is handled by VoiceInputButton component
                }
                is StorySideEffect.StopAudioRecording -> {
                    // Audio recording stop is handled by VoiceInputButton component
                }
            }
        }
    ) { viewState, onIntent ->
        StoryScreenContent(
            viewState = viewState,
            onIntent = onIntent,
            onNavigateBack = onNavigateBack,
            snackbarHostState = snackbarHostState,
            listState = listState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryScreenContent(
    viewState: StoryViewState,
    onIntent: (StoryIntent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("스토리 생성")
                        ConnectionStatusIndicator(connectionState = viewState.connectionState)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(StoryIntent.ClearMessages) }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로운 대화")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Voice Transcription Status Display
            AnimatedVisibility(
                visible = viewState.transcribingAudio,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFD1FAE5) // emerald-100
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewState.transcribingAudio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF059669) // emerald-600
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "음성을 인식하고 있습니다...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF065F46) // emerald-800
                            )
                        }
                    }
                }
            }

            // Story Display Section with adaptive layout
            AnimatedVisibility(
                visible = viewState.currentStory != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                viewState.currentStory?.let { story ->
                    StoryDisplayCard(
                        story = story,
                        windowAdaptiveInfo = windowAdaptiveInfo,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Conversation Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (viewState.conversations.isEmpty()) {
                    EmptyConversationState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(viewState.conversations) { message ->
                            ConversationMessageItem(
                                message = message,
                                onSuggestedQuestionClick = { question ->
                                    onIntent(StoryIntent.SendMessage(question))
                                }
                            )
                        }
                    }
                }

                // Loading indicator with improved accessibility - only show when actually waiting for response
                if (viewState.isLoading && !viewState.transcribingAudio) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (viewState.transcribingAudio) {
                                    "음성을 인식하고 있습니다..."
                                } else {
                                    "응답을 생성하고 있습니다..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Generate Story Button
            AnimatedVisibility(
                visible = viewState.isReadyForStory && viewState.currentStory == null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "대화가 충분히 진행되었습니다!",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "지금까지의 대화를 바탕으로 특별한 소설을 생성해보세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onIntent(StoryIntent.GenerateStory(viewState.conversationId))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF10B981), // emerald-500
                                                Color(0xFF14B8A6)  // teal-500
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "소설 생성하기",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }


            // Message Input Section
            MessageInputSection(
                enabled = viewState.connectionState == ConnectionState.AUTHENTICATED && !viewState.isLoading,
                isRecording = viewState.isRecording,
                hasAudioPermission = viewState.hasAudioPermission,
                onSendMessage = { message ->
                    onIntent(StoryIntent.SendMessage(message))
                },
                onStartRecording = {
                    onIntent(StoryIntent.StartRecording)
                },
                onStopRecording = {
                    onIntent(StoryIntent.StopRecording)
                },
                onAudioRecorded = { audioData ->
                    onIntent(StoryIntent.SendAudioMessage(audioData, viewState.conversationId))
                },
                onRequestAudioPermission = {
                    onIntent(StoryIntent.RequestAudioPermission)
                }
            )
        }
    }
}


@Composable
private fun ConnectionStatusIndicator(
    connectionState: ConnectionState
) {
    val (color, text) = when (connectionState) {
        ConnectionState.DISCONNECTED -> Color.Red to "연결 끊김"
        ConnectionState.CONNECTING -> Color.Yellow to "연결 중..."
        ConnectionState.CONNECTED -> Color.Blue to "연결됨"
        ConnectionState.AUTHENTICATED -> Color.Green to "인증됨"
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StoryDisplayCard(
    story: WebSocketMessage.StoryOutput,
    windowAdaptiveInfo: WindowAdaptiveInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    story.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                AssistChip(
                    onClick = { },
                    label = { Text(story.genre) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                AssistChip(
                    onClick = { },
                    label = { Text(story.emotion) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                story.content,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp
            )
            
            if (story.emotionalArc.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "감정의 흐름: ${story.emotionalArc}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "대화를 시작해보세요",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "AI와 대화를 나누면서 특별한 소설을 만들어보세요.\n당신의 이야기가 놀라운 작품으로 탄생합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConversationMessageItem(
    message: ConversationMessage,
    onSuggestedQuestionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            // AI Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF34D399), // emerald-400
                                Color(0xFF14B8A6)  // teal-500
                            )
                        ),
                        shape = CircleShape
                    )
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Column(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isFromUser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isFromUser) 4.dp else 16.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Voice input indicator
                    if (message.inputType in listOf(MessageInputType.VOICE, MessageInputType.WHISPER_STREAMING)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (message.inputType) {
                                    MessageInputType.WHISPER_STREAMING -> Icons.Default.HighQuality
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (message.isFromUser) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (message.inputType) {
                                    MessageInputType.WHISPER_STREAMING -> "음성 입력"
                                    else -> "음성 입력"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.isFromUser) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isFromUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    message.emotion?.let { emotion ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "감정: $emotion",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message.isFromUser) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
            
            // Suggested Questions
            if (message.suggestedQuestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "추천 질문:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                message.suggestedQuestions.forEach { question ->
                    SuggestionChip(
                        onClick = { onSuggestedQuestionClick(question) },
                        label = {
                            Text(
                                question,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
        
        if (message.isFromUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // User Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Color(0xFF64748B), // slate-600
                        CircleShape
                    )
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "사용자",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInputSection(
    enabled: Boolean,
    isRecording: Boolean,
    hasAudioPermission: Boolean,
    onSendMessage: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAudioRecorded: (ByteArray) -> Unit,
    onRequestAudioPermission: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지를 입력하세요...") },
                enabled = enabled && !isRecording,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Voice Input Button
            VoiceInputButton(
                modifier = Modifier.padding(end = 4.dp),
                isRecording = isRecording,
                hasPermission = hasAudioPermission,
                enabled = enabled,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onAudioRecorded = onAudioRecorded,
                onRequestPermission = onRequestAudioPermission
            )
            
            
            FloatingActionButton(
                onClick = {
                    if (message.isNotBlank()) {
                        onSendMessage(message)
                        message = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .shadow(8.dp, CircleShape),
                containerColor = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF10B981), // emerald-500
                                    Color(0xFF14B8A6)  // teal-500
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
