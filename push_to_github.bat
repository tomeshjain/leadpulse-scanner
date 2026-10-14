@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   LeadPulse - Auto Sync & Android APK Cloud Builder
echo   Repository: https://github.com/tomeshjain/leadpulse-scanner
echo ============================================================
echo.

set GIT="C:\Users\Dell\.gemini\antigravity-ide\scratch\mingit\cmd\git.exe"
if not exist %GIT% (
    set GIT="git"
)

echo [1/3] Staging all files...
%GIT% add -A

echo [2/3] Checking for changes to commit...
%GIT% diff --cached --quiet
if errorlevel 1 (
    echo Committing latest changes...
    %GIT% commit -m "update: Auto-sync LeadPulse codebase and APK workflow"
) else (
    echo No new changes to commit. Local branch is clean.
)

echo.
echo [3/3] Pushing to GitHub (main branch)...
%GIT% push origin main
if errorlevel 1 (
    echo.
    echo [!] Standard push rejected (remote had changes).
    echo Synchronizing with remote history...
    %GIT% pull origin main --rebase
    echo Retrying push...
    %GIT% push origin main
    if errorlevel 1 (
        echo.
        echo [!] Rebase push failed. Syncing local version to remote...
        %GIT% push origin main --force
    )
)

if errorlevel 1 (
    echo.
    echo ============================================================
    echo [ERROR] Push failed. Please check your internet connection.
    echo ============================================================
) else (
    echo.
    echo ============================================================
    echo [SUCCESS] Code successfully pushed to GitHub!
    echo.
    echo 1. Check APK build status in GitHub Actions:
    echo    https://github.com/tomeshjain/leadpulse-scanner/actions
    echo.
    echo 2. Once finished, download LeadPulse.apk from Releases:
    echo    https://github.com/tomeshjain/leadpulse-scanner/releases
    echo ============================================================
)

pause
