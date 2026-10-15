$headers = @{ 'User-Agent' = 'Mozilla/5.0' }
$url = 'https://api.github.com/repos/tomeshjain/leadpulse-scanner/releases'
try {
    $releases = Invoke-RestMethod -Uri $url -Headers $headers
    Write-Host "Found $($releases.Count) releases on GitHub:"
    foreach ($r in $releases) {
        Write-Host "ID: $($r.id) | Tag: $($r.tag_name) | Name: $($r.name) | Published: $($r.published_at)"
    }
} catch {
    Write-Host "Error fetching releases: $_"
}
