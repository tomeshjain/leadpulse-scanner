$gitExe = 'C:\Users\Dell\.gemini\antigravity-ide\scratch\mingit\cmd\git.exe'
if (-not (Test-Path $gitExe)) {
    $gitExe = 'git'
}

Set-Location -Path $PSScriptRoot

Write-Host "Using Git: $gitExe"
& $gitExe status -s

Write-Host "Adding files..."
& $gitExe add -A

Write-Host "Committing..."
& $gitExe commit -m "feat: LeadPulse AI Keyboard v1.5.0 - complete executive rebranding, strict 6-field card extraction, zero-touch automated contact saving, multi-model fallback cascade, transparent engine badges, Gemini Live WebSocket client, and pure AI diagnostics"

Write-Host "Pushing to origin main..."
& $gitExe push origin main

Write-Host "Done!"
