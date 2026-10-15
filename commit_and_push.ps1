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
& $gitExe commit -m "feat: True GIF image insertion via commitContent, live and voice GIF search, contextual text-to-GIF suggestions, and expanded curated reaction catalog"

Write-Host "Pushing to origin main..."
& $gitExe push origin main

Write-Host "Done!"
