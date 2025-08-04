# Ameekaa Python - Audio Emotion Analysis Pipeline

A comprehensive audio processing pipeline that performs speaker diarization, emotion analysis, and sentiment detection using quantized models optimized for mobile and edge devices.

## Features

- **Speaker Diarization**: Identify and separate different speakers in audio recordings
- **Emotion Analysis**: Analyze emotions using Plutchik's Wheel of Emotions
- **ECAPA-TDNN Integration**: Speaker verification using ECAPA-TDNN model
- **Model Quantization**: Optimized models for mobile/edge deployment
- **Multi-format Support**: Process various audio file formats
- **Batch Processing**: Handle multiple audio files efficiently

## Project Structure

```
Ameekaa-Python/
├── models/                    # Model storage directory
│   ├── onnx/                 # ONNX model files
│   └── speechbrain_ecapa/    # SpeechBrain ECAPA model files
├── test_data/                # Test audio files
├── diarization_output/       # Speaker diarization results
└── emotion_analysis_output/  # Emotion analysis results
```

## Installation

1. Clone the repository:
```bash
git clone [repository-url]
cd Ameekaa-Python
```

2. Install dependencies:
```bash
pip install -r requirements.txt
```

## Model Setup

### Download Quantized Gemma Model
```bash
python download_quantized_gemma_model.py --quantization 4bit
```

Options:
- `--quantization`: Choose from '4bit', '8bit', or 'fp16' (default: 4bit)
- `--save-path`: Custom save location
- `--onnx`: Download ONNX version
- `--test-only`: Test existing model
- `--test-audio`: Test with specific audio file

### Convert ECAPA Model to ONNX
```bash
python ecapa_to_onnx_pipeline.py
```

### Model Quantization
```bash
python ecapa_onnx_quantization.py
```

## Usage

### 1. Speaker Diarization
```bash
python run_dynamic_quantized_diarization.py --enroll [path_to_enroll_audio] --meeting [path_to_meeting_audio] --output [path_to_output_audio] --name [user_name]
```

### 2. Emotion Analysis
```bash
python gemma3n_plutchik_audio_analysis.py --audio [path_to_audio]
```

## Model Quantization

The project supports various quantization methods:
- Dynamic Quantization (recommended for CPU)
- Static Quantization
- ONNX Export

## Output Formats

### Diarization Results
- JSON files with speaker segments
- Separated audio files per speaker

### Emotion Analysis
- Text files with detailed emotion analysis
- Plutchik's Wheel categorization
- Intensity scores (1-10)

## Requirements

- Python 3.8+
- CUDA compatible GPU (optional, for faster processing)
- See requirements.txt for full dependency list

## Performance Considerations

- 4-bit quantization recommended for mobile deployment
- CPU-only mode available with fp16 quantization
- Batch processing for multiple files
- Memory optimization for large audio files

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

[Your License Here]

## Acknowledgments

- SpeechBrain for ECAPA-TDNN model
- Google's Gemma model for emotion analysis
- ONNX Runtime for model optimization 