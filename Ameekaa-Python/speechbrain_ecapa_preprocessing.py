#!/usr/bin/env python3
"""
Reference implementation of SpeechBrain ECAPA preprocessing pipeline.
This replicates the exact preprocessing used in SpeechBrain's ECAPA models.

Based on SpeechBrain's Fbank and InputNormalization classes.
Production-ready version with error handling and validation.
"""

import torch
import torch.nn.functional as F
import torchaudio
import numpy as np
import logging
from typing import Optional, Tuple, Union
from pathlib import Path

# Configure logging
logger = logging.getLogger(__name__)

class PreprocessingError(Exception):
    """Custom exception for preprocessing errors."""
    pass

def validate_input(waveform: torch.Tensor, sample_rate: int) -> None:
    """Validate input parameters for preprocessing."""
    if not isinstance(waveform, torch.Tensor):
        raise PreprocessingError(f"waveform must be a torch.Tensor, got {type(waveform)}")
    
    if waveform.dim() == 0:
        raise PreprocessingError("waveform cannot be a scalar tensor")
    
    if waveform.dim() > 2:
        raise PreprocessingError(f"waveform must be 1D or 2D, got {waveform.dim()}D")
    
    if not isinstance(sample_rate, int) or sample_rate <= 0:
        raise PreprocessingError(f"sample_rate must be a positive integer, got {sample_rate}")
    
    if torch.isnan(waveform).any():
        raise PreprocessingError("waveform contains NaN values")
    
    if torch.isinf(waveform).any():
        raise PreprocessingError("waveform contains infinite values")

def hz_to_mel(hz: Union[float, torch.Tensor], mel_scale: str = "htk") -> Union[float, torch.Tensor]:
    """Convert frequency in Hz to mel scale."""
    if mel_scale == "htk":
        return 2595.0 * torch.log10(1.0 + hz / 700.0)
    else:
        raise PreprocessingError(f"Unsupported mel scale: {mel_scale}. Only 'htk' is supported.")

def mel_to_hz(mel: Union[float, torch.Tensor], mel_scale: str = "htk") -> Union[float, torch.Tensor]:
    """Convert mel scale to frequency in Hz."""
    if mel_scale == "htk":
        return 700.0 * (10.0 ** (mel / 2595.0) - 1.0)
    else:
        raise PreprocessingError(f"Unsupported mel scale: {mel_scale}. Only 'htk' is supported.")

def create_filterbank_matrix(
    n_mels: int,
    n_fft: int,
    sample_rate: int,
    f_min: float = 0.0,
    f_max: Optional[float] = None,
    norm: Optional[str] = None,
    mel_scale: str = "htk",
) -> torch.Tensor:
    """Create a complete mel filterbank matrix without skipping filters."""
    try:
        if f_max is None:
            f_max = sample_rate // 2
        
        # Validate parameters
        if n_mels <= 0:
            raise PreprocessingError(f"n_mels must be positive, got {n_mels}")
        if n_fft <= 0:
            raise PreprocessingError(f"n_fft must be positive, got {n_fft}")
        if sample_rate <= 0:
            raise PreprocessingError(f"sample_rate must be positive, got {sample_rate}")
        if f_min < 0:
            raise PreprocessingError(f"f_min must be non-negative, got {f_min}")
        if f_max <= f_min:
            raise PreprocessingError(f"f_max must be greater than f_min, got f_max={f_max}, f_min={f_min}")
        
        # Convert frequencies to mel scale
        mel_min = hz_to_mel(torch.tensor(f_min, dtype=torch.float32), mel_scale=mel_scale)
        mel_max = hz_to_mel(torch.tensor(f_max, dtype=torch.float32), mel_scale=mel_scale)
        
        # Create mel frequencies
        mel_freqs = torch.linspace(mel_min, mel_max, n_mels + 2)
        hz_freqs = mel_to_hz(mel_freqs, mel_scale=mel_scale)
        
        # Convert to FFT bin indices
        bins = torch.floor((n_fft + 1) * hz_freqs / sample_rate).long()
        
        # Ensure bins are within valid range
        bins = torch.clamp(bins, 0, n_fft)
        
        # Create filterbank matrix
        filterbank = torch.zeros(n_mels, n_fft // 2 + 1)
        
        for i in range(n_mels):
            left_bin = bins[i]
            center_bin = bins[i + 1]
            right_bin = bins[i + 2]
            
            # Ensure we have valid ranges
            if left_bin < center_bin:
                # Rising slope
                for j in range(left_bin, center_bin + 1):
                    if j < filterbank.shape[1]:
                        filterbank[i, j] = (j - left_bin) / (center_bin - left_bin + 1e-8)
            
            if center_bin < right_bin:
                # Falling slope
                for j in range(center_bin, right_bin + 1):
                    if j < filterbank.shape[1]:
                        filterbank[i, j] = (right_bin - j) / (right_bin - center_bin + 1e-8)
        
        # Normalize if requested
        if norm == "slaney":
            # Slaney normalization
            enorm = 2.0 / (hz_freqs[2:n_mels + 2] - hz_freqs[:n_mels])
            filterbank *= enorm.unsqueeze(1)
        
        return filterbank
        
    except Exception as e:
        raise PreprocessingError(f"Failed to create filterbank matrix: {str(e)}")

def extract_log_mel_filterbank_features(
    waveform: torch.Tensor,
    sample_rate: int,
    n_mels: int = 80,
    n_fft: int = 400,
    hop_length: int = 160,
    win_length: int = 400,
    window: str = "hann",
    center: bool = True,
    pad_mode: str = "reflect",
    power: float = 2.0,
    norm: Optional[str] = None,
    mel_scale: str = "htk",
    f_min: float = 0.0,
    f_max: Optional[float] = None,
    top_db: float = 80.0,
    log_mel: bool = True,
) -> torch.Tensor:
    """Extract log-mel filterbank features using exact SpeechBrain preprocessing."""
    
    try:
        # Validate input
        validate_input(waveform, sample_rate)
        
        # Ensure waveform is 1D
        if waveform.dim() == 2:
            waveform = waveform.squeeze(0)
        
        # STFT
        stft = torch.stft(
            waveform,
            n_fft=n_fft,
            hop_length=hop_length,
            win_length=win_length,
            window=torch.hann_window(win_length).to(waveform.device),
            center=center,
            pad_mode=pad_mode,
            return_complex=True,
        )
        
        # Power spectrogram
        spec = torch.abs(stft) ** power
        
        # Create filterbank matrix
        filterbank = create_filterbank_matrix(
            n_mels=n_mels,
            n_fft=n_fft,
            sample_rate=sample_rate,
            f_min=f_min,
            f_max=f_max,
            norm=norm,
            mel_scale=mel_scale,
        ).to(waveform.device)
        
        # Apply filterbank
        mel_spec = torch.matmul(filterbank, spec)
        
        # Convert to log scale
        if log_mel:
            mel_spec = torch.log(mel_spec + 1e-8)
        
        return mel_spec
        
    except Exception as e:
        raise PreprocessingError(f"Failed to extract log-mel features: {str(e)}")

def compute_deltas(
    features: torch.Tensor,
    win_length: int = 5,
    mode: str = "replicate",
) -> torch.Tensor:
    """Compute delta features."""
    try:
        if features.dim() != 2:
            raise PreprocessingError(f"features must be 2D, got {features.dim()}D")
        
        if win_length <= 0 or win_length % 2 == 0:
            raise PreprocessingError(f"win_length must be positive and odd, got {win_length}")
        
        # Pad the features
        pad_length = win_length // 2
        if mode == "replicate":
            # Manually replicate padding for 2D tensors
            left = features[:, 0:1].repeat(1, pad_length)
            right = features[:, -1:].repeat(1, pad_length)
            padded = torch.cat([left, features, right], dim=1)
        else:
            padded = F.pad(features, (0, 0, pad_length, pad_length), mode="constant", value=0)
        
        # Prepare for grouped conv1d
        features_exp = padded.unsqueeze(0)  # [1, features, frames]
        n_feats = features_exp.shape[1]
        kernel = torch.arange(-pad_length, pad_length + 1, dtype=torch.float32).repeat(n_feats, 1)  # [features, win_length]
        kernel = kernel.unsqueeze(1)  # [features, 1, win_length]
        kernel = kernel.to(features.device)
        deltas = F.conv1d(features_exp, kernel, padding=0, groups=n_feats).squeeze(0)
        return deltas
        
    except Exception as e:
        raise PreprocessingError(f"Failed to compute deltas: {str(e)}")

def extract_features_with_deltas(
    waveform: torch.Tensor,
    sample_rate: int,
    n_mels: int = 80,
    n_fft: int = 400,
    hop_length: int = 160,
    win_length: int = 400,
    window: str = "hann",
    center: bool = True,
    pad_mode: str = "reflect",
    power: float = 2.0,
    norm: Optional[str] = None,
    mel_scale: str = "htk",
    f_min: float = 0.0,
    f_max: Optional[float] = None,
    top_db: float = 80.0,
    log_mel: bool = True,
    delta_order: int = 2,
    delta_win_length: int = 5,
) -> torch.Tensor:
    """Extract features with deltas and double deltas."""
    
    try:
        # Extract base features
        features = extract_log_mel_filterbank_features(
            waveform=waveform,
            sample_rate=sample_rate,
            n_mels=n_mels,
            n_fft=n_fft,
            hop_length=hop_length,
            win_length=win_length,
            window=window,
            center=center,
            pad_mode=pad_mode,
            power=power,
            norm=norm,
            mel_scale=mel_scale,
            f_min=f_min,
            f_max=f_max,
            top_db=top_db,
            log_mel=log_mel,
        )
        
        # Squeeze batch and channel dimensions if present
        while features.dim() > 2:
            features = features.squeeze(0)
        
        # Now features should be [features, frames]
        if features.dim() != 2:
            raise PreprocessingError(f"Expected 2D features after squeezing, got {features.dim()}D")
        
        # Compute deltas
        deltas = compute_deltas(features, win_length=delta_win_length)
        
        # Compute double deltas
        double_deltas = compute_deltas(deltas, win_length=delta_win_length)
        
        # Concatenate features
        all_features = torch.cat([features, deltas, double_deltas], dim=0)
        return all_features
        
    except Exception as e:
        raise PreprocessingError(f"Failed to extract features with deltas: {str(e)}")

def normalize_features(
    features: torch.Tensor,
    mean: Optional[torch.Tensor] = None,
    std: Optional[torch.Tensor] = None,
    norm_type: str = "global",
) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    """Normalize features using mean and std."""
    
    try:
        if features.dim() != 2:
            raise PreprocessingError(f"features must be 2D, got {features.dim()}D")
        
        if norm_type == "global":
            # Use provided mean and std (global statistics)
            if mean is None or std is None:
                raise PreprocessingError("Global normalization requires mean and std")
            
            if mean.dim() != 1 or std.dim() != 1:
                raise PreprocessingError("mean and std must be 1D tensors")
            
            if mean.shape[0] != features.shape[0] or std.shape[0] != features.shape[0]:
                raise PreprocessingError(f"mean/std shape mismatch: mean={mean.shape}, std={std.shape}, features={features.shape}")
            
            normalized = (features - mean.unsqueeze(1)) / (std.unsqueeze(1) + 1e-8)
            return normalized, mean, std
        
        elif norm_type == "utterance":
            # Per-utterance normalization
            mean = features.mean(dim=1, keepdim=True)
            std = features.std(dim=1, keepdim=True)
            normalized = (features - mean) / (std + 1e-8)
            return normalized, mean.squeeze(1), std.squeeze(1)
        
        else:
            raise PreprocessingError(f"Unknown normalization type: {norm_type}")
            
    except Exception as e:
        raise PreprocessingError(f"Failed to normalize features: {str(e)}")

def speechbrain_ecapa_preprocessing(
    waveform: torch.Tensor,
    sample_rate: int,
    mean: Optional[torch.Tensor] = None,
    std: Optional[torch.Tensor] = None,
    norm_type: str = "global",
) -> torch.Tensor:
    """Complete SpeechBrain ECAPA preprocessing pipeline."""
    
    try:
        logger.debug(f"Starting ECAPA preprocessing: waveform shape={waveform.shape}, sr={sample_rate}")
        
        # Extract features with deltas
        features = extract_features_with_deltas(
            waveform=waveform,
            sample_rate=sample_rate,
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
            delta_order=2,
            delta_win_length=5,
        )
        
        # Normalize features
        normalized_features, computed_mean, computed_std = normalize_features(
            features=features,
            mean=mean,
            std=std,
            norm_type=norm_type,
        )
        
        logger.debug(f"ECAPA preprocessing completed: output shape={normalized_features.shape}")
        return normalized_features
        
    except Exception as e:
        raise PreprocessingError(f"ECAPA preprocessing failed: {str(e)}")

# Convenience function for log-mel only (used by diarization script)
def extract_log_mel_filterbank_features_simple(
    waveform: torch.Tensor,
    sample_rate: int,
    n_mels: int = 80,
    n_fft: int = 400,
    hop_length: int = 160,
    win_length: int = 400,
    window: str = "hann",
    center: bool = True,
    pad_mode: str = "reflect",
    power: float = 2.0,
    norm: Optional[str] = None,
    mel_scale: str = "htk",
    f_min: float = 0.0,
    f_max: Optional[float] = None,
    top_db: float = 80.0,
    log_mel: bool = True,
) -> torch.Tensor:
    """Extract log-mel filterbank features (no deltas) for simple preprocessing."""
    return extract_log_mel_filterbank_features(
        waveform=waveform,
        sample_rate=sample_rate,
        n_mels=n_mels,
        n_fft=n_fft,
        hop_length=hop_length,
        win_length=win_length,
        window=window,
        center=center,
        pad_mode=pad_mode,
        power=power,
        norm=norm,
        mel_scale=mel_scale,
        f_min=f_min,
        f_max=f_max,
        top_db=top_db,
        log_mel=log_mel,
    ) 