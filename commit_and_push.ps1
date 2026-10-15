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
& $gitExe commit -m "feat: Add LinkedIn and past work extraction, local ML Kit OCR fallback, Clean Light default theme, dynamic Settings live theming, and bottom-left navigation layout"

Write-Host "Pushing to origin main..."
& $gitExe push origin main

Write-Host "Done!"
