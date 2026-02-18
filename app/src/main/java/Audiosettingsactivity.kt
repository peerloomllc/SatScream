package com.peerloomllc.satscream

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import androidx.core.content.edit

class AudioSettingsActivity : AppCompatActivity() {

    private lateinit var tvPumpAudioStatus: TextView
    private lateinit var tvDumpAudioStatus: TextView

    private var selectingPump = true  // Track which audio we're selecting
    private var mediaPlayer: MediaPlayer? = null  // For playing test audio

    // File picker launcher - using OpenDocument for better MIME type filtering
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Validate it's an audio file
            val mimeType = contentResolver.getType(it)
            if (mimeType?.startsWith("audio/") == true) {
                handleAudioSelection(it, selectingPump)
            } else {
                Toast.makeText(this, "Please select an audio file (.mp3, .wav, .m4a, etc.)", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_settings)

        val btnBack = findViewById<ImageButton>(R.id.btnBackAudio)
        tvPumpAudioStatus = findViewById(R.id.tvPumpAudioStatus)
        tvDumpAudioStatus = findViewById(R.id.tvDumpAudioStatus)

        val btnSelectPumpAudio = findViewById<Button>(R.id.btnSelectPumpAudio)
        val btnPlayPumpAudio = findViewById<ImageButton>(R.id.btnPlayPumpAudio)
        val btnResetPumpAudio = findViewById<Button>(R.id.btnResetPumpAudio)
        val btnSelectDumpAudio = findViewById<Button>(R.id.btnSelectDumpAudio)
        val btnPlayDumpAudio = findViewById<ImageButton>(R.id.btnPlayDumpAudio)
        val btnResetDumpAudio = findViewById<Button>(R.id.btnResetDumpAudio)

        // Back button
        btnBack.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            finish()
        }

        // Pump audio buttons
        btnSelectPumpAudio.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            selectingPump = true
            openFilePicker()
        }

        btnResetPumpAudio.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            resetToDefault(true)
        }

        // Play pump audio button
        btnPlayPumpAudio.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            playTestAudio(true)
        }

        // Dump audio buttons
        btnSelectDumpAudio.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            selectingPump = false
            openFilePicker()
        }

        btnResetDumpAudio.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            resetToDefault(false)
        }

        // Play dump audio button
        btnPlayDumpAudio.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            playTestAudio(false)
        }

        // Load current status
        updateAudioStatus()
    }

    private fun openFilePicker() {
        // OpenDocument requires an array of MIME types
        // This provides better filtering than GetContent
        filePickerLauncher.launch(arrayOf(
            "audio/*",
            "audio/mpeg",      // .mp3
            "audio/wav",       // .wav
            "audio/x-wav",     // .wav (alternative)
            "audio/mp4",       // .m4a
            "audio/ogg",       // .ogg
            "audio/flac"       // .flac
        ))
    }

    private fun handleAudioSelection(uri: Uri, isPump: Boolean) {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)

        try {
            // Get file size
            val fileSize = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L

            // Check file size limit (10MB = 10 * 1024 * 1024 bytes)
            val maxFileSize = 10 * 1024 * 1024L
            if (fileSize > maxFileSize) {
                val fileSizeMB = fileSize / (1024.0 * 1024.0)
                Toast.makeText(
                    this,
                    "Audio file too large (${String.format(Locale.US, "%.1f", fileSizeMB)} MB). Maximum size is 10 MB.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // Check duration (optional - 30 seconds max)
            val duration = getAudioDuration(uri)
            if (duration > 5000) { // 5 seconds in milliseconds
                val durationSeconds = duration / 1000
                Toast.makeText(
                    this,
                    "Audio file too long ($durationSeconds seconds). Maximum length is 5 seconds.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // Get the original filename from the URI
            val originalFileName = getFileNameFromUri(uri) ?: "audio_file.wav"

            // Copy the audio file to internal storage
            val fileName = if (isPump) "custom_pump_audio.wav" else "custom_dump_audio.wav"
            val outputFile = java.io.File(filesDir, fileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Save the internal file path AND original filename
            val pathKey = if (isPump) "CUSTOM_PUMP_AUDIO_PATH" else "CUSTOM_DUMP_AUDIO_PATH"
            val nameKey = if (isPump) "CUSTOM_PUMP_AUDIO_NAME" else "CUSTOM_DUMP_AUDIO_NAME"

            sharedPrefs.edit {
                putString(pathKey, outputFile.absolutePath)
                putString(nameKey, originalFileName)
            }

            Toast.makeText(
                this,
                if (isPump) "Pump alert audio updated" else "Dump alert audio updated",
                Toast.LENGTH_SHORT
            ).show()

            updateAudioStatus()

        } catch (e: Exception) {
            android.util.Log.e("AudioSettings", "Error saving audio", e)
            Toast.makeText(
                this,
                "Error selecting audio file: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getAudioDuration(uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            android.util.Log.e("AudioSettings", "Error getting audio duration", e)
            0L // If we can't get duration, allow it (fail open)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null

        // Try to get filename from content resolver
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }

        // Fallback to last path segment
        if (fileName == null) {
            fileName = uri.lastPathSegment
        }

        return fileName
    }

    private fun resetToDefault(isPump: Boolean) {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
        val pathKey = if (isPump) "CUSTOM_PUMP_AUDIO_PATH" else "CUSTOM_DUMP_AUDIO_PATH"
        val nameKey = if (isPump) "CUSTOM_PUMP_AUDIO_NAME" else "CUSTOM_DUMP_AUDIO_NAME"

        // Remove the custom audio file
        val filePath = sharedPrefs.getString(pathKey, null)
        filePath?.let {
            val file = java.io.File(it)
            if (file.exists()) {
                file.delete()
            }
        }

        // Remove both path and name preferences
        sharedPrefs.edit {
            remove(pathKey)
            remove(nameKey)
        }

        Toast.makeText(
            this,
            if (isPump) "Reset to default pump audio" else "Reset to default dump audio",
            Toast.LENGTH_SHORT
        ).show()

        updateAudioStatus()
    }

    private fun playTestAudio(isPump: Boolean) {
        try {
            // Stop any currently playing audio
            mediaPlayer?.release()
            mediaPlayer = null

            val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)
            val customAudioKey = if (isPump) "CUSTOM_PUMP_AUDIO_PATH" else "CUSTOM_DUMP_AUDIO_PATH"
            val customAudioPath = sharedPrefs.getString(customAudioKey, null)

            mediaPlayer = MediaPlayer()

            // Check if custom audio exists
            if (customAudioPath != null && customAudioPath.isNotEmpty()) {
                val customFile = java.io.File(customAudioPath)
                if (customFile.exists() && customFile.canRead() && customFile.length() > 0) {
                    // Play custom audio
                    mediaPlayer?.setDataSource(customAudioPath)
                    mediaPlayer?.prepare()
                    mediaPlayer?.start()
                    android.util.Log.d("AudioSettings", "Playing custom test audio: $customAudioPath")
                    return
                }
            }

            // Play default audio from assets
            val assetFileName = if (isPump) "pump.wav" else "dump.wav"
            val afd = assets.openFd(assetFileName)

            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            mediaPlayer?.prepare()
            mediaPlayer?.start()
            android.util.Log.d("AudioSettings", "Playing default test audio: $assetFileName")

        } catch (e: Exception) {
            android.util.Log.e("AudioSettings", "Error playing test audio", e)
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun updateAudioStatus() {
        val sharedPrefs = getSharedPreferences("BitcoinPrefs", MODE_PRIVATE)

        // Update pump audio status
        val pumpPath = sharedPrefs.getString("CUSTOM_PUMP_AUDIO_PATH", null)
        val pumpName = sharedPrefs.getString("CUSTOM_PUMP_AUDIO_NAME", null)

        android.util.Log.d("AudioSettings", "Pump - Path: $pumpPath, Name: $pumpName")

        tvPumpAudioStatus.text = if (pumpPath != null && java.io.File(pumpPath).exists() && pumpName != null) {
            val statusText = "Using custom audio \"$pumpName\""
            android.util.Log.d("AudioSettings", "Setting pump status to: $statusText")
            statusText
        } else {
            android.util.Log.d("AudioSettings", "Using default pump audio")
            getString(R.string.using_default_audio)
        }

        // Update dump audio status
        val dumpPath = sharedPrefs.getString("CUSTOM_DUMP_AUDIO_PATH", null)
        val dumpName = sharedPrefs.getString("CUSTOM_DUMP_AUDIO_NAME", null)

        android.util.Log.d("AudioSettings", "Dump - Path: $dumpPath, Name: $dumpName")

        tvDumpAudioStatus.text = if (dumpPath != null && java.io.File(dumpPath).exists() && dumpName != null) {
            val statusText = "Using custom audio \"$dumpName\""
            android.util.Log.d("AudioSettings", "Setting dump status to: $statusText")
            statusText
        } else {
            android.util.Log.d("AudioSettings", "Using default dump audio")
            getString(R.string.using_default_audio)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up MediaPlayer
        mediaPlayer?.release()
        mediaPlayer = null
    }
}