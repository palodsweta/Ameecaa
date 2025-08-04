package com.ameekaa.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import ai.onnxruntime.*
import kotlin.math.*

class DynamicQuantizedDiarizationError(message: String) : Exception(message)

class DynamicQuantizedAudioProcessor(
    private val context: Context? = null,
    private val modelPath: String = "ecapa_model_dynamic_quantized.onnx",
    private val sampleRate: Int = 16000
) {
    private val TAG = "DynamicQuantizedAudioProcessor"
    
    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private lateinit var ortSession: OrtSession
    val benchmarkResults = mutableMapOf<String, Any>()

    // Feature extraction parameters matching Python implementation
    private val nMels = 80
    private val nFft = 400
    private val hopLength = 160
    private val winLength = 400
    private val fMin = 0.0f
    private val fMax = sampleRate / 2.0f
    private val topDb = 80.0f

    init {
        try {
            Log.i(TAG, "Initializing DynamicQuantizedAudioProcessor...")
            Log.i(TAG, "Model path: $modelPath")
            Log.i(TAG, "Context available: ${context != null}")
            
            // Load ONNX model from assets or file
            val modelBytes = if (context != null) {
                Log.i(TAG, "Loading model from assets...")
                try {
                    context.assets.open(modelPath).use { input ->
                        BufferedInputStream(input).readBytes()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load from assets, trying file path: ${e.message}")
                    File(modelPath).readBytes()
                }
            } else {
                Log.i(TAG, "Loading model from file system...")
                File(modelPath).readBytes()
            }

            Log.i(TAG, "Model bytes loaded: ${modelBytes.size} bytes")

            // Configure session options to match Python implementation
            Log.i(TAG, "Creating ONNX session...")
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(2)
                setInterOpNumThreads(2)
            }
            
            ortSession = ortEnvironment.createSession(modelBytes, sessionOptions)
            Log.i(TAG, "ONNX session created successfully")
            
            val modelSize = modelBytes.size / (1024.0 * 1024.0)
            Log.i(TAG, "[SUCCESS] Loaded dynamic quantized ONNX model: $modelPath")
            Log.i(TAG, "[SUCCESS] Model size: ${"%.1f".format(modelSize)} MB")
            
            // Benchmark model performance
            benchmarkModel()
        } catch (e: Exception) {
            Log.e(TAG, "Detailed error during ONNX model loading: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw DynamicQuantizedDiarizationError("Failed to load dynamic quantized ONNX model: $modelPath - ${e.message}")
        }
    }

    private fun benchmarkModel(): Map<String, Double> {
        return try {
            // Create test input matching Python benchmark
            val testInput = FloatArray(1 * 200 * 80) { (Math.random() * 2 - 1).toFloat() }
            val inputShape = longArrayOf(1, 200, 80)
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(testInput), inputShape)
            
            // Warmup runs
            repeat(3) {
                ortSession.run(mapOf("input" to inputTensor))
            }
            
            // Benchmark runs
            val times = mutableListOf<Double>()
            repeat(5) {
                val startTime = System.nanoTime()
                ortSession.run(mapOf("input" to inputTensor))
                val endTime = System.nanoTime()
                times.add((endTime - startTime) / 1_000_000.0) // Convert to ms
            }
            
            val avgTime = times.average()
            val stdTime = sqrt(times.map { (it - avgTime).pow(2) }.average())
            val throughput = if (avgTime > 0) 1000.0 / avgTime else 0.0
            
            val results = mapOf(
                "avg_inference_time_ms" to avgTime,
                "std_inference_time_ms" to stdTime,
                "throughput_fps" to throughput
            )
            
            benchmarkResults.putAll(results)
            Log.i(TAG, "[SUCCESS] Model performance: ${"%.2f".format(avgTime)}ms ± ${"%.2f".format(stdTime)}ms")
            results
        } catch (e: Exception) {
            Log.w(TAG, "Benchmark failed: ${e.message}")
            emptyMap()
        }
    }

    fun loadAudio(audioPath: String): Pair<FloatArray, Int> {
        try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                throw DynamicQuantizedDiarizationError("Audio file not found: $audioPath")
            }
            
            // Use AudioUtils to load the WAV file
            val audio = AudioUtils.readWavAsFloatArray(audioFile.inputStream(), sampleRate)
            
            if (audio.isEmpty()) {
                throw DynamicQuantizedDiarizationError("Audio file is empty: $audioPath")
            }
            
            val duration = audio.size.toDouble() / sampleRate
            if (duration < 0.5) {
                throw DynamicQuantizedDiarizationError("Audio too short: ${"%.2f".format(duration)}s (minimum 0.5s)")
            }
            
            Log.d(TAG, "Loaded audio: $audioPath, duration: ${"%.2f".format(duration)}s, shape: ${audio.size}")
            return Pair(audio, sampleRate)
        } catch (e: Exception) {
            throw DynamicQuantizedDiarizationError("Failed to load audio $audioPath: ${e.message}")
        }
    }

    fun extractEmbedding(audio: FloatArray, sr: Int): FloatArray {
        try {
            // Ensure mono audio
            val monoAudio = audio // Assuming already mono from loadAudio
            
            // Extract log-mel features using SpeechBrain ECAPA preprocessing (matches Python exactly)
            val featuresFramesMels = SpeechBrainEcapaPreprocessing.extractLogMel(
                audio = monoAudio,
                sampleRate = sr,
                nMels = nMels,
                nFft = nFft,
                hopLength = hopLength,
                winLength = winLength,
                fMin = fMin,
                fMax = fMax
            )
            
            // Convert from [frames, 80] to [80, frames] to match Python format
            val numFrames = featuresFramesMels.size
            val features = Array(nMels) { FloatArray(numFrames) }
            for (f in 0 until numFrames) {
                for (m in 0 until nMels) {
                    features[m][f] = featuresFramesMels[f][m]
                }
            }
            
            // Apply per-utterance mean normalization exactly like Python:
            // mean = features.mean(dim=1, keepdim=True)  # [80, 1] - mean across time for each mel band
            // features = features - mean  # Only mean normalization
            val mean = FloatArray(nMels) { m ->
                features[m].average().toFloat()
            }
            
            for (m in 0 until nMels) {
                for (f in 0 until numFrames) {
                    features[m][f] -= mean[m]
                }
            }
            
            // Prepare input tensor for ONNX - shape [1, frames, 80] 
            // Convert back to [frames, 80] format for ONNX input
            val inputData = FloatArray(numFrames * nMels)
            for (f in 0 until numFrames) {
                for (m in 0 until nMels) {
                    inputData[f * nMels + m] = features[m][f]
                }
            }
            
            // Run inference
            val startTime = System.nanoTime()
            val inputShape = longArrayOf(1, numFrames.toLong(), nMels.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(inputData), inputShape)
            
            val output = ortSession.run(mapOf("input" to inputTensor))
            val endTime = System.nanoTime()
            
            val inferenceTime = (endTime - startTime) / 1_000_000.0 // ms
            
            // Extract embedding from output tensor
            // The model output is shape [1, embedding_dim], we need to extract the first row
            val outputTensor = output[0]
            Log.i(TAG, "Output tensor type: ${outputTensor?.javaClass?.simpleName}")
            
            // Try different approaches to extract the embedding
            val embeddingArray = try {
                // First, try to get the raw value and handle it properly
                val rawValue = outputTensor.value
                Log.i(TAG, "Raw value type: ${rawValue?.javaClass?.simpleName}")
                
                when {
                    // Case 1: Direct FloatArray
                    rawValue is FloatArray -> {
                        Log.i(TAG, "Found direct FloatArray, size: ${rawValue.size}")
                        rawValue
                    }
                    // Case 2: 2D array (most likely case)
                    rawValue is Array<*> && rawValue.isNotEmpty() -> {
                        Log.i(TAG, "Found Array, size: ${rawValue.size}")
                        val firstElement = rawValue[0]
                        Log.i(TAG, "First element type: ${firstElement?.javaClass?.simpleName}")
                        
                        when {
                            firstElement is FloatArray -> {
                                Log.d(TAG, "First element is FloatArray, size: ${firstElement.size}")
                                firstElement
                            }
                            firstElement is Array<*> -> {
                                // Handle Array<Array<Float>> case
                                val nestedArray = firstElement as Array<*>
                                Log.d(TAG, "Nested array size: ${nestedArray.size}")
                                if (nestedArray.isNotEmpty()) {
                                    val firstNestedElement = nestedArray[0]
                                    Log.d(TAG, "First nested element type: ${firstNestedElement?.javaClass?.simpleName}")
                                    
                                    when {
                                        firstNestedElement is Float -> {
                                            Log.d(TAG, "Converting Array<Array<Float>> to FloatArray")
                                            FloatArray(nestedArray.size) { (nestedArray[it] as Float) }
                                        }
                                        firstNestedElement is FloatArray -> {
                                            Log.d(TAG, "Found Array<FloatArray>, using first FloatArray")
                                            firstNestedElement
                                        }
                                        else -> {
                                            Log.e(TAG, "Unexpected nested element type: ${firstNestedElement?.javaClass}")
                                            Log.e(TAG, "Sample nested elements: ${nestedArray.take(3).map { it?.javaClass?.simpleName }}")
                                            throw IllegalArgumentException("Unexpected nested array content: ${firstNestedElement?.javaClass}")
                                        }
                                    }
                                } else {
                                    throw IllegalArgumentException("Empty nested array")
                                }
                            }
                            else -> throw IllegalArgumentException("Unexpected first element type: ${firstElement?.javaClass}")
                        }
                    }
                    // Case 3: Something else entirely
                    else -> {
                        Log.e(TAG, "Unexpected output format. Raw value: $rawValue")
                        throw IllegalArgumentException("Unexpected output format: ${rawValue?.javaClass}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract embedding with detailed logging: ${e.message}")
                e.printStackTrace()
                
                // Last resort: try to flatten any nested structure
                Log.w(TAG, "Attempting last resort flattening...")
                try {
                    val rawValue = outputTensor.value
                    val flattenedList = mutableListOf<Float>()
                    
                    fun flattenToFloats(obj: Any?) {
                        when (obj) {
                            is Float -> flattenedList.add(obj)
                            is Array<*> -> obj.forEach { flattenToFloats(it) }
                            is FloatArray -> flattenedList.addAll(obj.toList())
                            else -> Log.w(TAG, "Skipping unflattenable object: ${obj?.javaClass}")
                        }
                    }
                    
                    flattenToFloats(rawValue)
                    Log.i(TAG, "Flattened to ${flattenedList.size} floats")
                    
                    if (flattenedList.isNotEmpty()) {
                        flattenedList.toFloatArray()
                    } else {
                        throw e // Re-throw original exception if flattening fails
                    }
                } catch (flattenException: Exception) {
                    Log.e(TAG, "Flattening also failed: ${flattenException.message}")
                    throw e // Re-throw original exception
                }
            }
            
            Log.d(TAG, "Extracted embedding shape: ${embeddingArray.size}, inference time: ${"%.2f".format(inferenceTime)}ms")
            Log.i(TAG, "[SUCCESS] Speaker embedding extracted successfully")
            return embeddingArray
        } catch (e: Exception) {
            throw DynamicQuantizedDiarizationError("Failed to extract embedding: ${e.message}")
        }
    }
}

/**
 * Enroll a speaker from enrollment audio - matches Python enroll_speaker function exactly
 */
fun enrollSpeaker(
    enrollmentPath: String, 
    speakerName: String, 
    modelPath: String, 
    sampleRate: Int = 16000,
    context: Context? = null
): FloatArray {
    val processor = DynamicQuantizedAudioProcessor(context, modelPath, sampleRate)
    Log.i("EnrollSpeaker", "Enrolling speaker '$speakerName' from $enrollmentPath")
    
    val (audio, sr) = processor.loadAudio(enrollmentPath)
    val embedding = processor.extractEmbedding(audio, sr)
    
    Log.i("EnrollSpeaker", "[SUCCESS] Enrolled speaker '$speakerName' - embedding shape: ${embedding.size}")
    return embedding
} 


