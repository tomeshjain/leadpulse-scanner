$headers = @{ 'User-Agent' = 'Mozilla/5.0' }
$url = 'https://api.github.com/repos/tomeshjain/leadpulse-scanner/actions/runs?per_page=2'

for ($i = 1; $i -le 16; $i++) {
    Start-Sleep -Seconds 15
    try {
        $res = Invoke-RestMethod -Uri $url -Headers $headers
        $latest = $res.workflow_runs | Where-Object { $_.name -eq 'Build Android APK' } | Select-Object -First 1
        Write-Host "[$i] Status: $($latest.status), Conclusion: $($latest.conclusion), SHA: $($latest.head_sha.Substring(0,7))"
        if ($latest.status -eq 'completed') {
            Write-Host "Workflow completed with conclusion: $($latest.conclusion)"
            break
        }
    } catch {
        Write-Host "Poll error: $_"
    }
}
