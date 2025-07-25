package com.novel.mvp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Enhanced AudioPlayer with improved stability and features
 */
class AudioPlayer(private val context: Context) {
    companion object {
        private const val TAG = "AudioPlayer"
        private const val MEMORY_THRESHOLD = 0.8 // Use max 80% of available memory
        private const val DEFAULT_BITRATE = 128000 // 128 kbps for estimation
    }

    private var exoPlayer: ExoPlayer? = null
    private val playbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentPlaybackJob: Job? = null
    private val tempFiles = mutableListOf<File>()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackProgress = MutableStateFlow(PlaybackProgress(0L, 0L))
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateString = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            Log.d(TAG, "ExoPlayer state changed: $stateString")

            when (playbackState) {
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Player is idle")
                    _playbackState.value = PlaybackState.IDLE
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Player is buffering...")
                    _playbackState.value = PlaybackState.BUFFERING
                }
                Player.STATE_READY -> {
                    val duration = exoPlayer?.duration ?: 0
                    Log.d(TAG, "Player is ready - Duration: ${duration}ms (${duration/1000f}s)")
                    if (exoPlayer?.isPlaying == true) {
                        _playbackState.value = PlaybackState.PLAYING
                    } else {
                        _playbackState.value = PlaybackState.READY
                    }
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Playback ended")
                    _playbackState.value = PlaybackState.ENDED
                    releasePlayer()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "ExoPlayer error occurred!")
            Log.e(TAG, "Error type: ${error.errorCode}")
            Log.e(TAG, "Error message: ${error.message}")
            Log.e(TAG, "Cause: ${error.cause?.message}")
            error.printStackTrace()

            _playbackState.value = PlaybackState.ERROR(error.message ?: "Unknown playback error")
            releasePlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Playing state changed: $isPlaying")
            if (isPlaying) {
                _playbackState.value = PlaybackState.PLAYING
                startProgressUpdates()
            } else if (exoPlayer?.playbackState == Player.STATE_READY) {
                _playbackState.value = PlaybackState.PAUSED
                stopProgressUpdates()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val reasonString = when (reason) {
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
                Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
                Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
                Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
                else -> "UNKNOWN($reason)"
            }
            Log.d(TAG, "Position discontinuity: ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms (reason: $reasonString)")
        }
    }

    private var progressUpdateJob: Job? = null

    /**
     * Play audio from Base64 string with automatic format detection
     */
    suspend fun playAudioFromBase64(base64Audio: String, format: String = "auto"): PlaybackResult {
        // Cancel any ongoing playback
        currentPlaybackJob?.cancel()

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting playback - Base64 length: ${base64Audio.length}, format: $format")

                // Check memory before decoding
                val estimatedSize = (base64Audio.length * 0.75).toLong()
                if (!checkMemoryAvailable(estimatedSize)) {
                    return@withContext PlaybackResult.Error("Audio file too large for available memory")
                }

                val audioBytes = try {
                    android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Out of memory while decoding audio", e)
                    return@withContext PlaybackResult.Error("Audio file too large")
                }

                Log.d(TAG, "Decoded audio: ${audioBytes.size} bytes")

                // Log audio header for debugging
                if (audioBytes.size >= 16) {
                    val headerHex = audioBytes.take(16).joinToString(" ") { "%02x".format(it) }
                    Log.d(TAG, "Audio header: $headerHex")
                }

                val detectedFormat = if (format == "auto") detectAudioFormat(audioBytes) else format
                Log.d(TAG, "Using audio format: $detectedFormat")

                currentPlaybackJob = playbackScope.launch {
                    playAudioFromBytes(audioBytes, detectedFormat)
                }

                PlaybackResult.Success("Audio playback started")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode and play Base64 audio", e)
                PlaybackResult.Error("Failed to decode audio: ${e.message}")
            }
        }
    }

    /**
     * Play audio from byte array
     */
    private suspend fun playAudioFromBytes(audioBytes: ByteArray, format: String) {
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Starting playback for ${audioBytes.size} bytes of $format audio")

                stopPlayback()
                Log.d(TAG, "Previous playback stopped")

                initializePlayer()
                Log.d(TAG, "Player initialized")

                val mediaSource = createMediaSourceFromBytes(audioBytes, format)
                Log.d(TAG, "MediaSource created, setting to player")

                exoPlayer?.let { player ->
                    player.setMediaSource(mediaSource)
                    Log.d(TAG, "MediaSource set, preparing player...")

                    player.prepare()
                    Log.d(TAG, "Player prepared, playWhenReady will start playback automatically")

                    _playbackState.value = PlaybackState.BUFFERING

                    // Log initial player info
                    Log.d(TAG, "Player info - isPlaying: ${player.isPlaying}, playWhenReady: ${player.playWhenReady}")
                    Log.d(TAG, "Audio session ID: ${player.audioSessionId}")
                    Log.d(TAG, "Initial playback state: ${player.playbackState}")
                } ?: run {
                    Log.e(TAG, "ExoPlayer is null after initialization")
                    _playbackState.value = PlaybackState.ERROR("Failed to initialize ExoPlayer")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to play audio", e)
                e.printStackTrace()
                releasePlayer()
                _playbackState.value = PlaybackState.ERROR("Playback failed: ${e.message}")
            }
        }
    }

    /**
     * Create MediaSource from byte array using ReusableByteArrayDataSource
     */
    private fun createMediaSourceFromBytes(audioBytes: ByteArray, format: String): MediaSource {
        Log.d(TAG, "Creating MediaSource for ${audioBytes.size} bytes of $format audio")

        try {
            // Create reusable ByteArrayDataSource factory
            val byteArrayDataSourceFactory = object : DataSource.Factory {
                override fun createDataSource(): DataSource {
                    Log.d(TAG, "Creating ReusableByteArrayDataSource with ${audioBytes.size} bytes")
                    return ReusableByteArrayDataSource(audioBytes)
                }
            }

            // Use a simple, valid URI - the actual content comes from ByteArrayDataSource
            val uri = Uri.parse("asset://audio_data.$format")
            Log.d(TAG, "Using URI: $uri (content provided by ReusableByteArrayDataSource)")

            // Set MIME type based on format for better compatibility
            val mimeType = when (format.lowercase()) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "m4a", "aac" -> "audio/aac"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "audio/*"
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeType)
                .build()

            Log.d(TAG, "Created MediaItem - URI: ${mediaItem.localConfiguration?.uri}, MIME: $mimeType")

            val mediaSource = ProgressiveMediaSource.Factory(byteArrayDataSourceFactory)
                .createMediaSource(mediaItem)

            Log.d(TAG, "MediaSource created successfully with ReusableByteArrayDataSource")
            return mediaSource

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaSource from ByteArray", e)
            throw e
        }
    }

    /**
     * Alternative method using temporary file for better compatibility
     */
    suspend fun playAudioFromFile(audioBytes: ByteArray, format: String): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = createTempAudioFile(audioBytes, format)
                withContext(Dispatchers.Main) {
                    val mediaItem = MediaItem.fromUri(tempFile.toURI().toString())

                    stopPlayback()
                    initializePlayer()

                    exoPlayer?.let { player ->
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        _playbackState.value = PlaybackState.BUFFERING
                    }
                }
                PlaybackResult.Success("Audio playback started from file")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play audio from file", e)
                PlaybackResult.Error("Failed to play audio: ${e.message}")
            }
        }
    }

    /**
     * Create temporary file for audio data
     */
    private suspend fun createTempAudioFile(audioBytes: ByteArray, format: String): File {
        return withContext(Dispatchers.IO) {
            val tempFile = File.createTempFile("temp_audio", ".$format", context.cacheDir)
            tempFiles.add(tempFile)

            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
            }
            tempFile
        }
    }

    /**
     * Detect audio format from byte array header
     */
    private fun detectAudioFormat(audioBytes: ByteArray): String {
        if (audioBytes.isEmpty()) {
            Log.w(TAG, "Audio bytes cannot be empty, defaulting to mp3")
            return "mp3"
        }

        if (audioBytes.size < 12) {
            Log.w(TAG, "Audio data too small for format detection, defaulting to mp3")
            return "mp3"
        }

        return try {
            // Check for MP3 header (ID3 tag or MP3 sync word)
            if (audioBytes[0] == 'I'.code.toByte() &&
                audioBytes[1] == 'D'.code.toByte() &&
                audioBytes[2] == '3'.code.toByte()) {
                Log.d(TAG, "Detected MP3 format (ID3 tag)")
                return "mp3"
            }

            // Check for MP3 sync word (0xFF 0xFB or similar)
            if ((audioBytes[0].toInt() and 0xFF) == 0xFF &&
                (audioBytes[1].toInt() and 0xE0) == 0xE0) {
                Log.d(TAG, "Detected MP3 format (sync word)")
                return "mp3"
            }

            // Check for WAV header
            if (audioBytes[0] == 'R'.code.toByte() &&
                audioBytes[1] == 'I'.code.toByte() &&
                audioBytes[2] == 'F'.code.toByte() &&
                audioBytes[3] == 'F'.code.toByte()) {
                Log.d(TAG, "Detected WAV format")
                return "wav"
            }

            // Check for OGG header
            if (audioBytes[0] == 'O'.code.toByte() &&
                audioBytes[1] == 'g'.code.toByte() &&
                audioBytes[2] == 'g'.code.toByte() &&
                audioBytes[3] == 'S'.code.toByte()) {
                Log.d(TAG, "Detected OGG format")
                return "ogg"
            }

            // Check for FLAC header
            if (audioBytes[0] == 'f'.code.toByte() &&
                audioBytes[1] == 'L'.code.toByte() &&
                audioBytes[2] == 'a'.code.toByte() &&
                audioBytes[3] == 'C'.code.toByte()) {
                Log.d(TAG, "Detected FLAC format")
                return "flac"
            }

            // Default to MP3 for unknown formats
            Log.w(TAG, "Unknown audio format, attempting as mp3")
            "mp3"

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting audio format", e)
            "mp3"
        }
    }

    /**
     * Initialize ExoPlayer with audio-optimized settings
     */
    private fun initializePlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true // Handle audio focus
                )
                .build()
                .also { player ->
                    player.addListener(playerListener)
                    // Set playWhenReady to true for immediate playback when prepared
                    player.playWhenReady = true
                    Log.d(TAG, "ExoPlayer configured - playWhenReady: ${player.playWhenReady}")
                }

            Log.d(TAG, "ExoPlayer initialized with audio-optimized settings")
        }
    }

    /**
     * Check if enough memory is available
     */
    private fun checkMemoryAvailable(requiredBytes: Long): Boolean {
        val runtime = Runtime.getRuntime()
        val availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val threshold = (availableMemory * MEMORY_THRESHOLD).toLong()

        Log.d(TAG, "Memory check - Required: ${requiredBytes/1024/1024}MB, Available: ${availableMemory/1024/1024}MB, Threshold: ${threshold/1024/1024}MB")

        return requiredBytes < threshold
    }

    /**
     * Start progress updates
     */
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = playbackScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _playbackProgress.value = PlaybackProgress(
                        currentPosition = player.currentPosition,
                        duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    )
                }
                delay(100) // Update every 100ms
            }
        }
    }

    /**
     * Stop progress updates
     */
    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    /**
     * Pause playback
     */
    fun pause() {
        exoPlayer?.pause()
        Log.d(TAG, "Playback paused")
    }

    /**
     * Resume playback
     */
    fun resume() {
        exoPlayer?.play()
        Log.d(TAG, "Playback resumed")
    }

    /**
     * Seek to position
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        Log.d(TAG, "Seeked to position: ${positionMs}ms")
    }

    /**
     * Stop current playback
     */
    fun stopPlayback() {
        currentPlaybackJob?.cancel()
        stopProgressUpdates()
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
        }
        _playbackState.value = PlaybackState.IDLE
        _playbackProgress.value = PlaybackProgress(0L, 0L)
        Log.d(TAG, "Playback stopped")
    }

    /**
     * Release ExoPlayer resources
     */
    private fun releasePlayer() {
        stopProgressUpdates()
        exoPlayer?.let { player ->
            player.removeListener(playerListener)
            player.release()
        }
        exoPlayer = null
        _playbackState.value = PlaybackState.IDLE
        _playbackProgress.value = PlaybackProgress(0L, 0L)
        Log.d(TAG, "ExoPlayer released")
    }

    /**
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    /**
     * Get current playback position in milliseconds
     */
    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0

    /**
     * Get total duration in milliseconds
     */
    fun getDuration(): Long = exoPlayer?.duration?.takeIf { it != C.TIME_UNSET } ?: 0

    /**
     * Get playback speed
     */
    fun getPlaybackSpeed(): Float = exoPlayer?.playbackParameters?.speed ?: 1.0f

    /**
     * Set playback speed
     */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        Log.d(TAG, "Playback speed set to: $speed")
    }

    /**
     * Estimate playback duration from audio data
     */
    fun estimatePlaybackDuration(base64Audio: String): Float {
        return try {
            val audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)

            // Rough estimation for MP3: assume 128kbps bitrate
            val fileSizeBits = audioBytes.size * 8
            val durationSeconds = fileSizeBits.toFloat() / DEFAULT_BITRATE

            Log.d(TAG, "Estimated duration: ${durationSeconds}s for ${audioBytes.size} bytes")
            durationSeconds
        } catch (e: Exception) {
            Log.e(TAG, "Failed to estimate duration", e)
            2.0f // Default fallback
        }
    }

    /**
     * Clean up temporary files
     */
    private fun cleanupTempFiles() {
        tempFiles.forEach { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Deleted temp file: ${file.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete temp file: ${file.name}", e)
            }
        }
        tempFiles.clear()
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        currentPlaybackJob?.cancel()
        playbackScope.cancel()
        stopPlayback()
        releasePlayer()
        cleanupTempFiles()
        Log.d(TAG, "AudioPlayer destroyed")
    }
}

/**
 * Reusable ByteArrayDataSource for ExoPlayer
 */
class ReusableByteArrayDataSource(private val data: ByteArray) : BaseDataSource(true) {
    private var bytesRemaining = 0
    private var position = 0

    override fun open(dataSpec: DataSpec): Long {
        position = dataSpec.position.toInt()
        bytesRemaining = data.size - position

        if (bytesRemaining <= 0) {
            throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
        }

        transferStarted(dataSpec)
        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length, bytesRemaining.toLong())
        } else {
            bytesRemaining.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = min(bytesRemaining, length)
        System.arraycopy(data, position, buffer, offset, bytesToRead)
        position += bytesToRead
        bytesRemaining -= bytesToRead
        bytesTransferred(bytesToRead)

        return bytesToRead
    }

    override fun getUri(): Uri? = Uri.parse("data://audio")

    override fun close() {
        // Nothing to close
    }
}

/**
 * Playback state sealed class
 */
sealed class PlaybackState {
    data object IDLE : PlaybackState()
    data object BUFFERING : PlaybackState()
    data object READY : PlaybackState()
    data object PLAYING : PlaybackState()
    data object PAUSED : PlaybackState()
    data object ENDED : PlaybackState()
    data class ERROR(val message: String) : PlaybackState()
}

/**
 * Playback result sealed class
 */
sealed class PlaybackResult {
    data class Success(val message: String) : PlaybackResult()
    data class Error(val message: String) : PlaybackResult()
}

/**
 * Playback progress data class
 */
data class PlaybackProgress(
    val currentPosition: Long,
    val duration: Long
)