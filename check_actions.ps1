$headers = @{ 'User-Agent' = 'Mozilla/5.0' }
$url = 'https://api.github.com/repos/tomeshjain/leadpulse-scanner/actions/runs?per_page=3'
try {
    $res = Invoke-RestMethod -Uri $url -Headers $headers
    $res.workflow_runs | Select-Object id, name, head_branch, head_sha, status, conclusion, created_at, html_url | Format-Table -AutoSize
} catch {
    Write-Host "Error: $_"
}
