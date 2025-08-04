import os
import numpy as np
import soundfile as sf
import logging
import time
from typing import List, Tuple, Dict, Any
from datetime import datetime

logger = logging.getLogger(__name__)

class DynamicQuantizedDiarizationError(Exception):
    pass

class DynamicQuantizedDiarizationEngine:
    def __init__(self, model_path: str, config: Dict[str, Any], audio_processor):
        self.config = config
        self.audio_processor = audio_processor
        self.segment_length = config['segment_length']
        self.segment_step = config['segment_step']
        self.min_segment_ratio = config['min_segment_ratio']
        self.performance_stats = {
            'total_inference_time': 0.0,
            'total_segments_processed': 0,
            'enrollment_time': 0.0,
            'diarization_time': 0.0
        }
        logger.info(f"[SUCCESS] Initialized dynamic quantized diarization engine")
        logger.info(f"[SUCCESS] Model performance: {getattr(self.audio_processor, 'benchmark_results', None)}")

    def compute_similarity(self, embedding1: np.ndarray, embedding2: np.ndarray) -> float:
        try:
            emb1_norm = embedding1 / (np.linalg.norm(embedding1) + 1e-8)
            emb2_norm = embedding2 / (np.linalg.norm(embedding2) + 1e-8)
            similarity = float(np.dot(emb1_norm, emb2_norm))
            return np.clip(similarity, -1.0, 1.0)
        except Exception as e:
            logger.warning(f"Similarity computation failed: {str(e)}")
            return 0.0

    def diarize_meeting(self, meeting_path: str, enrollment_embedding: np.ndarray, 
                       speaker_name: str, threshold: float) -> List[Tuple[float, float]]:
        try:
            logger.info(f"Diarizing meeting audio: {meeting_path}")
            start_time = time.time()
            audio, sr = self.audio_processor.load_audio(meeting_path)
            duration = len(audio) / sr
            logger.info(f"Meeting duration: {duration:.2f}s")
            segments = []
            total_segments = 0
            matched_segments = 0
            total_inference_time = 0.0
            for start in np.arange(0, duration, self.segment_step):
                end = min(start + self.segment_length, duration)
                segment_audio = audio[int(start*sr):int(end*sr)]
                if len(segment_audio) < int(self.segment_length * sr * self.min_segment_ratio):
                    continue
                total_segments += 1
                try:
                    segment_start_time = time.time()
                    segment_embedding = self.audio_processor.extract_embedding(segment_audio, sr)
                    segment_inference_time = time.time() - segment_start_time
                    total_inference_time += segment_inference_time
                    similarity = self.compute_similarity(segment_embedding, enrollment_embedding)
                    logger.debug(f"Segment {start:.2f}s-{end:.2f}s | Similarity: {similarity:.3f} | Time: {segment_inference_time*1000:.1f}ms")
                    if similarity >= threshold:
                        segments.append((start, end))
                        matched_segments += 1
                        logger.info(f"  -> Matched {speaker_name} (Similarity: {similarity:.3f})")
                except Exception as e:
                    logger.warning(f"Failed to process segment {start:.2f}s-{end:.2f}s: {str(e)}")
                    continue
            diarization_time = time.time() - start_time
            self.performance_stats['diarization_time'] = diarization_time
            self.performance_stats['total_inference_time'] = total_inference_time
            self.performance_stats['total_segments_processed'] = total_segments
            avg_inference_time = total_inference_time / total_segments if total_segments > 0 else 0
            real_time_factor = diarization_time / duration if duration > 0 else 0
            logger.info(f"[SUCCESS] Diarization completed: {matched_segments}/{total_segments} segments matched")
            logger.info(f"[SUCCESS] Processing time: {diarization_time:.2f}s (RTF: {real_time_factor:.2f}x)")
            logger.info(f"[SUCCESS] Average inference time per segment: {avg_inference_time*1000:.1f}ms")
            return segments
        except Exception as e:
            raise DynamicQuantizedDiarizationError(f"Meeting diarization failed: {str(e)}")

    def extract_segments(self, meeting_path: str, segments: List[Tuple[float, float]], 
                        output_path: str) -> bool:
        try:
            if not segments:
                logger.warning("No segments to extract")
                return False
            audio, sr = self.audio_processor.load_audio(meeting_path)
            segment_audio_list = []
            total_duration = 0
            for start, end in segments:
                start_sample = int(start * sr)
                end_sample = int(end * sr)
                segment_audio = audio[start_sample:end_sample]
                segment_audio_list.append(segment_audio)
                total_duration += (end - start)
            output_audio = np.concatenate(segment_audio_list)
            sf.write(output_path, output_audio, sr, subtype='PCM_16')
            logger.info(f"[SUCCESS] Extracted {len(segments)} segments ({total_duration:.1f}s total)")
            logger.info(f"[SUCCESS] Saved to: {output_path}")
            return True
        except Exception as e:
            raise DynamicQuantizedDiarizationError(f"Failed to extract segments: {str(e)}")

    def get_performance_summary(self) -> Dict[str, Any]:
        return {
            'enrollment_time': self.performance_stats['enrollment_time'],
            'diarization_time': self.performance_stats['diarization_time'],
            'total_inference_time': self.performance_stats['total_inference_time'],
            'total_segments_processed': self.performance_stats['total_segments_processed'],
            'avg_inference_time_per_segment': (
                self.performance_stats['total_inference_time'] / 
                self.performance_stats['total_segments_processed']
                if self.performance_stats['total_segments_processed'] > 0 else 0
            ),
            'model_benchmark': getattr(self.audio_processor, 'benchmark_results', None)
        } 
 