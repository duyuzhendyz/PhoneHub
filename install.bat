@echo off
set PATH=c:\PhoneHub\android-sdk\platform-tools;%PATH%
echo Uninstalling old version...
adb uninstall com.phonehub
echo Installing new APK...
adb install c:\PhoneHub\app\build\outputs\apk\debug\app-debug.apk
echo Done.
