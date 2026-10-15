$headers = @{ 'User-Agent' = 'Mozilla/5.0' }
$relUrl = 'https://api.github.com/repos/tomeshjain/leadpulse-scanner/releases/tags/wispr-keyboard-v1.4.1'
$rel = Invoke-RestMethod -Uri $relUrl -Headers $headers
Write-Host "Release:" $rel.name
Write-Host "Tag:" $rel.tag_name
Write-Host "Assets:"
$rel.assets | Select-Object name, size, browser_download_url | Format-Table -AutoSize
