@echo off
echo Installing ADB Platform Tools...
echo ===============================

REM Check if already installed
if exist "adb-tools\adb.exe" (
    echo ADB already installed in adb-tools directory
    adb-tools\adb.exe version
    pause
    exit /b 0
)

echo Creating adb-tools directory...
if not exist "adb-tools" mkdir adb-tools

echo.
echo Downloading Android Platform Tools...
echo This will download about 5MB and extract ADB tools locally.
echo.

REM Download using PowerShell
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile 'adb-tools\platform-tools.zip'}"

if errorlevel 1 (
    echo ERROR: Failed to download platform tools
    echo Please manually download from:
    echo https://developer.android.com/studio/releases/platform-tools
    echo Extract to the adb-tools folder
    pause
    exit /b 1
)

echo Download complete. Extracting...

REM Extract using PowerShell
powershell -Command "& {Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('adb-tools\platform-tools.zip', 'adb-tools\')}"

if errorlevel 1 (
    echo ERROR: Failed to extract platform tools
    echo Please manually extract platform-tools.zip in adb-tools folder
    pause
    exit /b 1
)

REM Move files from platform-tools subfolder to adb-tools
echo Moving ADB files...
move "adb-tools\platform-tools\*" "adb-tools\"
rmdir "adb-tools\platform-tools"
del "adb-tools\platform-tools.zip"

REM Test ADB
if exist "adb-tools\adb.exe" (
    echo.
    echo SUCCESS: ADB installed successfully!
    echo Version:
    adb-tools\adb.exe version
    echo.
    echo You can now run copy_gemma_model.bat
) else (
    echo ERROR: ADB installation failed
    echo Please manually install ADB
)

echo.
pause 