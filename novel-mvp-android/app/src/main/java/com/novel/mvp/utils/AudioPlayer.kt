package com.novel.mvp.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AudioPlayer(private val context: Context) {
    companion object {
        private const val TAG = "AudioPlayer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackScope: CoroutineScope? = null

    /**
     * Play WAV audio from Base64 string
     */
    suspend fun playAudioFromBase64(base64Audio: String): PlaybackResult {
        return try {
            val audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
            Log.d(TAG, "Decoded audio: ${audioBytes.size} bytes")
            
            playWavAudio(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode and play Base64 audio", e)
            PlaybackResult.Error("Failed to decode audio: ${e.message}")
        }
    }

    /**
     * Play WAV audio from byte array
     */
    private suspend fun playWavAudio(wavBytes: ByteArray): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                // Extract PCM data from WAV file
                val pcmData = extractPcmFromWav(wavBytes)
                if (pcmData.isEmpty()) {
                    return@withContext PlaybackResult.Error("No audio data found in WAV file")
                }

                Log.d(TAG, "Extracted PCM data: ${pcmData.size} bytes")
                
                // Play the PCM data
                playPcmAudio(pcmData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play WAV audio", e)
                PlaybackResult.Error("Playback failed: ${e.message}")
            }
        }
    }

    /**
     * Extract PCM data from WAV byte array
     */
    private fun extractPcmFromWav(wavBytes: ByteArray): ByteArray {
        return try {
            // Simple WAV parser - skip 44-byte header and get PCM data
            if (wavBytes.size < 44) {
                Log.e(TAG, "WAV file too small: ${wavBytes.size} bytes")
                return ByteArray(0)
            }
            
            // Check for RIFF header
            val riffHeader = String(wavBytes.sliceArray(0..3))
            if (riffHeader != "RIFF") {
                Log.e(TAG, "Invalid WAV file: missing RIFF header")
                return ByteArray(0)
            }
            
            // Check for WAVE format
            val waveHeader = String(wavBytes.sliceArray(8..11))
            if (waveHeader != "WAVE") {
                Log.e(TAG, "Invalid WAV file: missing WAVE header")
                return ByteArray(0)
            }
            
            // For simplicity, assume standard 44-byte header
            val pcmData = wavBytes.sliceArray(44 until wavBytes.size)
            Log.d(TAG, "WAV header validated, PCM data: ${pcmData.size} bytes")
            
            pcmData
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract PCM from WAV", e)
            ByteArray(0)
        }
    }

    /**
     * Play PCM audio data
     */
    private suspend fun playPcmAudio(pcmData: ByteArray): PlaybackResult {
        return withContext(Dispatchers.Main) {
            try {
                stopPlayback() // Stop any existing playback
                
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )
                
                if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
                    return@withContext PlaybackResult.Error("Failed to get audio buffer size")
                }

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.let { track ->
                    track.play()
                    isPlaying = true
                    
                    // Play audio in background
                    playbackScope = CoroutineScope(Dispatchers.IO)
                    playbackScope?.launch {
                        try {
                            var offset = 0
                            val chunkSize = bufferSize / 4 // Smaller chunks for smoother playback
                            
                            while (offset < pcmData.size && isPlaying) {
                                val bytesToWrite = minOf(chunkSize, pcmData.size - offset)
                                val bytesWritten = track.write(
                                    pcmData, 
                                    offset, 
                                    bytesToWrite
                                )
                                
                                if (bytesWritten > 0) {
                                    offset += bytesWritten
                                } else {
                                    Log.w(TAG, "AudioTrack write returned: $bytesWritten")
                                    break
                                }
                                
                                // Small delay to prevent overwhelming the audio system
                                delay(10)
                            }
                            
                            // Wait for playback to finish
                            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && isPlaying) {
                                delay(50)
                            }
                            
                        } finally {
                            withContext(Dispatchers.Main) {
                                stopPlayback()
                            }
                        }
                    }
                }

                PlaybackResult.Success("Audio playback started")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play PCM audio", e)
                stopPlayback()
                PlaybackResult.Error("Playback failed: ${e.message}")
            }
        }
    }

    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        try {
            isPlaying = false
            playbackScope?.cancel()
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                    track.release()
                }
            }
            audioTrack = null
            Log.d(TAG, "Audio playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    /**
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean = isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    /**
     * Get playback duration estimate
     */
    fun estimatePlaybackDuration(base64Audio: String): Float {
        return try {
            val audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
            val pcmData = extractPcmFromWav(audioBytes)
            
            // Calculate duration: samples / sample_rate
            // PCM 16-bit = 2 bytes per sample
            val samples = pcmData.size / 2
            samples.toFloat() / SAMPLE_RATE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to estimate duration", e)
            0f
        }
    }
}

sealed class PlaybackResult {
    data class Success(val message: String) : PlaybackResult()
    data class Error(val message: String) : PlaybackResult()
}