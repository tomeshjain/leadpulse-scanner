$git = "C:\Users\Dell\.gemini\antigravity-ide\scratch\mingit\cmd\git.exe"
if (-not (Test-Path $git)) { $git = "git" }

Set-Location "C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app"
Write-Host "--- Git Status before add ---"
& $git status -s

Write-Host "--- Staging files ---"
& $git add -A

Write-Host "--- Committing changes ---"
& $git commit -m "feat(keyboard): v1.3.1 - Auto camera card scanner, voice note attachment, save contact to phone & send, return to caller fix"

Write-Host "--- Pushing to GitHub main ---"
& $git push origin main
Write-Host "--- Done! ---"
