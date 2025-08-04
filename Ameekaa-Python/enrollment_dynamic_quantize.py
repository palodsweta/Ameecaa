import os
import numpy as np
import torch
import librosa
import logging
import time
from typing import Tuple, Dict, Any
from speechbrain_ecapa_preprocessing import extract_log_mel_filterbank_features_simple, PreprocessingError

logger = logging.getLogger(__name__)

class DynamicQuantizedDiarizationError(Exception):
    pass

class DynamicQuantizedAudioProcessor:
    def __init__(self, model_path: str = "models/onnx/ecapa_model_dynamic_quantized.onnx", sample_rate: int = 16000):
        try:
            import onnxruntime as ort
            self.sample_rate = sample_rate
            self.session = ort.InferenceSession(model_path)
            model_size = os.path.getsize(model_path) / (1024 * 1024)
            logger.info(f"[SUCCESS] Loaded dynamic quantized ONNX model: {model_path}")
            logger.info(f"[SUCCESS] Model size: {model_size:.1f} MB")
            self.benchmark_results = self._benchmark_model()
            if self.benchmark_results:
                logger.info(f"[SUCCESS] Model performance: {self.benchmark_results['avg_inference_time_ms']:.2f}ms ± {self.benchmark_results['std_inference_time_ms']:.2f}ms")
        except Exception as e:
            raise DynamicQuantizedDiarizationError(f"Failed to load dynamic quantized ONNX model: {str(e)}")

    def _benchmark_model(self) -> Dict[str, float]:
        try:
            test_input = np.random.randn(1, 200, 80).astype(np.float32)
            for _ in range(3):
                self.session.run(None, {'input': test_input})
            times = []
            for _ in range(5):
                start_time = time.time()
                self.session.run(None, {'input': test_input})
                end_time = time.time()
                times.append((end_time - start_time) * 1000)
            return {
                'avg_inference_time_ms': float(np.mean(times)),
                'std_inference_time_ms': float(np.std(times)),
                'throughput_fps': float(1000.0 / np.mean(times)) if np.mean(times) > 0 else 0.0
            }
        except:
            return {}

    def load_audio(self, audio_path: str) -> Tuple[np.ndarray, int]:
        try:
            if not os.path.exists(audio_path):
                raise DynamicQuantizedDiarizationError(f"Audio file not found: {audio_path}")
            audio, sr = librosa.load(audio_path, sr=self.sample_rate, mono=True)
            if len(audio) == 0:
                raise DynamicQuantizedDiarizationError(f"Audio file is empty: {audio_path}")
            duration = len(audio) / sr
            if duration < 0.5:
                raise DynamicQuantizedDiarizationError(f"Audio too short: {duration:.2f}s (minimum 0.5s)")
            logger.debug(f"Loaded audio: {audio_path}, duration: {duration:.2f}s, shape: {audio.shape}")
            return audio, int(sr)
        except Exception as e:
            raise DynamicQuantizedDiarizationError(f"Failed to load audio {audio_path}: {str(e)}")

    def extract_embedding(self, audio: np.ndarray, sr: int) -> np.ndarray:
        try:
            if len(audio.shape) > 1:
                audio = np.mean(audio, axis=1)
            waveform = torch.tensor(audio, dtype=torch.float32)
            features = extract_log_mel_filterbank_features_simple(
                waveform=waveform,
                sample_rate=sr,
                n_mels=80,
                n_fft=400,
                hop_length=160,
                win_length=400,
                window="hann",
                center=True,
                pad_mode="reflect",
                power=2.0,
                norm="slaney",
                mel_scale="htk",
                f_min=0.0,
                f_max=None,
                top_db=80.0,
                log_mel=True,
            )
            features = features.squeeze(0)
            mean = features.mean(dim=1, keepdim=True)
            features = features - mean
            feats = features.cpu().numpy().astype(np.float32)
            feats = np.transpose(feats, (1, 0))[np.newaxis, ...]
            start_time = time.time()
            output = self.session.run(None, {'input': feats})[0]
            inference_time = (time.time() - start_time) * 1000
            embedding = np.squeeze(output)
            logger.debug(f"Extracted embedding shape: {embedding.shape}, inference time: {inference_time:.2f}ms")
            return embedding
        except Exception as e:
            raise DynamicQuantizedDiarizationError(f"Failed to extract embedding: {str(e)}")

def enroll_speaker(enrollment_path: str, speaker_name: str, model_path: str, sample_rate: int = 16000) -> np.ndarray:
    processor = DynamicQuantizedAudioProcessor(model_path, sample_rate)
    logger.info(f"Enrolling speaker '{speaker_name}' from {enrollment_path}")
    audio, sr = processor.load_audio(enrollment_path)
    embedding = processor.extract_embedding(audio, sr)
    logger.info(f"[SUCCESS] Enrolled speaker '{speaker_name}' - embedding shape: {embedding.shape}")
    return embedding 