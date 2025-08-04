package com.ameekaa.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class for managing asset files and copying them to internal storage
 */
object AssetUtils {
    private const val TAG = "AssetUtils"
    
    /**
     * Setup test data by copying assets to internal storage
     * @param context Application context
     * @return TestDataPaths containing paths to all test files
     */
    fun setupTestData(context: Context): TestDataPaths {
        val testDataDir = File(context.filesDir, "test_data")
        val modelsDir = File(testDataDir, "models/onnx")
        
        // Create directories
        testDataDir.mkdirs()
        modelsDir.mkdirs()
        
        // Copy test audio files
        val enrollmentFile = File(testDataDir, "speaker_enrollment_user4.wav")
        val meetingFile = File(testDataDir, "meeting_audio_user4.wav")
        val outputFile = File(testDataDir, "user4_dynamic_segments.wav")
        
        // Copy ONNX model
        val modelFile = File(modelsDir, "ecapa_model_dynamic_quantized.onnx")
        
        try {
            // Copy enrollment audio if not exists or if asset is newer
            if (!enrollmentFile.exists() || shouldUpdateFile(context, "test_data/speaker_enrollment_user4.wav", enrollmentFile)) {
                copyAssetToFile(context, "test_data/speaker_enrollment_user4.wav", enrollmentFile)
                Log.i(TAG, "Copied speaker enrollment audio: ${enrollmentFile.absolutePath}")
            }
            
            // Copy meeting audio if not exists or if asset is newer
            if (!meetingFile.exists() || shouldUpdateFile(context, "test_data/meeting_audio_user4.wav", meetingFile)) {
                copyAssetToFile(context, "test_data/meeting_audio_user4.wav", meetingFile)
                Log.i(TAG, "Copied meeting audio: ${meetingFile.absolutePath}")
            }
            
            // Copy ONNX model if not exists or if asset is newer
            if (!modelFile.exists() || shouldUpdateFile(context, "ecapa_model_dynamic_quantized.onnx", modelFile)) {
                copyAssetToFile(context, "ecapa_model_dynamic_quantized.onnx", modelFile)
                Log.i(TAG, "Copied ONNX model: ${modelFile.absolutePath}")
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Error setting up test data: ${e.message}", e)
            throw RuntimeException("Failed to setup test data", e)
        }
        
        return TestDataPaths(
            enrollmentPath = enrollmentFile.absolutePath,
            meetingPath = meetingFile.absolutePath,
            outputPath = outputFile.absolutePath,
            modelPath = modelFile.absolutePath,
            testDataDir = testDataDir.absolutePath
        )
    }
    
    /**
     * Copy an asset file to internal storage
     */
    private fun copyAssetToFile(context: Context, assetPath: String, targetFile: File) {
        context.assets.open(assetPath).use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
    
    /**
     * Check if asset file should be updated (simple size comparison)
     */
    private fun shouldUpdateFile(context: Context, assetPath: String, targetFile: File): Boolean {
        return try {
            val assetDescriptor = context.assets.openFd(assetPath)
            val assetSize = assetDescriptor.length
            assetDescriptor.close()
            
            // Update if sizes don't match
            targetFile.length() != assetSize
        } catch (e: Exception) {
            // If we can't get asset size, assume we should update
            true
        }
    }
    
    /**
     * Clean up test data directory
     */
    fun cleanupTestData(context: Context) {
        val testDataDir = File(context.filesDir, "test_data")
        if (testDataDir.exists()) {
            testDataDir.deleteRecursively()
            Log.i(TAG, "Cleaned up test data directory")
        }
    }
    
    /**
     * Get test data paths without copying (assumes files are already copied)
     */
    fun getTestDataPaths(context: Context): TestDataPaths {
        val testDataDir = File(context.filesDir, "test_data")
        val modelsDir = File(testDataDir, "models/onnx")
        
        return TestDataPaths(
            enrollmentPath = File(testDataDir, "speaker_enrollment_user4.wav").absolutePath,
            meetingPath = File(testDataDir, "meeting_audio_user4.wav").absolutePath,
            outputPath = File(testDataDir, "user4_dynamic_segments.wav").absolutePath,
            modelPath = File(modelsDir, "ecapa_model_dynamic_quantized.onnx").absolutePath,
            testDataDir = testDataDir.absolutePath
        )
    }
}

/**
 * Data class containing paths to all test data files
 */
data class TestDataPaths(
    val enrollmentPath: String,
    val meetingPath: String,
    val outputPath: String,
    val modelPath: String,
    val testDataDir: String
) 


