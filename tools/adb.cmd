@echo off
rem Project-local adb wrapper so you do not have to add platform-tools to PATH.
rem Resolution order: %ADB% -> %ANDROID_HOME% -> %ANDROID_SDK_ROOT% -> %LOCALAPPDATA%\Android\Sdk -> PATH
setlocal

if defined ADB if exist "%ADB%" (
    "%ADB%" %*
    exit /b %ERRORLEVEL%
)

if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    "%ANDROID_HOME%\platform-tools\adb.exe" %*
    exit /b %ERRORLEVEL%
)

if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" (
    "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" %*
    exit /b %ERRORLEVEL%
)

if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" %*
    exit /b %ERRORLEVEL%
)

where adb >nul 2>nul
if %ERRORLEVEL%==0 (
    adb %*
    exit /b %ERRORLEVEL%
)

echo [adb.cmd] adb not found. 1>&2
echo   Set ADB, or ANDROID_HOME, or install Android SDK platform-tools. 1>&2
exit /b 1
