# Ameekaa App

An Android application that uses AI to provide companionship and emotional support through natural conversations and emotional analysis.

## Features
- Voice interaction and audio processing
- Emotion and sentiment analysis
- Nudge generation

## Prerequisites
- Android Studio Arctic Fox or newer
- Android SDK 34
- JDK 8 or newer
- Android device or emulator running Android 7.0 (API 24) or higher
- At least 4GB of free storage space for model files

## Setup

1. Clone the repository:
```bash
git clone [repository-url]
cd Ameekaa-App
```

2. Install ADB (Android Debug Bridge):
```bash
./install_adb.bat
```

3. Copy the Gemma model:
```bash
./copy_gemma_model.bat
```

4. Open the project in Android Studio and sync Gradle files

5. Configure your device:
```bash
./start_emulator_with_network.bat
```

## Building and Running

1. Build the project:
```bash
./gradlew build
```

2. Install on device:
```bash
./gradlew installDebug
```

## Project Structure

- `app/` - Main application module
- `test_data/` - Test data for development
- `adb-tools/` - ADB utility scripts
- `gradle/` - Gradle configuration files

## Utility Scripts

- `get_output_file.bat` - Retrieve output files from device
- `copy_gemma_model.bat` - Copy AI model to device
- `add_files_to_emulator.bat` - Add test files to emulator
- `start_emulator_with_network.bat` - Start emulator with network configuration
- `view_logs.bat` - View application logs

## Dependencies

The app uses several key dependencies:

- AndroidX Core and AppCompat libraries
- Google Material Design components
- ONNX Runtime for model inference
- MediaPipe GenAI for LLM inference
- JTransforms for audio processing
- Kotlin Coroutines for async operations
- DataStore for data persistence
- Kotlinx Serialization for JSON handling

For a complete list of dependencies and versions, see `requirements.txt`.

## Development
The application is built using:
- Kotlin for Android development

## License

[Add your license information here]

## Contributing

## Acknowledgments
- SpeechBrain for ECAPA-TDNN model
- Google's Gemma model for emotion analysis
- ONNX Runtime for model optimization
- Cursor and Claude-Sonnet for code generation 
