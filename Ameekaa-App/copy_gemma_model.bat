@echo off
setlocal enabledelayedexpansion
echo Simple Gemma Model Copy to Emulator
echo ===================================

set "ADB_PATH=%CD%\adb-tools\adb.exe"
set "MODEL_FILE=gemma-3n-E2B-it-int4.task"
set "TARGET_PATH=/storage/emulated/0/Android/data/com.ameekaa.app/files/Download/"

echo.
echo Step 1: Checking if model file exists...
if not exist "%MODEL_FILE%" (
    echo ERROR: %MODEL_FILE% not found in current directory
    echo Please place your Gemma model file here first.
    pause
    exit /b 1
)
echo ✅ Model file found: %MODEL_FILE%

echo.
echo Step 2: Starting ADB server...
"%ADB_PATH%" start-server >nul 2>&1

echo.
echo Step 3: Waiting for emulator...
:wait_for_emulator
"%ADB_PATH%" shell echo "ready" >nul 2>&1
if errorlevel 1 (
    echo Waiting for emulator to be ready...
    timeout /t 5 /nobreak >nul
    goto wait_for_emulator
)
echo ✅ Emulator is ready

echo.
echo Step 4: Creating target directory...
"%ADB_PATH%" shell "mkdir -p %TARGET_PATH%" >nul 2>&1

echo.
echo Step 5: Copying model file (this may take 2-3 minutes for ~3GB file)...
echo From: %MODEL_FILE%
echo To: %TARGET_PATH%%MODEL_FILE%
echo.
"%ADB_PATH%" push "%MODEL_FILE%" "%TARGET_PATH%%MODEL_FILE%"

if errorlevel 1 (
    echo.
    echo ❌ Copy failed. Trying alternative location...
    set "ALT_PATH=/storage/emulated/0/Android/data/com.ameekaa.app/files/"
    echo To: !ALT_PATH!%MODEL_FILE%
    "%ADB_PATH%" push "%MODEL_FILE%" "!ALT_PATH!%MODEL_FILE%"
    
    if errorlevel 1 (
        echo ❌ Copy failed to both locations
        pause
        exit /b 1
    ) else (
        echo ✅ Model copied to alternative location
        set "FINAL_PATH=!ALT_PATH!"
    )
) else (
    echo ✅ Model copied successfully
    set "FINAL_PATH=%TARGET_PATH%"
)

echo.
echo Step 6: Verifying copy...
"%ADB_PATH%" shell "ls -la %FINAL_PATH%%MODEL_FILE%" 2>nul
if errorlevel 1 (
    "%ADB_PATH%" shell "ls -la /storage/emulated/0/Android/data/com.ameekaa.app/files/%MODEL_FILE%" 2>nul
)

echo.
echo ========================================
echo ✅ SUCCESS: Model ready for Ameekaa app!
echo ========================================
echo.
echo The Gemma model is now available at:
echo %FINAL_PATH%%MODEL_FILE%
echo.
echo You can now:
echo 1. Open Ameekaa app
echo 2. Go to Sentiment Analysis
echo 3. The app will detect the model automatically
echo.
pause 