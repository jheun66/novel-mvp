package com.novel.mvp.presentation.story.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novel.mvp.utils.AudioRecorder
import com.novel.mvp.utils.RecordingState
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VoiceInputButton(
    isRecording: Boolean,
    hasPermission: Boolean,
    enabled: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRequestPermission: () -> Unit,
    onAudioRecorded: (ByteArray) -> Unit,
    onAudioEchoTest: ((ByteArray) -> Unit)? = null, // Optional echo test callback
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioRecorder = remember { AudioRecorder(context) }
    
    var currentAmplitude by remember { mutableFloatStateOf(0f) }
    
    // Animation for recording state
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    val color by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            isRecording -> Color.Red
            else -> MaterialTheme.colorScheme.primary
        },
        label = "button_color"
    )

    // Recording visualization
    LaunchedEffect(isRecording) {
        if (isRecording) {
            audioRecorder.startRecording().collect { state ->
                when (state) {
                    is RecordingState.Recording -> {
                        // Recording started
                        currentAmplitude = 0f
                    }
                    is RecordingState.Amplitude -> {
                        currentAmplitude = if (state.amplitude.isNaN() || state.amplitude.isInfinite()) {
                            0f
                        } else {
                            state.amplitude.coerceIn(0f, 1f)
                        }
                    }
                    is RecordingState.Completed -> {
                        currentAmplitude = 0f
                        if (onAudioEchoTest != null) {
                            // If echo test callback is provided, use it instead
                            onAudioEchoTest(state.audioData)
                        } else {
                            onAudioRecorded(state.audioData)
                        }
                        onStopRecording()
                    }
                    is RecordingState.Error -> {
                        // Handle error
                        currentAmplitude = 0f
                        onStopRecording()
                    }
                }
            }
        } else {
            // Reset amplitude when not recording
            currentAmplitude = 0f
        }
    }

    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        // Amplitude visualization rings
        if (isRecording) {
            repeat(3) { index ->
                val validAmplitude = if (currentAmplitude.isNaN() || currentAmplitude.isInfinite()) 0f else currentAmplitude.coerceIn(0f, 1f)
                val animatedScale by animateFloatAsState(
                    targetValue = 1f + (validAmplitude * (index + 1) * 0.3f),
                    animationSpec = tween(100),
                    label = "amplitude_ring_$index"
                )
                
                Box(
                    modifier = Modifier
                        .size(56.dp + (index * 8).dp)
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .background(
                            color.copy(alpha = 0.2f - (index * 0.05f))
                        )
                )
            }
        }
        
        FloatingActionButton(
            onClick = {
                if (!hasPermission) {
                    onRequestPermission()
                } else if (isRecording) {
                    audioRecorder.stopRecording()
                    onStopRecording()
                } else {
                    onStartRecording()
                }
            },
            modifier = Modifier.scale(scale),
            containerColor = color,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            AnimatedContent(
                targetState = isRecording,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "icon_transition"
            ) { recording ->
                Icon(
                    imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (recording) "녹음 중지" else "음성 입력"
                )
            }
        }
    }
}

@Composable
fun TranscriptionDisplay(
    transcription: String?,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = transcription != null || isTranscribing,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTranscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "음성을 텍스트로 변환 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = transcription ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}