$sha = "0740b3a"
$uri = "https://api.github.com/repos/tomeshjain/leadpulse-scanner/commits/$sha/comments"
$res = Invoke-RestMethod -Uri $uri -Headers @{ 'User-Agent' = 'Mozilla/5.0' }
foreach ($c in $res) {
    Write-Host "=== COMMENT ==="
    Write-Host $c.body
}
