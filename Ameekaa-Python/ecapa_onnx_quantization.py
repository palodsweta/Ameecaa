#!/usr/bin/env python3
"""
Quantize ECAPA-TDNN ONNX model for improved performance and reduced size.
Supports both dynamic and static quantization methods.
"""
import os
import sys
import logging
import numpy as np
from pathlib import Path
import onnx
import onnxruntime as ort
from onnxruntime.quantization import (
    quantize_dynamic,
    quantize_static,
    QuantType,
    QuantFormat,
    CalibrationDataReader
)
from typing import Dict, List, Tuple, Optional
import torch

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('quantization.log')
    ]
)
logger = logging.getLogger(__name__)

class ECAPACalibrationDataReader(CalibrationDataReader):
    """Custom calibration data reader for ECAPA-TDNN model."""
    
    def __init__(self, calibration_data: List[np.ndarray], input_name: str = "input"):
        self.calibration_data = calibration_data
        self.input_name = input_name
        self.index = 0
    
    def get_next(self) -> Optional[Dict[str, np.ndarray]]:
        if self.index >= len(self.calibration_data):
            return None
        
        data = {self.input_name: self.calibration_data[self.index]}
        self.index += 1
        return data
    
    def rewind(self):
        self.index = 0

def generate_calibration_data(
    num_samples: int = 100,
    input_shape: Tuple[int, int, int] = (1, 200, 80),
    random_seed: int = 42
) -> List[np.ndarray]:
    """
    Generate calibration data for static quantization.
    
    Args:
        num_samples: Number of calibration samples to generate
        input_shape: Shape of input data (batch, frames, features)
        random_seed: Random seed for reproducibility
    
    Returns:
        List of numpy arrays for calibration
    """
    logger.info(f"Generating {num_samples} calibration samples...")
    np.random.seed(random_seed)
    
    calibration_data = []
    for i in range(num_samples):
        # Generate realistic audio feature data
        # Vary frame length to simulate different audio durations
        frames = np.random.randint(50, 400)
        sample = np.random.randn(1, frames, 80).astype(np.float32)
        
        # Normalize to typical mel-spectrogram ranges
        sample = (sample - sample.mean()) / (sample.std() + 1e-8)
        sample = np.clip(sample, -3, 3)  # Clip to reasonable range
        
        calibration_data.append(sample)
    
    logger.info(f"Generated {len(calibration_data)} calibration samples")
    return calibration_data

def get_model_info(model_path: str) -> Dict:
    """Get information about the ONNX model."""
    try:
        model = onnx.load(model_path)
        model_size_mb = os.path.getsize(model_path) / (1024 * 1024)
        
        # Get input/output shapes
        input_shape = None
        output_shape = None
        
        for input_info in model.graph.input:
            if input_info.name == "input":
                input_shape = [dim.dim_value if dim.dim_param == "" else dim.dim_param 
                             for dim in input_info.type.tensor_type.shape.dim]
        
        for output_info in model.graph.output:
            if output_info.name == "output":
                output_shape = [dim.dim_value if dim.dim_param == "" else dim.dim_param 
                              for dim in output_info.type.tensor_type.shape.dim]
        
        return {
            "size_mb": model_size_mb,
            "input_shape": input_shape,
            "output_shape": output_shape,
            "op_count": len(model.graph.node)
        }
    except Exception as e:
        logger.error(f"Error getting model info: {e}")
        return {}

def quantize_dynamic_ecapa(
    input_model_path: str,
    output_model_path: str,
    weight_type: QuantType = QuantType.QUInt8
) -> bool:
    """
    Perform dynamic quantization on ECAPA-TDNN model.
    
    Args:
        input_model_path: Path to input ONNX model
        output_model_path: Path to save quantized model
        weight_type: Quantization type for weights
        optimize_model: Whether to optimize model before quantization
    
    Returns:
        True if successful, False otherwise
    """
    try:
        logger.info("Starting dynamic quantization...")
        
        # Create output directory
        Path(output_model_path).parent.mkdir(parents=True, exist_ok=True)
        
        # Perform dynamic quantization
        quantize_dynamic(
            model_input=input_model_path,
            model_output=output_model_path,
            weight_type=weight_type,
            extra_options={
                "DisableShapeInference": True,
                "ForceQuantizeNoInputCheck": True,
                "MatMulConstBOnly": True
            }
        )
        
        logger.info(f"Dynamic quantization completed: {output_model_path}")
        return True
        
    except Exception as e:
        logger.error(f"Dynamic quantization failed: {e}")
        return False

def quantize_static_ecapa(
    input_model_path: str,
    output_model_path: str,
    calibration_data: List[np.ndarray],
    weight_type: QuantType = QuantType.QUInt8,
    activation_type: QuantType = QuantType.QUInt8
) -> bool:
    """
    Perform static quantization on ECAPA-TDNN model.
    
    Args:
        input_model_path: Path to input ONNX model
        output_model_path: Path to save quantized model
        calibration_data: List of calibration data samples
        weight_type: Quantization type for weights
        activation_type: Quantization type for activations
        optimize_model: Whether to optimize model before quantization
    
    Returns:
        True if successful, False otherwise
    """
    try:
        logger.info("Starting static quantization...")
        
        # Create output directory
        Path(output_model_path).parent.mkdir(parents=True, exist_ok=True)
        
        # Create calibration data reader
        calibration_reader = ECAPACalibrationDataReader(calibration_data)
        
        # Perform static quantization
        quantize_static(
            model_input=input_model_path,
            model_output=output_model_path,
            calibration_data_reader=calibration_reader,
            weight_type=weight_type,
            activation_type=activation_type,
            extra_options={
                "DisableShapeInference": True,
                "ForceQuantizeNoInputCheck": True,
                "MatMulConstBOnly": True
            }
        )
        
        logger.info(f"Static quantization completed: {output_model_path}")
        return True
        
    except Exception as e:
        logger.error(f"Static quantization failed: {e}")
        return False

def test_quantized_model(
    model_path: str,
    test_input: np.ndarray,
    reference_output: Optional[np.ndarray] = None
) -> Dict:
    """
    Test the quantized model and compare with original if reference provided.
    
    Args:
        model_path: Path to quantized model
        test_input: Test input data
        reference_output: Reference output from original model (optional)
    
    Returns:
        Dictionary with test results
    """
    try:
        logger.info(f"Testing quantized model: {model_path}")
        
        # Create inference session
        session = ort.InferenceSession(model_path)
        
        # Run inference
        output = session.run(None, {'input': test_input})[0]
        
        results = {
            "success": True,
            "output_shape": output.shape,
            "output_mean": float(output.mean()),
            "output_std": float(output.std()),
            "inference_time_ms": None
        }
        
        # Compare with reference if provided
        if reference_output is not None:
            mse = np.mean((output - reference_output) ** 2)
            mae = np.mean(np.abs(output - reference_output))
            cosine_sim = np.dot(output.flatten(), reference_output.flatten()) / (
                np.linalg.norm(output.flatten()) * np.linalg.norm(reference_output.flatten())
            )
            
            results.update({
                "mse": float(mse),
                "mae": float(mae),
                "cosine_similarity": float(cosine_sim)
            })
        
        logger.info(f"Test completed successfully")
        return results
        
    except Exception as e:
        logger.error(f"Model test failed: {e}")
        return {"success": False, "error": str(e)}

def benchmark_model_performance(model_path: str, num_runs: int = 100) -> Dict:
    """
    Benchmark model performance with multiple runs.
    
    Args:
        model_path: Path to model
        num_runs: Number of benchmark runs
    
    Returns:
        Dictionary with benchmark results
    """
    try:
        logger.info(f"Benchmarking model: {model_path}")
        
        session = ort.InferenceSession(model_path)
        
        # Create test input
        test_input = np.random.randn(1, 200, 80).astype(np.float32)
        
        # Warm up
        for _ in range(10):
            session.run(None, {'input': test_input})
        
        # Benchmark
        import time
        times = []
        
        for _ in range(num_runs):
            start_time = time.time()
            session.run(None, {'input': test_input})
            end_time = time.time()
            times.append((end_time - start_time) * 1000)  # Convert to ms
        
        avg_time = np.mean(times)
        std_time = np.std(times)
        min_time = np.min(times)
        max_time = np.max(times)
        
        results = {
            "avg_inference_time_ms": float(avg_time),
            "std_inference_time_ms": float(std_time),
            "min_inference_time_ms": float(min_time),
            "max_inference_time_ms": float(max_time),
            "throughput_fps": 1000.0 / avg_time if avg_time > 0 else 0
        }
        
        logger.info(f"Benchmark completed: {avg_time:.2f}ms ± {std_time:.2f}ms")
        return results
        
    except Exception as e:
        logger.error(f"Benchmark failed: {e}")
        return {"error": str(e)}

def main():
    """Main function to quantize ECAPA-TDNN model."""
    logger.info("=" * 70)
    logger.info("AMICA - ECAPA-TDNN ONNX Model Quantization")
    logger.info("=" * 70)
    
    # Paths
    input_model_path = "models/onnx/ecapa_model.onnx"
    dynamic_output_path = "models/onnx/ecapa_model_dynamic_quantized.onnx"
    static_output_path = "models/onnx/ecapa_model_static_quantized.onnx"
    
    # Check if input model exists
    if not os.path.exists(input_model_path):
        logger.error(f"Input model not found: {input_model_path}")
        logger.error("Please run ecapa_to_onnx_pipeline.py first to export the model")
        sys.exit(1)
    
    # Get original model info
    logger.info("Analyzing original model...")
    original_info = get_model_info(input_model_path)
    if original_info:
        logger.info(f"Original model size: {original_info['size_mb']:.1f} MB")
        logger.info(f"Original model ops: {original_info['op_count']}")
    
    # Test original model
    logger.info("Testing original model...")
    test_input = np.random.randn(1, 200, 80).astype(np.float32)
    original_results = test_quantized_model(input_model_path, test_input)
    
    if not original_results["success"]:
        logger.error("Original model test failed")
        sys.exit(1)
    
    original_output = None
    try:
        session = ort.InferenceSession(input_model_path)
        original_output = session.run(None, {'input': test_input})[0]
    except:
        pass
    
    # 1. Dynamic Quantization
    logger.info("\n" + "="*50)
    logger.info("1. DYNAMIC QUANTIZATION")
    logger.info("="*50)
    
    success = quantize_dynamic_ecapa(input_model_path, dynamic_output_path)
    
    if success:
        # Test dynamic quantized model
        dynamic_info = get_model_info(dynamic_output_path)
        dynamic_results = test_quantized_model(dynamic_output_path, test_input, original_output)
        dynamic_benchmark = benchmark_model_performance(dynamic_output_path)
        
        logger.info(f"Dynamic quantized model size: {dynamic_info.get('size_mb', 0):.1f} MB")
        if dynamic_results["success"] and original_info:
            compression_ratio = original_info['size_mb'] / dynamic_info.get('size_mb', 1)
            logger.info(f"Compression ratio: {compression_ratio:.1f}x")
        
        if dynamic_results["success"] and original_output is not None:
            logger.info(f"Accuracy - MSE: {dynamic_results.get('mse', 0):.6f}")
            logger.info(f"Accuracy - Cosine Similarity: {dynamic_results.get('cosine_similarity', 0):.6f}")
        
        if "avg_inference_time_ms" in dynamic_benchmark:
            logger.info(f"Performance: {dynamic_benchmark['avg_inference_time_ms']:.2f}ms")
    
    # 2. Static Quantization
    logger.info("\n" + "="*50)
    logger.info("2. STATIC QUANTIZATION")
    logger.info("="*50)
    
    # Generate calibration data
    calibration_data = generate_calibration_data(num_samples=100)
    
    success = quantize_static_ecapa(
        input_model_path, 
        static_output_path, 
        calibration_data
    )
    
    if success:
        # Test static quantized model
        static_info = get_model_info(static_output_path)
        static_results = test_quantized_model(static_output_path, test_input, original_output)
        static_benchmark = benchmark_model_performance(static_output_path)
        
        logger.info(f"Static quantized model size: {static_info.get('size_mb', 0):.1f} MB")
        if static_results["success"] and original_info:
            compression_ratio = original_info['size_mb'] / static_info.get('size_mb', 1)
            logger.info(f"Compression ratio: {compression_ratio:.1f}x")
        
        if static_results["success"] and original_output is not None:
            logger.info(f"Accuracy - MSE: {static_results.get('mse', 0):.6f}")
            logger.info(f"Accuracy - Cosine Similarity: {static_results.get('cosine_similarity', 0):.6f}")
        
        if "avg_inference_time_ms" in static_benchmark:
            logger.info(f"Performance: {static_benchmark['avg_inference_time_ms']:.2f}ms")
    
    # Summary
    logger.info("\n" + "="*70)
    logger.info("QUANTIZATION SUMMARY")
    logger.info("="*70)
    
    if os.path.exists(dynamic_output_path):
        dynamic_size = os.path.getsize(dynamic_output_path) / (1024 * 1024)
        logger.info(f"✓ Dynamic quantized model: {dynamic_size:.1f} MB")
    
    if os.path.exists(static_output_path):
        static_size = os.path.getsize(static_output_path) / (1024 * 1024)
        logger.info(f"✓ Static quantized model: {static_size:.1f} MB")
    
    logger.info("\nQuantized models are ready for deployment!")
    logger.info("Use the quantized models for improved performance and reduced memory usage.")

if __name__ == "__main__":
    main() 