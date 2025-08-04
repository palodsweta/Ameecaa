@echo off
echo ========================================
echo Add Audio Files to Emulator Downloads
echo ========================================
echo.
echo Simple script to upload audio files to emulator Downloads folder
echo for testing Ameekaa app file upload functionality.
echo.

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo ❌ Error: ADB not found at %ADB%
    echo Please make sure Android SDK is installed.
    pause
    exit /b 1
)

echo Checking emulator connection...
"%ADB%" devices

echo.
echo Available options:
echo.
echo 1. Push a WAV file to emulator Downloads folder
echo 2. Push multiple audio files to Downloads folder
echo.

set /p choice="Enter your choice (1-2): "

if "%choice%"=="1" goto push_single
if "%choice%"=="2" goto push_multiple

echo Invalid choice. Exiting.
pause
exit /b 1

:push_single
echo.
set /p filepath="Enter full path to WAV file (drag and drop here): "
if not exist "%filepath%" (
    echo ❌ File not found: %filepath%
    pause
    exit /b 1
)

echo Pushing file to emulator Downloads...
"%ADB%" push "%filepath%" "/storage/emulated/0/Download/"
if %ERRORLEVEL% EQU 0 (
    echo ✅ File successfully uploaded to emulator Downloads folder
    echo You can now browse and select it in the app!
) else (
    echo ❌ Failed to push file
)
goto end

:push_multiple
echo.
echo Drag and drop multiple audio files here (press Enter when done):
set /p files="Files: "
for %%f in (%files%) do (
    if exist "%%f" (
        echo Pushing %%f...
        "%ADB%" push "%%f" "/storage/emulated/0/Download/"
    ) else (
        echo ⚠️  File not found: %%f
    )
)
echo ✅ Upload complete!
goto end

:end
echo.
echo ========================================
echo Next Steps:
echo ========================================
echo 1. Open the Ameekaa app on emulator
echo 2. Click "Select Speaker Enrollment Audio" or "Select Meeting Audio"
echo 3. In the file picker, navigate to Downloads folder
echo 4. Select your uploaded audio file and test!
echo.
echo 📁 Files are stored in: /storage/emulated/0/Download/
echo 🎵 Supported formats: WAV, MP3, FLAC, AAC, OGG
echo.
pause 