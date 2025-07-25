package com.novel.mvp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

class AudioStreamRecorder(private val context: Context) {
    companion object {
        private const val TAG = "AudioStreamRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val CHUNK_SIZE = 1024 // Size of each chunk in bytes (about 32ms of audio at 16kHz)
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingScope: CoroutineScope? = null
    private val sequenceCounter = AtomicInteger(0)
    
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startStreaming(conversationId: String): Flow<AudioStreamState> = callbackFlow {
        if (!hasAudioPermission()) {
            trySend(AudioStreamState.Error("Audio permission not granted"))
            close()
            return@callbackFlow
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
        
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            trySend(AudioStreamState.Error("Unable to get buffer size"))
            close()
            return@callbackFlow
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                trySend(AudioStreamState.Error("AudioRecord initialization failed"))
                close()
                return@callbackFlow
            }

            // Send stream start event
            trySend(AudioStreamState.StreamStarted(conversationId, SAMPLE_RATE, "pcm16", 1))

            audioRecord?.startRecording()
            isRecording = true
            sequenceCounter.set(0)

            recordingScope = CoroutineScope(Dispatchers.IO)
            
            recordingScope?.launch {
                val buffer = ByteArray(CHUNK_SIZE)
                var totalChunks = 0
                
                while (isRecording && currentCoroutineContext().isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        // Create a copy of the actual data read
                        val chunkData = buffer.copyOf(bytesRead)
                        val sequenceNumber = sequenceCounter.getAndIncrement()
                        totalChunks++
                        
                        // Send chunk
                        trySend(AudioStreamState.ChunkReady(
                            conversationId = conversationId,
                            audioData = chunkData,
                            sequenceNumber = sequenceNumber
                        ))
                        
                        // Send amplitude for visualization
                        val amplitude = calculateAmplitude(chunkData, bytesRead)
                        trySend(AudioStreamState.Amplitude(amplitude))
                        
                        // Small delay to prevent overwhelming the system
                        delay(10)
                    }
                }
                
                // Send stream end event
                trySend(AudioStreamState.StreamEnded(conversationId, totalChunks))
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during recording", e)
            trySend(AudioStreamState.Error("Audio permission denied"))
        } catch (e: Exception) {
            Log.e(TAG, "Exception during recording", e)
            trySend(AudioStreamState.Error("Recording failed: ${e.message}"))
        }

        awaitClose {
            stopStreaming()
        }
    }

    fun stopStreaming() {
        try {
            isRecording = false
            recordingScope?.cancel()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Audio streaming stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio streaming", e)
        }
    }

    private fun calculateAmplitude(buffer: ByteArray, bytesRead: Int): Float {
        if (bytesRead <= 0) return 0f
        
        var sum = 0.0
        var sampleCount = 0
        
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                val sample = (buffer[i].toInt() and 0xFF) or ((buffer[i + 1].toInt() and 0xFF) shl 8)
                sum += (sample * sample).toDouble()
                sampleCount++
            }
        }
        
        if (sampleCount == 0) return 0f
        
        val rms = kotlin.math.sqrt(sum / sampleCount)
        val normalized = (rms / 32768.0).toFloat()
        
        return if (normalized.isNaN() || normalized.isInfinite()) {
            0f
        } else {
            normalized.coerceIn(0f, 1f)
        }
    }
}

sealed class AudioStreamState {
    data class StreamStarted(
        val conversationId: String,
        val sampleRate: Int,
        val format: String,
        val channels: Int
    ) : AudioStreamState()
    
    data class ChunkReady(
        val conversationId: String,
        val audioData: ByteArray,
        val sequenceNumber: Int
    ) : AudioStreamState()
    
    data class StreamEnded(
        val conversationId: String,
        val totalChunks: Int
    ) : AudioStreamState()
    
    data class Amplitude(val amplitude: Float) : AudioStreamState()
    data class Error(val message: String) : AudioStreamState()
}