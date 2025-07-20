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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AudioRecorder(private val context: Context) {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingScope: CoroutineScope? = null
    
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(): Flow<RecordingState> = callbackFlow {
        if (!hasAudioPermission()) {
            trySend(RecordingState.Error("Audio permission not granted"))
            close()
            return@callbackFlow
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
        
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            trySend(RecordingState.Error("Unable to get buffer size"))
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
                trySend(RecordingState.Error("AudioRecord initialization failed"))
                close()
                return@callbackFlow
            }

            audioRecord?.startRecording()
            isRecording = true
            trySend(RecordingState.Recording)

            recordingScope = CoroutineScope(Dispatchers.IO)
            val audioData = ByteArrayOutputStream()
            
            recordingScope?.launch {
                val buffer = ByteArray(bufferSize)
                
                while (isRecording && currentCoroutineContext().isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        audioData.write(buffer, 0, bytesRead)
                        // Send amplitude for visualization
                        val amplitude = calculateAmplitude(buffer, bytesRead)
                        trySend(RecordingState.Amplitude(amplitude))
                    }
                }
                
                // Recording finished, convert to WAV and send final result
                val rawAudioData = audioData.toByteArray()
                val wavData = convertToWav(rawAudioData)
                trySend(RecordingState.Completed(wavData))
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during recording", e)
            trySend(RecordingState.Error("Audio permission denied"))
        } catch (e: Exception) {
            Log.e(TAG, "Exception during recording", e)
            trySend(RecordingState.Error("Recording failed: ${e.message}"))
        }

        awaitClose {
            stopRecording()
        }
    }

    fun stopRecording() {
        try {
            isRecording = false
            recordingScope?.cancel()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
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

    private fun convertToWav(audioData: ByteArray): ByteArray {
        val totalDataLen = audioData.size + 36
        val longSampleRate = SAMPLE_RATE.toLong()
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 16 / 8

        val header = ByteArray(44)
        
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        
        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // fmt subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // subchunk1size
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // audio format (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        
        // data subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (audioData.size and 0xff).toByte()
        header[41] = ((audioData.size shr 8) and 0xff).toByte()
        header[42] = ((audioData.size shr 16) and 0xff).toByte()
        header[43] = ((audioData.size shr 24) and 0xff).toByte()

        return header + audioData
    }
}

sealed class RecordingState {
    object Recording : RecordingState()
    data class Amplitude(val amplitude: Float) : RecordingState()
    data class Completed(val audioData: ByteArray) : RecordingState()
    data class Error(val message: String) : RecordingState()
}