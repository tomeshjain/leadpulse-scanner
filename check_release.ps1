$headers = @{ 'User-Agent' = 'Mozilla/5.0' }
$url = 'https://api.github.com/repos/tomeshjain/leadpulse-scanner/releases/latest'
try {
    $res = Invoke-RestMethod -Uri $url -Headers $headers
    Write-Host "Tag: $($res.tag_name)"
    Write-Host "Name: $($res.name)"
    Write-Host "URL: $($res.html_url)"
    Write-Host "Assets:"
    foreach ($a in $res.assets) {
        Write-Host " - $($a.name) ($([math]::Round($a.size / 1MB, 2)) MB): $($a.browser_download_url)"
    }
} catch {
    Write-Host "Error: $_"
}
