package com.ameekaa.app

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {
    fun readWavAsFloatArray(inputStream: InputStream, targetSampleRate: Int): FloatArray {
        val header = ByteArray(44)
        inputStream.read(header, 0, 44)
        val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val numChannels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        require(bitsPerSample == 16) { "Only 16-bit PCM WAV supported" }
        val pcmBytes = inputStream.readBytes()
        val numSamples = pcmBytes.size / 2 / numChannels
        
        // Convert 16-bit PCM to float array exactly like librosa.load()
        // librosa normalizes 16-bit samples by dividing by 32768.0 (not 32767)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val idx = i * numChannels * 2  // Stereo to mono: take left channel
            val sample = ByteBuffer.wrap(pcmBytes, idx, 2).order(ByteOrder.LITTLE_ENDIAN).short
            // CRITICAL: Match librosa's exact normalization: divide by 32768.0f (not 32767)
            samples[i] = sample.toFloat() / 32768.0f
        }
        
        // Resample if needed (basic implementation)
        return if (sampleRate != targetSampleRate) {
            resample(samples, sampleRate, targetSampleRate)
        } else {
            samples
        }
    }
    
    private fun resample(input: FloatArray, inputSampleRate: Int, outputSampleRate: Int): FloatArray {
        if (inputSampleRate == outputSampleRate) return input
        
        val ratio = inputSampleRate.toDouble() / outputSampleRate
        val outputLength = (input.size / ratio).toInt()
        val output = FloatArray(outputLength)
        
        for (i in output.indices) {
            val srcIndex = i * ratio
            val index = srcIndex.toInt()
            if (index < input.size - 1) {
                val fraction = srcIndex - index
                output[i] = (input[index] * (1 - fraction) + input[index + 1] * fraction).toFloat()
            } else if (index < input.size) {
                output[i] = input[index]
            }
        }
        
        return output
    }
}


