$runId = 38670641982
$uri = "https://api.github.com/repos/tomeshjain/leadpulse-scanner/actions/runs/$runId/jobs"
$res = Invoke-RestMethod -Uri $uri -Headers @{ 'User-Agent' = 'Mozilla/5.0' }
foreach ($job in $res.jobs) {
    Write-Host ("Job: {0} | Status: {1} | Conclusion: {2}" -f $job.name, $job.status, $job.conclusion)
    foreach ($step in $job.steps) {
        Write-Host ("  Step: {0} | Status: {1} | Conclusion: {2}" -f $step.name, $step.status, $step.conclusion)
    }
}
