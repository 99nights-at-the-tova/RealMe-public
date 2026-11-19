@echo off
rem RealMe: take a device screenshot (double-click).
rem Use this to test the app's screenshot detection.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0take-screenshot.ps1" %*
echo.
pause
