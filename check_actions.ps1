$headers = @{ 'User-Agent' = 'Mozilla/5.0' }
$url = 'https://api.github.com/repos/tomeshjain/leadpulse-scanner/actions/runs?per_page=3'
try {
    $res = Invoke-RestMethod -Uri $url -Headers $headers
    $res.workflow_runs | Select-Object id, name, head_branch, head_sha, status, conclusion, created_at, html_url | Format-Table -AutoSize

    $targetRun = $res.workflow_runs | Where-Object { $_.name -like '*Keyboard*' } | Select-Object -First 1
    if ($targetRun) {
        Write-Host "Target Run: $($targetRun.name) ID: $($targetRun.id) Status: $($targetRun.status) Conclusion: $($targetRun.conclusion)"
        $jobsUrl = "https://api.github.com/repos/tomeshjain/leadpulse-scanner/actions/runs/$($targetRun.id)/jobs"
        $jobsRes = Invoke-RestMethod -Uri $jobsUrl -Headers $headers
        foreach ($j in $jobsRes.jobs) {
            Write-Host "Job: $($j.name) | Status: $($j.status) | Conclusion: $($j.conclusion)"
            $j.steps | Select-Object name, status, conclusion | Format-Table -AutoSize
        }
    }
} catch {
    Write-Host "Error: $_"
}
