@echo off
echo Starting Amica emulator with network connectivity fixes and adequate storage...

set "ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk"
set "EMULATOR_PATH=%ANDROID_HOME%\emulator"
set "AVD_NAME=Pixel_9_Pro"

echo.
echo Using Android SDK: %ANDROID_HOME%
echo Emulator path: %EMULATOR_PATH%
echo AVD: %AVD_NAME%

REM Check if emulator exists
if not exist "%EMULATOR_PATH%\emulator.exe" (
    echo ERROR: Android emulator not found at %EMULATOR_PATH%
    echo Please install Android SDK and emulator tools
    pause
    exit /b 1
)

REM Check if AVD exists
"%EMULATOR_PATH%\emulator.exe" -list-avds | findstr /C:"%AVD_NAME%" >nul
if errorlevel 1 (
    echo ERROR: AVD '%AVD_NAME%' not found
    echo Available AVDs:
    "%EMULATOR_PATH%\emulator.exe" -list-avds
    pause
    exit /b 1
)

echo.
echo Starting emulator with optimized configuration:
echo - DNS servers: 8.8.8.8, 8.8.4.4
echo - Network latency: none
echo - Network speed: full
echo - Internal storage: 2GB (max supported)
echo - RAM: 4GB
echo.

REM Start emulator with network configuration and adequate storage
"%EMULATOR_PATH%\emulator.exe" ^
    -avd "%AVD_NAME%" ^
    -dns-server 8.8.8.8,8.8.4.4 ^
    -netdelay none ^
    -netspeed full ^
    -partition-size 2047 ^
    -memory 4096 ^
    -no-snapshot-load

echo.
echo Emulator startup command completed.
pause 