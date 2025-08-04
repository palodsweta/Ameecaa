#!/usr/bin/env python3
"""
Export SpeechBrain ECAPA-TDNN embedding model to ONNX with correct input format.
Input: [batch, features, frames] = [1, 80, frames] from base model's preprocessing.
"""
import torch
import logging
import sys
from pathlib import Path
from speechbrain.inference.speaker import EncoderClassifier

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('model_export.log')
    ]
)
logger = logging.getLogger(__name__)

def export_ecapa_to_onnx_corrected():
    """Export SpeechBrain ECAPA-TDNN model to ONNX format."""
    try:
        logger.info("Starting ECAPA-TDNN model export to ONNX...")
        
        # Load the SpeechBrain model
        logger.info("Loading SpeechBrain ECAPA-TDNN model...")
        model = EncoderClassifier.from_hparams(
            source="speechbrain/spkrec-ecapa-voxceleb",
            savedir="models/speechbrain_ecapa"
        )
        
        # Set model to evaluation mode
        model.eval()
        
        # Create output directory
        onnx_path = Path("models/onnx/ecapa_model.onnx")
        onnx_path.parent.mkdir(parents=True, exist_ok=True)
        
        # Create dummy input that matches base model's preprocessing output
        # Base model preprocessing produces [batch, features, frames] = [1, 80, frames]
        # But ECAPA model expects [batch, frames, features] = [1, frames, 80]
        dummy_input = torch.randn(1, 200, 80)  # [batch, frames, features]
        logger.info(f"Dummy input shape for export: {dummy_input.shape}")
        
        # Export to ONNX
        logger.info("Exporting model to ONNX format...")
        torch.onnx.export(
            model.mods.embedding_model,
            dummy_input,
            str(onnx_path),
            export_params=True,
            opset_version=17,
            do_constant_folding=True,
            input_names=['input'],
            output_names=['output'],
            dynamic_axes={
                'input': {1: 'frames'}, 
                'output': {1: 'frames'}
            },  # frames dimension is dynamic
            verbose=False
        )
        
        # Verify the exported model
        logger.info("Verifying exported ONNX model...")
        import onnx
        onnx_model = onnx.load(str(onnx_path))
        onnx.checker.check_model(onnx_model)
        
        # Get model size
        model_size_mb = onnx_path.stat().st_size / (1024 * 1024)
        logger.info(f"[SUCCESS] Successfully exported to {onnx_path}")
        logger.info(f"[SUCCESS] Model size: {model_size_mb:.1f} MB")
        logger.info(f"[SUCCESS] Model input shape: {onnx_model.graph.input[0].type.tensor_type.shape}")
        logger.info(f"[SUCCESS] Model output shape: {onnx_model.graph.output[0].type.tensor_type.shape}")
        
        return True
        
    except ImportError as e:
        logger.error(f"Import error: {e}")
        logger.error("Please install required dependencies: pip install speechbrain torch onnx")
        return False
    except Exception as e:
        logger.error(f"Export failed: {e}")
        return False

def test_onnx_model():
    """Test the exported ONNX model with sample input."""
    try:
        logger.info("Testing exported ONNX model...")
        import onnxruntime as ort
        import numpy as np
        
        # Load ONNX model
        session = ort.InferenceSession("models/onnx/ecapa_model.onnx")
        
        # Create test input
        test_input = np.random.randn(1, 100, 80).astype(np.float32)
        
        # Run inference
        output = session.run(None, {'input': test_input})[0]
        
        logger.info(f"[SUCCESS] Test input shape: {test_input.shape}")
        logger.info(f"[SUCCESS] Test output shape: {output.shape}")
        logger.info(f"[SUCCESS] Test output mean: {output.mean():.4f}")
        logger.info(f"[SUCCESS] Test output std: {output.std():.4f}")
        
        return True
        
    except Exception as e:
        logger.error(f"ONNX model test failed: {e}")
        return False

def main():
    """Main function to export and test the ONNX model."""
    logger.info("=" * 60)
    logger.info("AMICA - ECAPA-TDNN ONNX Model Export")
    logger.info("=" * 60)
    
    # Export model
    success = export_ecapa_to_onnx_corrected()
    
    if success:
        # Test the exported model
        test_success = test_onnx_model()
        
        if test_success:
            logger.info("=" * 60)
            logger.info("[SUCCESS] Model export and test completed successfully!")
            logger.info("[SUCCESS] Model is ready for mobile deployment")
            logger.info("=" * 60)
        else:
            logger.error("✗ Model export succeeded but test failed")
            sys.exit(1)
    else:
        logger.error("✗ Model export failed")
        sys.exit(1)

if __name__ == "__main__":
    main() 