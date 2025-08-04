@echo off
echo ==========================================
echo  Get Ameekaa Output File
echo ==========================================
echo.

set "ADB=C:\Users\sweta\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo Checking for output file in app external directory...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell ls -la "/storage/emulated/0/Android/data/com.ameekaa.app/files/Download/"

echo.
echo Copying file to current directory...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" pull "/storage/emulated/0/Android/data/com.ameekaa.app/files/Download/user4_dynamic_segments.wav" .

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ Successfully copied user4_dynamic_segments.wav to current directory!
    echo File size:
    dir user4_dynamic_segments.wav
) else (
    echo.
    echo ❌ Failed to copy file. Make sure:
    echo 1. You've run the embedded test successfully
    echo 2. The app has copied the file to Download directory
    echo 3. The emulator is still connected
    echo.
    echo Alternative: Run the test again to create a fresh copy
)

pause 