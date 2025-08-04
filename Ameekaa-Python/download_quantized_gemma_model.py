#!/usr/bin/env python3
"""
Download and save a quantized Gemma model for mobile/local processing.
This script downloads a quantized version of the Gemma model that can run efficiently on mobile devices.
"""

import os
import torch
from transformers import AutoProcessor, AutoModelForImageTextToText, BitsAndBytesConfig
from pathlib import Path
import argparse
from datetime import datetime

# Model configurations
GEMMA_MODEL_ID = "google/gemma-3n-E2B-it"
LOCAL_MODEL_DIR = "models/gemma-3n-quantized"

def download_quantized_gemma_model(quantization_type="4bit", save_path=LOCAL_MODEL_DIR):
    """
    Download and save a quantized Gemma model for mobile use.
    
    Args:
        quantization_type (str): Type of quantization - "4bit", "8bit", or "fp16"
        save_path (str): Local directory to save the model
    """
    print(f"Starting download of quantized Gemma model ({quantization_type})...")
    print(f"Model ID: {GEMMA_MODEL_ID}")
    print(f"Save path: {save_path}")
    print(f"Started at: {datetime.now()}")
    
    # Create models directory if it doesn't exist
    os.makedirs(save_path, exist_ok=True)
    
    try:
        # Configure quantization
        if quantization_type == "4bit":
            quantization_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_compute_dtype=torch.float16,
                bnb_4bit_use_double_quant=True,
                bnb_4bit_quant_type="nf4"
            )
        elif quantization_type == "8bit":
            quantization_config = BitsAndBytesConfig(
                load_in_8bit=True
            )
        else:  # fp16
            quantization_config = None
        
        # Download processor
        print("Downloading processor...")
        processor = AutoProcessor.from_pretrained(GEMMA_MODEL_ID)
        processor.save_pretrained(save_path)
        print(f"Processor saved to: {save_path}")
        
        # Download and quantize model
        print(f"Downloading and quantizing model ({quantization_type})...")
        if quantization_config:
            model = AutoModelForImageTextToText.from_pretrained(
                GEMMA_MODEL_ID,
                quantization_config=quantization_config,
                torch_dtype=torch.float16,
                device_map="auto"
            )
        else:
            model = AutoModelForImageTextToText.from_pretrained(
                GEMMA_MODEL_ID,
                torch_dtype=torch.float16,
                device_map="auto"
            )
        
        # Save the quantized model
        print("Saving quantized model...")
        model.save_pretrained(save_path)
        print(f"Model saved to: {save_path}")
        
        # Save model info
        model_info = {
            "original_model_id": GEMMA_MODEL_ID,
            "quantization_type": quantization_type,
            "download_date": datetime.now().isoformat(),
            "model_size_gb": get_folder_size_gb(save_path),
            "usage": "Mobile-optimized for sentiment analysis using Plutchik's Wheel of Emotions"
        }
        
        import json
        with open(os.path.join(save_path, "model_info.json"), "w") as f:
            json.dump(model_info, f, indent=2)
        
        print(f"\n✅ Successfully downloaded and saved quantized Gemma model!")
        print(f"📁 Location: {os.path.abspath(save_path)}")
        print(f"📊 Model size: ~{model_info['model_size_gb']:.1f} GB")
        print(f"⚡ Quantization: {quantization_type}")
        print(f"🕒 Completed at: {datetime.now()}")
        
        return save_path
        
    except Exception as e:
        print(f"❌ Error downloading model: {str(e)}")
        return None

def download_onnx_version(save_path="models/gemma-3n-onnx"):
    """
    Download ONNX version of Gemma model for mobile deployment.
    """
    print("Downloading ONNX version for mobile deployment...")
    
    try:
        # Check if there's an ONNX version available
        onnx_model_id = "microsoft/gemma-3n-onnx"  # This might not exist, it's an example
        
        # For now, we'll use the quantized PyTorch version
        # In production, you'd convert to ONNX or TensorFlow Lite
        print("Note: ONNX conversion not implemented yet.")
        print("Using quantized PyTorch model instead.")
        
        return download_quantized_gemma_model("4bit", save_path)
        
    except Exception as e:
        print(f"ONNX download failed: {str(e)}")
        print("Falling back to quantized PyTorch model...")
        return download_quantized_gemma_model("4bit", save_path)

def get_folder_size_gb(folder_path):
    """Calculate folder size in GB."""
    total_size = 0
    for dirpath, dirnames, filenames in os.walk(folder_path):
        for filename in filenames:
            filepath = os.path.join(dirpath, filename)
            if os.path.exists(filepath):
                total_size += os.path.getsize(filepath)
    return total_size / (1024 ** 3)  # Convert to GB

def test_local_model(model_path, audio_file=None):
    """
    Test the downloaded model with a sample audio file.
    """
    print(f"\n🧪 Testing local model at: {model_path}")
    
    try:
        # Load local model
        processor = AutoProcessor.from_pretrained(model_path)
        model = AutoModelForImageTextToText.from_pretrained(
            model_path,
            torch_dtype=torch.float16,
            device_map="auto"
        )
        
        print("✅ Model loaded successfully!")
        print(f"📊 Model device: {model.device}")
        print(f"🔧 Model dtype: {model.dtype}")
        
        if audio_file and os.path.exists(audio_file):
            print(f"🎵 Testing with audio file: {audio_file}")
            
            prompt = (
                "Can you analyze the emotion in this audio using Plutchik's Wheel of Emotions? "
                "Return the primary emotion(s), intensity (1-10), and a brief explanation in JSON format."
            )
            messages = [
                {
                    "role": "user",
                    "content": [
                        {"type": "audio", "audio": audio_file},
                        {"type": "text", "text": prompt},
                    ]
                }
            ]
            
            input_ids = processor.apply_chat_template(
                messages,
                add_generation_prompt=True,
                tokenize=True,
                return_dict=True,
                return_tensors="pt",
            )
            
            input_ids = input_ids.to(model.device, dtype=model.dtype)
            
            with torch.no_grad():
                outputs = model.generate(**input_ids, max_new_tokens=128)
            
            text = processor.batch_decode(
                outputs,
                skip_special_tokens=False,
                clean_up_tokenization_spaces=False
            )
            
            print("\n📝 Test Result:")
            print(text[0])
        
        return True
        
    except Exception as e:
        print(f"❌ Test failed: {str(e)}")
        return False

def main():
    parser = argparse.ArgumentParser(
        description="Download quantized Gemma model for mobile processing",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python download_quantized_gemma_model.py --quantization 4bit
  python download_quantized_gemma_model.py --quantization 8bit --save-path models/gemma-custom
  python download_quantized_gemma_model.py --onnx
  python download_quantized_gemma_model.py --test-only --model-path models/gemma-3n-quantized
        """
    )
    
    parser.add_argument(
        '--quantization', 
        choices=['4bit', '8bit', 'fp16'], 
        default='4bit',
        help='Quantization type (default: 4bit for mobile)'
    )
    parser.add_argument(
        '--save-path', 
        default=LOCAL_MODEL_DIR,
        help='Directory to save the model'
    )
    parser.add_argument(
        '--onnx', 
        action='store_true',
        help='Download ONNX version for mobile deployment'
    )
    parser.add_argument(
        '--test-only', 
        action='store_true',
        help='Only test existing local model'
    )
    parser.add_argument(
        '--model-path', 
        help='Path to local model for testing'
    )
    parser.add_argument(
        '--test-audio', 
        help='Audio file to test with'
    )
    
    args = parser.parse_args()
    
    if args.test_only:
        model_path = args.model_path or args.save_path
        test_local_model(model_path, args.test_audio)
        return
    
    print("=" * 70)
    print("📱 Gemma Model Downloader for Mobile Processing")
    print("=" * 70)
    
    if args.onnx:
        result_path = download_onnx_version(args.save_path)
    else:
        result_path = download_quantized_gemma_model(args.quantization, args.save_path)
    
    if result_path:
        print(f"\n🎉 Download completed! Model ready for mobile processing.")
        print(f"📍 Use this path in your mobile app: {os.path.abspath(result_path)}")
        
        # Optional test
        if args.test_audio:
            test_local_model(result_path, args.test_audio)
    else:
        print("❌ Download failed. Please check your internet connection and try again.")

if __name__ == "__main__":
    main() 