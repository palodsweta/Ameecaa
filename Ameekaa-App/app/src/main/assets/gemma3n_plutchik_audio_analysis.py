#!/usr/bin/env python3
"""
Analyze user audio for Plutchik's Wheel of Emotions using google/gemma-3n-E2B-it (no transcription).
Sends the audio file directly to the model with a prompt.
"""
import torch
from transformers import AutoProcessor, AutoModelForImageTextToText
from datetime import datetime
import argparse

GEMMA_MODEL_ID = "google/gemma-3n-E2B-it"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--audio', required=True, help='Path to user audio file (wav)')
    args = parser.parse_args()
    audio_path = args.audio

    start = datetime.now()
    print("Started", start)
    processor = AutoProcessor.from_pretrained(GEMMA_MODEL_ID)
    print("Processor loaded", datetime.now())
    model = AutoModelForImageTextToText.from_pretrained(
        GEMMA_MODEL_ID, torch_dtype="auto", device_map=None)
    print("Model loaded", datetime.now())

    # Compose the prompt for emotion analysis
    prompt = (
        "You are Amica — a hyper-personalized emotional wellness companion trained to support users by "
        "listening compassionately and intelligently. You are given an audio recording of a user's voice. "
        "Your task is to analyze the emotional and cognitive patterns in that audio and respond with 3 outputs "
        "in the following structured JSON format:\n\n"
        "{\n"
        '    "task_1_emotion_sentiment": {\n'
        '        "primary_emotion": "<one of: calm, joy, sadness, anger, fear, guilt, shame, anxiety, tired, numb, overwhelmed>",\n'
        '        "sentiment": "<one of: positive, neutral, negative>",\n'
        '        "confidence_score": "<float between 0 and 1>"\n'
        "    },\n"
        '    "task_2_negative_spiral_detection": {\n'
        '        "is_negative_spiral_detected": "<true/false>",\n'
        '        "detected_triggers": ["<low self-worth>", "<relationship stress>", "<burnout>"],\n'
        '        "reasoning": "<brief natural language explanation for why this was flagged>"\n'
        "    },\n"
        '    "task_3_topic_keyword_extraction": {\n'
        '        "key_themes": ["<emotion regulation>", "<family conflict>", "<career burnout>"],\n'
        '        "important_keywords": ["<keyword1>", "<keyword2>", "<keyword3>"]\n'
        "    }\n"
        "}\n\n"
        "Please analyze the audio and provide a complete analysis following this exact format. "
        "Make sure to detect emotional patterns, potential negative thought spirals, and key themes from the audio."
    )

    messages = [
        {
            "role": "user",
            "content": [
                {"type": "audio", "audio": audio_path},
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
    print("Input ids processed", datetime.now())
    input_ids = input_ids.to(model.device, dtype=model.dtype)
    print("Input ids mapped to device", datetime.now())
    outputs = model.generate(
        **input_ids,
        max_new_tokens=256,  # Increased for longer response
        temperature=0.7,     # Added for better response variety
        do_sample=True      # Enable sampling
    )
    print("Output generated", datetime.now())
    text = processor.batch_decode(
        outputs,
        skip_special_tokens=True,  # Changed to True for cleaner output
        clean_up_tokenization_spaces=True
    )
    print("Output decoded", datetime.now())
    print("\n[Amica Emotion Analysis Result]")
    print(text[0])

if __name__ == "__main__":
    main() 