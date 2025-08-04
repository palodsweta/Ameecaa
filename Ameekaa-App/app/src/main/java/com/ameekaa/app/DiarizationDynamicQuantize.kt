package com.ameekaa.app

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class DiarizationSegment(val startTime: Double, val endTime: Double)

class DynamicQuantizedDiarizationEngine(
    private val modelPath: String,
    private val config: Map<String, Any>,
    val audioProcessor: DynamicQuantizedAudioProcessor
) {
    private val TAG = "DynamicQuantizedDiarizationEngine"
    
    private val segmentLength: Double = (config["segment_length"] as? Double) ?: 2.0
    private val segmentStep: Double = (config["segment_step"] as? Double) ?: 2.0
    private val minSegmentRatio: Double = (config["min_segment_ratio"] as? Double) ?: 0.5
    
    val performanceStats = mutableMapOf<String, Any>(
        "total_inference_time" to 0.0,
        "total_segments_processed" to 0,
        "enrollment_time" to 0.0,
        "diarization_time" to 0.0
    )

    init {
        Log.i(TAG, "[SUCCESS] Initialized dynamic quantized diarization engine")
        Log.i(TAG, "[SUCCESS] Model performance: ${audioProcessor.benchmarkResults}")
    }

    fun computeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        try {
            if (embedding1.isEmpty() || embedding2.isEmpty() || embedding1.size != embedding2.size) {
                Log.w(TAG, "Invalid embeddings for similarity computation")
                return 0.0f
            }
            
            val epsilon = 1e-8f
            
            // Calculate L2 norms (exactly like Python np.linalg.norm)
            var norm1 = 0.0f
            var norm2 = 0.0f
            for (i in embedding1.indices) {
                norm1 += embedding1[i] * embedding1[i]
                norm2 += embedding2[i] * embedding2[i]
            }
            norm1 = sqrt(norm1) + epsilon
            norm2 = sqrt(norm2) + epsilon

            // Normalize embeddings first (exactly like Python: embedding / norm)
            val emb1Norm = FloatArray(embedding1.size) { i -> embedding1[i] / norm1 }
            val emb2Norm = FloatArray(embedding2.size) { i -> embedding2[i] / norm2 }

            // Compute dot product of normalized embeddings (exactly like Python: np.dot)
            var similarity = 0.0f
            for (i in emb1Norm.indices) {
                similarity += emb1Norm[i] * emb2Norm[i]
            }

            return similarity.coerceIn(-1.0f, 1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "Similarity computation failed: ${e.message}")
            return 0.0f
        }
    }

    fun diarizeMeeting(
        meetingPath: String,
        enrollmentEmbedding: FloatArray,
        speakerName: String,
        threshold: Float
    ): List<DiarizationSegment> {
        try {
            Log.i(TAG, "Diarizing meeting audio: $meetingPath")
            val startTime = System.currentTimeMillis()
            
            val (audio, sr) = audioProcessor.loadAudio(meetingPath)
            val duration = audio.size.toDouble() / sr
            Log.i(TAG, "Meeting duration: ${"%.2f".format(duration)}s")
            
            val segments = mutableListOf<DiarizationSegment>()
            var totalSegments = 0
            var matchedSegments = 0
            var totalInferenceTime = 0.0
            
            // Process segments using step size
            var currentTime = 0.0
            while (currentTime < duration) {
                val endTime = minOf(currentTime + segmentLength, duration)
                
                val startSample = (currentTime * sr).toInt()
                val endSample = (endTime * sr).toInt()
                val segmentAudio = audio.sliceArray(startSample until endSample)
                
                // Check minimum segment ratio
                if (segmentAudio.size < (segmentLength * sr * minSegmentRatio).toInt()) {
                    currentTime += segmentStep
                    continue
                }
                
                totalSegments++
                
                try {
                    val segmentStartTime = System.currentTimeMillis()
                    val segmentEmbedding = audioProcessor.extractEmbedding(segmentAudio, sr)
                    val segmentInferenceTime = System.currentTimeMillis() - segmentStartTime
                    totalInferenceTime += segmentInferenceTime
                    
                    val similarity = computeSimilarity(segmentEmbedding, enrollmentEmbedding)
                    Log.d(TAG, "Segment ${"%.2f".format(currentTime)}s-${"%.2f".format(endTime)}s | " +
                              "Similarity: ${"%.3f".format(similarity)} | " +
                              "Time: ${"%.1f".format(segmentInferenceTime.toDouble())}ms")
                    
                    if (similarity >= threshold) {
                        segments.add(DiarizationSegment(currentTime, endTime))
                        matchedSegments++
                        Log.i(TAG, "  -> Matched $speakerName (Similarity: ${"%.3f".format(similarity)})")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process segment ${"%.2f".format(currentTime)}s-${"%.2f".format(endTime)}s: ${e.message}")
                }
                
                currentTime += segmentStep
            }
            
            val diarizationTime = System.currentTimeMillis() - startTime
            performanceStats["diarization_time"] = diarizationTime.toDouble()
            performanceStats["total_inference_time"] = totalInferenceTime
            performanceStats["total_segments_processed"] = totalSegments
            
            val avgInferenceTime = if (totalSegments > 0) totalInferenceTime / totalSegments else 0.0
            val realTimeFactor = if (duration > 0) diarizationTime / (duration * 1000) else 0.0
            
            Log.i(TAG, "[SUCCESS] Diarization completed: $matchedSegments/$totalSegments segments matched")
            Log.i(TAG, "[SUCCESS] Processing time: ${"%.2f".format(diarizationTime / 1000.0)}s (RTF: ${"%.2f".format(realTimeFactor)}x)")
            Log.i(TAG, "[SUCCESS] Average inference time per segment: ${"%.1f".format(avgInferenceTime)}ms")
            
            return segments
            
        } catch (e: Exception) {
            throw DynamicQuantizedDiarizationError("Meeting diarization failed: ${e.message}")
        }
    }

    fun extractSegments(
        meetingPath: String,
        segments: List<DiarizationSegment>,
        outputPath: String
    ): Boolean {
        try {
            if (segments.isEmpty()) {
                Log.w(TAG, "No segments to extract")
                return false
            }
            
            val (audio, sr) = audioProcessor.loadAudio(meetingPath)
            val segmentAudioList = mutableListOf<FloatArray>()
            var totalDuration = 0.0
            
            for (segment in segments) {
                val startSample = (segment.startTime * sr).toInt()
                val endSample = (segment.endTime * sr).toInt()
                val segmentAudio = audio.sliceArray(startSample until endSample)
                segmentAudioList.add(segmentAudio)
                totalDuration += (segment.endTime - segment.startTime)
            }
            
            // Concatenate all segments
            val totalSamples = segmentAudioList.sumOf { it.size }
            val outputAudio = FloatArray(totalSamples)
            var currentIndex = 0
            for (segmentAudio in segmentAudioList) {
                segmentAudio.copyInto(outputAudio, currentIndex)
                currentIndex += segmentAudio.size
            }
            
            // Save as 16-bit PCM WAV file
            saveAudioAsWav(outputAudio, sr, outputPath)
            
            Log.i(TAG, "[SUCCESS] Extracted ${segments.size} segments (${"%.1f".format(totalDuration)}s total)")
            Log.i(TAG, "[SUCCESS] Saved to: $outputPath")
            return true
            
        } catch (e: Exception) {
            throw DynamicQuantizedDiarizationError("Failed to extract segments: ${e.message}")
        }
    }

    fun getPerformanceSummary(): Map<String, Any> {
        val totalSegments = performanceStats["total_segments_processed"] as? Int ?: 0
        val totalInferenceTime = performanceStats["total_inference_time"] as? Double ?: 0.0
        
        return mapOf(
            "enrollment_time" to (performanceStats["enrollment_time"] ?: 0.0),
            "diarization_time" to (performanceStats["diarization_time"] ?: 0.0),
            "total_inference_time" to totalInferenceTime,
            "total_segments_processed" to totalSegments,
            "avg_inference_time_per_segment" to if (totalSegments > 0) totalInferenceTime / totalSegments else 0.0,
            "model_benchmark" to audioProcessor.benchmarkResults
        )
    }

    private fun saveAudioAsWav(audioData: FloatArray, sampleRate: Int, outputPath: String) {
        try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            // Create WAV header
            val dataSize = audioData.size * 2 // 16-bit samples
            val totalSize = 36 + dataSize
            
            FileOutputStream(outputFile).use { fos ->
                // Write WAV header
                fos.write("RIFF".toByteArray())
                fos.write(intToByteArray(totalSize))
                fos.write("WAVE".toByteArray())
                fos.write("fmt ".toByteArray())
                fos.write(intToByteArray(16)) // PCM header size
                fos.write(shortToByteArray(1)) // PCM format
                fos.write(shortToByteArray(1)) // Mono
                fos.write(intToByteArray(sampleRate))
                fos.write(intToByteArray(sampleRate * 2)) // Byte rate
                fos.write(shortToByteArray(2)) // Block align
                fos.write(shortToByteArray(16)) // Bits per sample
                fos.write("data".toByteArray())
                fos.write(intToByteArray(dataSize))
                
                // Write audio data as 16-bit PCM (match Python's sf.write behavior exactly)
                for (sample in audioData) {
                    // Convert float [-1, 1] to 16-bit PCM exactly like Python soundfile
                    // Ensure no clipping and preserve original audio quality
                    val normalizedSample = sample.coerceIn(-1.0f, 1.0f)
                    val pcmSample = (normalizedSample * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    fos.write(shortToByteArray(pcmSample))
                }
            }
        } catch (e: Exception) {
            throw DynamicQuantizedDiarizationError("Failed to save WAV file: ${e.message}")
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
} 


