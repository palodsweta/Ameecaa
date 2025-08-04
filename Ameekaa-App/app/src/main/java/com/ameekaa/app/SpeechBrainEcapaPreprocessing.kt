package com.ameekaa.app

import kotlin.math.*
import org.jtransforms.fft.FloatFFT_1D

class PreprocessingError(message: String) : Exception(message)

object SpeechBrainEcapaPreprocessing {

    /**
     * Validates input parameters for preprocessing - matches Python validate_input
     */
    private fun validateInput(waveform: FloatArray, sampleRate: Int) {
        if (waveform.isEmpty()) {
            throw PreprocessingError("waveform cannot be empty")
        }
        
        if (sampleRate <= 0) {
            throw PreprocessingError("sample_rate must be a positive integer, got $sampleRate")
        }
        
        if (waveform.any { it.isNaN() }) {
            throw PreprocessingError("waveform contains NaN values")
        }
        
        if (waveform.any { it.isInfinite() }) {
            throw PreprocessingError("waveform contains infinite values")
        }
    }

    /**
     * Convert frequency in Hz to mel scale using HTK formula - matches Python hz_to_mel
     */
    private fun hzToMel(hz: Float, melScale: String = "htk"): Float {
        return when (melScale) {
            "htk" -> 2595.0f * log10(1.0f + hz / 700.0f)
            else -> throw PreprocessingError("Unsupported mel scale: $melScale. Only 'htk' is supported.")
        }
    }

    /**
     * Convert mel scale to frequency in Hz using HTK formula - matches Python mel_to_hz
     */
    private fun melToHz(mel: Float, melScale: String = "htk"): Float {
        return when (melScale) {
            "htk" -> 700.0f * (10.0f.pow(mel / 2595.0f) - 1.0f)
            else -> throw PreprocessingError("Unsupported mel scale: $melScale. Only 'htk' is supported.")
        }
    }

    /**
     * Create a complete mel filterbank matrix without skipping filters - matches Python create_filterbank_matrix
     */
    private fun createFilterbankMatrix(
        nMels: Int,
        nFft: Int,
        sampleRate: Int,
        fMin: Float = 0.0f,
        fMax: Float? = null,
        norm: String? = null,
        melScale: String = "htk"
    ): Array<FloatArray> {
        try {
            val actualFMax = fMax ?: (sampleRate / 2.0f)
            
            // Validate parameters
            if (nMels <= 0) throw PreprocessingError("n_mels must be positive, got $nMels")
            if (nFft <= 0) throw PreprocessingError("n_fft must be positive, got $nFft")
            if (sampleRate <= 0) throw PreprocessingError("sample_rate must be positive, got $sampleRate")
            if (fMin < 0) throw PreprocessingError("f_min must be non-negative, got $fMin")
            if (actualFMax <= fMin) throw PreprocessingError("f_max must be greater than f_min, got f_max=$actualFMax, f_min=$fMin")
            
            // Convert frequencies to mel scale
            val melMin = hzToMel(fMin, melScale)
            val melMax = hzToMel(actualFMax, melScale)
            
            // Create mel frequencies
            val melFreqs = FloatArray(nMels + 2) { i ->
                melMin + (melMax - melMin) * i.toFloat() / (nMels + 1).toFloat()
            }
            val hzFreqs = melFreqs.map { melToHz(it, melScale) }
            
            // Convert to FFT bin indices
            val bins = hzFreqs.map { hz ->
                floor((nFft + 1) * hz / sampleRate).toInt().coerceIn(0, nFft)
            }
            
            // Create filterbank matrix
            val filterbank = Array(nMels) { FloatArray(nFft / 2 + 1) }
            
            for (i in 0 until nMels) {
                val leftBin = bins[i]
                val centerBin = bins[i + 1]
                val rightBin = bins[i + 2]
                
                // Rising slope
                if (leftBin < centerBin) {
                    for (j in leftBin..centerBin) {
                        if (j < filterbank[i].size) {
                            filterbank[i][j] = (j - leftBin).toFloat() / (centerBin - leftBin + 1e-8f)
                        }
                    }
                }
                
                // Falling slope
                if (centerBin < rightBin) {
                    for (j in centerBin..rightBin) {
                        if (j < filterbank[i].size) {
                            filterbank[i][j] = (rightBin - j).toFloat() / (rightBin - centerBin + 1e-8f)
                        }
                    }
                }
            }
            
            // Apply Slaney normalization if requested
            if (norm == "slaney") {
                for (i in 0 until nMels) {
                    val enorm = 2.0f / (hzFreqs[i + 2] - hzFreqs[i])
                    for (j in filterbank[i].indices) {
                        filterbank[i][j] *= enorm
                    }
                }
            }
            
            return filterbank
            
        } catch (e: Exception) {
            throw PreprocessingError("Failed to create filterbank matrix: ${e.message}")
        }
    }

    /**
     * Create Hann window - matches Python torch.hann_window
     */
    private fun hannWindow(length: Int): FloatArray {
        return FloatArray(length) { i ->
            0.5f - 0.5f * cos(2.0 * PI * i / length).toFloat()
        }
    }

    /**
     * Apply windowing and padding to create frames - matches Python STFT framing
     */
    private fun frameSignal(
        signal: FloatArray,
        nFft: Int,
        hopLength: Int,
        winLength: Int,
        center: Boolean = true,
        padMode: String = "reflect"
    ): List<FloatArray> {
        val window = hannWindow(winLength)
        
        // Apply center padding if requested
        val paddedSignal = if (center) {
            val padLength = nFft / 2
            val leftPad = FloatArray(padLength)
            val rightPad = FloatArray(padLength)
            
            when (padMode) {
                "reflect" -> {
                    // Reflect padding
                    for (i in 0 until padLength) {
                        leftPad[padLength - 1 - i] = signal[minOf(i + 1, signal.size - 1)]
                        rightPad[i] = signal[maxOf(signal.size - 2 - i, 0)]
                    }
                }
                else -> {
                    // Zero padding
                    leftPad.fill(0.0f)
                    rightPad.fill(0.0f)
                }
            }
            
            leftPad + signal + rightPad
        } else {
            signal
        }
        
        // Create frames
        val numFrames = 1 + (paddedSignal.size - nFft) / hopLength
        return List(numFrames.coerceAtLeast(1)) { i ->
            val start = i * hopLength
            val end = start + nFft
            val frame = FloatArray(nFft)
            
            for (j in 0 until nFft) {
                val idx = start + j
                if (idx < paddedSignal.size) {
                    frame[j] = paddedSignal[idx] * window[j % winLength]
                }
            }
            frame
        }
    }

    /**
     * Compute power spectrum using FFT - matches Python torch.stft with power=2.0
     */
    private fun powerSpectrum(frame: FloatArray, nFft: Int, power: Float = 2.0f): FloatArray {
        val fft = FloatFFT_1D(nFft.toLong())
        val fftInput = frame.copyOf(nFft)
        fft.realForward(fftInput)
        
        val spectrum = FloatArray(nFft / 2 + 1)
        
        // DC component
        spectrum[0] = abs(fftInput[0]).pow(power)
        
        // Positive frequencies
        for (i in 1 until nFft / 2) {
            val re = fftInput[2 * i]
            val im = fftInput[2 * i + 1]
            spectrum[i] = (re * re + im * im).pow(power / 2.0f)
        }
        
        // Nyquist frequency
        spectrum[nFft / 2] = abs(fftInput[1]).pow(power)
        
        return spectrum
    }

    /**
     * Extract log-mel filterbank features using exact SpeechBrain preprocessing - matches Python extract_log_mel_filterbank_features_simple
     */
    fun extractLogMelFilterbankFeaturesSimple(
        waveform: FloatArray,
        sampleRate: Int,
        nMels: Int = 80,
        nFft: Int = 400,
        hopLength: Int = 160,
        winLength: Int = 400,
        window: String = "hann",
        center: Boolean = true,
        padMode: String = "reflect",
        power: Float = 2.0f,
        norm: String? = "slaney",
        melScale: String = "htk",
        fMin: Float = 0.0f,
        fMax: Float? = null,
        topDb: Float = 80.0f,
        logMel: Boolean = true
    ): Array<FloatArray> {
        try {
            // Validate input
            validateInput(waveform, sampleRate)
            
            // Create frames using STFT-like framing
            val frames = frameSignal(waveform, nFft, hopLength, winLength, center, padMode)
            
            // Compute power spectrogram for each frame
            val powerSpectra = frames.map { frame -> powerSpectrum(frame, nFft, power) }
            
            // Create filterbank matrix
            val filterbank = createFilterbankMatrix(
                nMels = nMels,
                nFft = nFft,
                sampleRate = sampleRate,
                fMin = fMin,
                fMax = fMax,
                norm = norm,
                melScale = melScale
            )
            
            // Apply filterbank to each frame
            val melSpectra = powerSpectra.map { spectrum ->
                FloatArray(nMels) { m ->
                    var sum = 0.0f
                    for (j in spectrum.indices) {
                        sum += filterbank[m][j] * spectrum[j]
                    }
                    sum
                }
            }
            
            // Convert to log scale if requested
            return if (logMel) {
                melSpectra.map { melSpectrum ->
                    melSpectrum.map { value -> ln(value + 1e-8f) }.toFloatArray()
                }.toTypedArray()
            } else {
                melSpectra.toTypedArray()
            }
            
        } catch (e: Exception) {
            throw PreprocessingError("Failed to extract log-mel features: ${e.message}")
        }
    }

    /**
     * Main function that replicates Python extract_log_mel_filterbank_features_simple exactly
     * This is what gets called from EnrollmentDynamicQuantize.kt
     */
    fun extractLogMel(
        audio: FloatArray,
        sampleRate: Int = 16000,
        nMels: Int = 80,
        nFft: Int = 400,
        hopLength: Int = 160,
        winLength: Int = 400,
        fMin: Float = 0.0f,
        fMax: Float? = null
    ): Array<FloatArray> {
        return extractLogMelFilterbankFeaturesSimple(
            waveform = audio,
            sampleRate = sampleRate,
            nMels = nMels,
            nFft = nFft,
            hopLength = hopLength,
            winLength = winLength,
            window = "hann",
            center = true,
            padMode = "reflect",
            power = 2.0f,
            norm = "slaney",  // This is the key difference - Slaney normalization
            melScale = "htk", // HTK mel scale
            fMin = fMin,
            fMax = fMax,
            topDb = 80.0f,
            logMel = true
        )
    }
} 


