@echo off
set JAVA_HOME=c:\PhoneHub\jdk17
set ANDROID_HOME=c:\PhoneHub\android-sdk
set ANDROID_SDK_ROOT=c:\PhoneHub\android-sdk
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d c:\PhoneHub
echo === Java version ===
"%JAVA_HOME%\bin\java.exe" -version
echo === Starting Gradle assembleDebug (using local gradle 8.9) ===
call "c:\PhoneHub\gradle-dist\gradle-8.9\bin\gradle.bat" assembleDebug --no-daemon --console=plain --offline
echo === Gradle exit code: %ERRORLEVEL% ===
