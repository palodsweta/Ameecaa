@echo off
echo ==========================================
echo  Amica App - Log Viewer
echo ==========================================
echo.
echo Choose log view option:
echo.
echo 1. MainActivity logs only (recommended)
echo 2. All Amica app logs
echo 3. Error logs only
echo 4. Clear log buffer and start fresh
echo.
set /p choice="Enter choice (1-4): "

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if "%choice%"=="1" (
    echo.
    echo Showing MainActivity logs... (Press Ctrl+C to stop)
    echo.
    %ADB% logcat | findstr MainActivity
) else if "%choice%"=="2" (
    echo.
    echo Showing all Amica app logs... (Press Ctrl+C to stop)
    echo.
    %ADB% logcat | findstr com.amica.app
) else if "%choice%"=="3" (
    echo.
    echo Showing error logs only... (Press Ctrl+C to stop)
    echo.
    %ADB% logcat *:E | findstr com.amica.app
) else if "%choice%"=="4" (
    echo.
    echo Clearing log buffer...
    %ADB% logcat -c
    echo Log buffer cleared!
    echo.
    echo Now showing fresh MainActivity logs... (Press Ctrl+C to stop)
    echo.
    %ADB% logcat | findstr MainActivity
) else (
    echo Invalid choice. Please run again and choose 1-4.
)

pause 