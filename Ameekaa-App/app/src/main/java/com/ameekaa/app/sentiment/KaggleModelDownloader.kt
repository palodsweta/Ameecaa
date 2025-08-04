package com.ameekaa.app.sentiment

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads Gemma3n model from Kaggle Hub using kaggle.json authentication
 * Handles downloading google/gemma-3n/tfLite/gemma-3n-e2b-it-int4 model
 */
class KaggleModelDownloader(private val context: Context) {
    
    private val TAG = "KaggleModelDownloader"
    
    // Kaggle model configuration
    private val kaggleModel = "google/gemma-3n/tfLite/gemma-3n-e2b-it-int4"
    private val modelFileName = "gemma-3n-E2B-it-int4.task"
    
    // Create models directory in app's private storage
    private val modelsDir = File(context.filesDir, "sentiment_models")
    private val modelFile = File(modelsDir, modelFileName)
    
    // Kaggle API configuration
    private val kaggleApiBaseUrl = "https://www.kaggle.com/api/v1"
    
    init {
        // Ensure models directory exists
        if (!modelsDir.exists()) {
            val created = modelsDir.mkdirs()
            Log.d(TAG, "Models directory created: $created, path: ${modelsDir.absolutePath}")
        }
    }
    
    // HTTP client for downloading large models - increased timeouts
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)  // 10 minutes for large file download
        .writeTimeout(300, TimeUnit.SECONDS) // 5 minutes for write operations
        .callTimeout(1800, TimeUnit.SECONDS) // 30 minutes total call timeout
        .build()
    
    /**
     * Load Kaggle credentials from assets/kaggle.json
     */
    private suspend fun loadKaggleCredentials(): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Loading Kaggle credentials from assets...")
            
            val kaggleJson = context.assets.open("kaggle.json").use { inputStream ->
                inputStream.bufferedReader().readText()
            }
            
            val jsonObject = JSONObject(kaggleJson)
            val username = jsonObject.getString("username")
            val key = jsonObject.getString("key")
            
            if (username.isBlank() || key.isBlank()) {
                return@withContext Result.failure(Exception("Invalid kaggle.json: username or key is empty"))
            }
            
            Log.i(TAG, "Kaggle credentials loaded for user: $username")
            Result.success(Pair(username, key))
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Kaggle credentials: ${e.message}", e)
            Result.failure(Exception("Failed to load kaggle.json from assets: ${e.message}"))
        }
    }
    
    /**
     * Get download URL for the Kaggle model
     */
    private suspend fun getModelDownloadUrl(username: String, key: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Getting download URL for model: $kaggleModel")
            
            // Create basic auth header
            val credentials = android.util.Base64.encodeToString("$username:$key".toByteArray(), android.util.Base64.NO_WRAP)
            
            // Request model download URL from Kaggle API
            val modelPath = kaggleModel.replace("/", "%2F")
            val apiUrl = "$kaggleApiBaseUrl/models/$modelPath/download"
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Basic $credentials")
                .header("User-Agent", "Amica-Android-App/1.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                // Kaggle API returns redirect URL or direct download URL
                val downloadUrl = response.request.url.toString()
                Log.i(TAG, "Got download URL: $downloadUrl")
                Result.success(downloadUrl)
            } else {
                val error = "Kaggle API error: ${response.code} - ${response.message}"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get download URL: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download the Gemma3n model from Kaggle with progress tracking
     * Returns a Flow that emits download progress and completion status
     */
    fun downloadModel(): Flow<DownloadProgress> = flow {
        try {
            // Ensure models directory exists
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
                Log.i(TAG, "Created models directory: ${modelsDir.absolutePath}")
            }
            
            // Check if model already exists
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.i(TAG, "Gemma3n model already exists, skipping download")
                emit(DownloadProgress.Success(modelFile))
                return@flow
            }
            
            Log.i(TAG, "Starting Gemma3n model download from Kaggle...")
            emit(DownloadProgress.Starting)
            
            // Load Kaggle credentials
            val credentialsResult = loadKaggleCredentials()
            if (credentialsResult.isFailure) {
                emit(DownloadProgress.Error("Failed to load Kaggle credentials: ${credentialsResult.exceptionOrNull()?.message}"))
                return@flow
            }
            
            val (username, key) = credentialsResult.getOrThrow()
            
            // Get download URL
            val urlResult = getModelDownloadUrl(username, key)
            if (urlResult.isFailure) {
                emit(DownloadProgress.Error("Failed to get download URL: ${urlResult.exceptionOrNull()?.message}"))
                return@flow
            }
            
            val downloadUrl = urlResult.getOrThrow()
            
            // Download the model file
            emit(DownloadProgress.Progress(0, 0, 0))
            
            val tempFile = File(modelsDir, "${modelFileName}.tmp")
            var bytesDownloaded = 0L
            
            // Support for resuming partial downloads
            if (tempFile.exists()) {
                bytesDownloaded = tempFile.length()
                Log.d(TAG, "Resuming download from byte: $bytesDownloaded")
            }
            
            val requestBuilder = Request.Builder().url(downloadUrl)
            if (bytesDownloaded > 0) {
                requestBuilder.header("Range", "bytes=$bytesDownloaded-")
            }
            
            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful && response.code != 206) {
                emit(DownloadProgress.Error("Download failed: ${response.code} - ${response.message}"))
                return@flow
            }
            
            val totalFileSize = response.header("Content-Length")?.toLongOrNull() ?: 0L
            val totalBytesToDownload = if (response.code == 206) {
                // Partial content - get range info
                response.header("Content-Range")?.let { range ->
                    val parts = range.split("/")
                    if (parts.size == 2) parts[1].toLongOrNull() else totalFileSize
                } ?: totalFileSize
            } else {
                totalFileSize
            }
            
            Log.i(TAG, "Downloading ${totalBytesToDownload / 1024 / 1024} MB...")
            
            val source = response.body!!.source()
            val sink = tempFile.sink().buffer()
            
            var totalBytesRead = bytesDownloaded
            val bufferSize = 8192L // 8KB buffer for better performance
            
            try {
                while (true) {
                    val bytesRead = source.read(sink.buffer, bufferSize)
                    if (bytesRead == -1L) break
                    
                    totalBytesRead += bytesRead
                    
                    // Emit progress
                    if (totalBytesToDownload > 0) {
                        val progress = (totalBytesRead * 100 / totalBytesToDownload).toInt()
                        emit(DownloadProgress.Progress(progress, totalBytesRead, totalBytesToDownload))
                    }
                }
                
                sink.close()
                source.close()
                
                // Move temp file to final location
                if (tempFile.renameTo(modelFile)) {
                    Log.i(TAG, "Gemma3n model download completed successfully")
                } else {
                    throw Exception("Failed to finalize downloaded file")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}")
                emit(DownloadProgress.Error("Download failed: ${e.message}"))
                return@flow
            }
            
            // Verify download
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.i(TAG, "Gemma3n model downloaded successfully: ${modelFile.length()} bytes")
                emit(DownloadProgress.Success(modelFile))
            } else {
                emit(DownloadProgress.Error("Download completed but file is missing or empty"))
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error during download: ${e.message}", e)
            emit(DownloadProgress.Error("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during download: ${e.message}", e)
            emit(DownloadProgress.Error("Download failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Check if the model is already downloaded
     */
    fun isModelDownloaded(): Boolean {
        val exists = modelFile.exists()
        val size = if (exists) modelFile.length() else 0L
        Log.d(TAG, "Model check: exists=$exists, size=$size, path=${modelFile.absolutePath}")
        return exists && size > 0
    }
    
    /**
     * Get model file information
     */
    fun getModelInfo(): ModelDownloadInfo {
        return ModelDownloadInfo(
            isDownloaded = isModelDownloaded(),
            modelPath = modelFile.absolutePath,
            modelSize = if (modelFile.exists()) modelFile.length() else 0L,
            modelName = "Gemma3n-E2B-IT-TFLite-INT4",
            requiredSpace = 3_000_000_000L // ~3GB for INT4 quantized model
        )
    }
    
    /**
     * Delete the downloaded model (for cleanup)
     */
    fun deleteModel(): Boolean {
        return try {
            if (modelFile.exists()) {
                val deleted = modelFile.delete()
                if (deleted) {
                    Log.i(TAG, "Gemma3n model file deleted successfully")
                } else {
                    Log.w(TAG, "Failed to delete Gemma3n model file")
                }
                deleted
            } else {
                Log.i(TAG, "Gemma3n model file doesn't exist, nothing to delete")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Gemma3n model file: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get available storage space
     */
    fun getAvailableSpace(): Long {
        return modelsDir.freeSpace
    }
    
    /**
     * Check if there's enough space for the model
     */
    fun hasEnoughSpace(): Boolean {
        val requiredSpace = 3_000_000_000L // ~3GB for INT4 quantized model
        val availableSpace = getAvailableSpace()
        return availableSpace > requiredSpace
    }
    
    /**
     * Check if kaggle.json exists in assets
     */
    fun hasKaggleCredentials(): Boolean {
        return try {
            context.assets.open("kaggle.json").use { true }
        } catch (e: Exception) {
            Log.w(TAG, "kaggle.json not found in assets: ${e.message}")
            false
        }
    }
}

/**
 * Sealed class representing download progress states
 */
sealed class DownloadProgress {
    object Starting : DownloadProgress()
    data class Progress(
        val percentage: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadProgress()
    data class Success(val modelFile: File) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

/**
 * Data class for model download information
 */
data class ModelDownloadInfo(
    val isDownloaded: Boolean,
    val modelPath: String,
    val modelSize: Long,
    val modelName: String,
    val requiredSpace: Long
) 


