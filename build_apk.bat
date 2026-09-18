@echo off
echo Building Ghostly APK...
"%USERPROFILE%\.gradle\wrapper\dists\gradle-8.0\bin\gradle.bat" assembleDebug
if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================================
    echo SUCCESS! Ghostly APK built successfully at:
    echo build\outputs\apk\debug\Ghostly-debug.apk
    echo ========================================================
) else (
    echo.
    echo Build failed. Check the error log above.
)
pause
