$uri = "https://api.github.com/repos/tomeshjain/leadpulse-scanner/actions/runs?per_page=3"
$res = Invoke-RestMethod -Uri $uri -Headers @{ 'User-Agent' = 'Mozilla/5.0' }
foreach ($run in $res.workflow_runs) {
    Write-Host ("Run ID: {0} | Name: {1} | Status: {2} | Conclusion: {3} | SHA: {4}" -f $run.id, $run.name, $run.status, $run.conclusion, $run.head_sha.Substring(0,7))
}
