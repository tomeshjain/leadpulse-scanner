$git = "C:\Users\Dell\.gemini\antigravity-ide\scratch\mingit\cmd\git.exe"

& $git branch -M main
& $git add -A
& $git status
& $git commit -m "feat: Add dynamic Gemini model selection, self-healing fallbacks, sales qualification metrics, PNG WebAPK icons and GitHub Actions APK build workflow"

Write-Host "Attempting push to origin main..."
& $git push -u origin main
