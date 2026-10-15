$headers = @{ 'User-Agent' = 'Mozilla/5.0' }
$runId = "38741204262"
$jobsUrl = "https://api.github.com/repos/tomeshjain/leadpulse-scanner/actions/runs/$runId/jobs"
$jobs = (Invoke-RestMethod -Uri $jobsUrl -Headers $headers).jobs

foreach ($j in $jobs) {
    Write-Host "Job: $($j.name) Status: $($j.status) Conclusion: $($j.conclusion)"
    $j.steps | Select-Object name, status, conclusion | Format-Table -AutoSize
}

# Check commit comment if posted
$commentsUrl = "https://api.github.com/repos/tomeshjain/leadpulse-scanner/commits/badfeecfa68840d7950fce803244c54085393ce2/comments"
try {
    $comments = Invoke-RestMethod -Uri $commentsUrl -Headers $headers
    foreach ($c in $comments) {
        Write-Host "=== Commit Comment ==="
        Write-Host $c.body
    }
} catch {
    Write-Host "No comments or error fetching comments: $_"
}
