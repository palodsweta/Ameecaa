import argparse
import logging
import json
from datetime import datetime
from pathlib import Path

from enrollment_dynamic_quantize import enroll_speaker, DynamicQuantizedAudioProcessor, DynamicQuantizedDiarizationError
from diarization_dynamic_quantize import DynamicQuantizedDiarizationEngine

def load_config(config_path=None):
    default_config = {
        'sample_rate': 16000,
        'segment_length': 2.0,
        'segment_step': 2.0,
        'min_segment_ratio': 0.5,
        'default_threshold': 0.6,
        'model_path': 'models/onnx/ecapa_model_dynamic_quantized.onnx'
    }
    if config_path:
        try:
            with open(config_path, 'r') as f:
                file_config = json.load(f)
            default_config.update(file_config)
        except Exception as e:
            print(f"Warning: Failed to load config from {config_path}: {str(e)}")
    return default_config

def save_results(results, output_dir, speaker_name):
    try:
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        results_file = output_dir / f"dynamic_quantized_diarization_results_{speaker_name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(results_file, 'w') as f:
            json.dump(results, f, indent=2, default=str)
        print(f"[SUCCESS] Saved results to {results_file}")
    except Exception as e:
        print(f"Warning: Failed to save results: {str(e)}")

def main():
    parser = argparse.ArgumentParser(
        description="Run Dynamic Quantized ECAPA Speaker Enrollment and Diarization",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument('--enroll', required=True, help='Enrollment audio file (WAV)')
    parser.add_argument('--meeting', required=True, help='Meeting audio file (WAV)')
    parser.add_argument('--output', required=True, help='Output WAV file for extracted segments')
    parser.add_argument('--name', required=True, help='Speaker name (for labeling)')
    parser.add_argument('--model', help='Dynamic quantized ECAPA ONNX model path')
    parser.add_argument('--threshold', type=float, help='Similarity threshold (0.0-1.0)')
    parser.add_argument('--config', help='Configuration JSON file')
    parser.add_argument('--results-dir', default='diarization_output', help='Directory for results')
    parser.add_argument('--verbose', '-v', action='store_true', help='Enable verbose logging')
    args = parser.parse_args()

    if args.verbose:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig(level=logging.INFO)

    try:
        print("=" * 70)
        print("AMICA - Dynamic Quantized ECAPA Speaker Diarization System")
        print("=" * 70)
        config = load_config(args.config)
        if args.model:
            config['model_path'] = args.model
        if args.threshold:
            config['default_threshold'] = args.threshold
        # Enrollment
        enrollment_embedding = enroll_speaker(
            args.enroll, args.name, config['model_path'], config['sample_rate']
        )
        # Diarization
        audio_processor = DynamicQuantizedAudioProcessor(config['model_path'], config['sample_rate'])
        engine = DynamicQuantizedDiarizationEngine(config['model_path'], config, audio_processor)
        segments = engine.diarize_meeting(
            args.meeting,
            enrollment_embedding,
            args.name,
            config['default_threshold']
        )
        # Extract segments
        success = engine.extract_segments(args.meeting, segments, args.output)
        if success:
            performance = engine.get_performance_summary()
            results = {
                'speaker_name': args.name,
                'enrollment_file': args.enroll,
                'meeting_file': args.meeting,
                'output_file': args.output,
                'threshold': config['default_threshold'],
                'model_type': 'dynamic_quantized',
                'model_path': config['model_path'],
                'segments': segments,
                'total_segments': len(segments),
                'total_duration': sum(end - start for start, end in segments),
                'performance': performance,
                'timestamp': datetime.now().isoformat(),
                'config': config
            }
            save_results(results, args.results_dir, args.name)
            print("=" * 70)
            print(f"[SUCCESS] Extracted {len(segments)} segments for speaker: {args.name}")
            print(f"[SUCCESS] Total processing time: {performance['enrollment_time'] + performance['diarization_time']:.2f}s")
            print(f"[SUCCESS] Average inference time: {performance['avg_inference_time_per_segment']*1000:.1f}ms")
            print("=" * 70)
        else:
            print("No segments were extracted.")
    except DynamicQuantizedDiarizationError as e:
        print(f"[ERROR] Dynamic quantized diarization failed: {str(e)}")
    except KeyboardInterrupt:
        print("Dynamic quantized diarization interrupted by user")
    except Exception as e:
        print(f"[ERROR] Unexpected error: {str(e)}")

if __name__ == "__main__":
    main() 