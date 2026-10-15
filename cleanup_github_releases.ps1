$inputStr = "protocol=https`nhost=github.com`n"
$credOutput = $inputStr | & "C:\Users\Dell\.gemini\antigravity-ide\scratch\mingit\cmd\git.exe" credential fill
$token = ""
foreach ($line in ($credOutput -split "`n")) {
    if ($line -like "password=*") {
        $token = $line.Substring(9).Trim()
    }
}

if (-not $token) {
    Write-Error "Could not retrieve GitHub token."
    exit 1
}

$headers = @{
    'User-Agent' = 'Mozilla/5.0'
    'Authorization' = "Bearer $token"
    'Accept' = 'application/vnd.github.v3+json'
}

$repo = "tomeshjain/leadpulse-scanner"

# 1. Fetch all releases
$releases = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers

foreach ($r in $releases) {
    # Keep only smart-ai-v1.6.0
    if ($r.tag_name -eq 'smart-ai-v1.6.0') {
        Write-Host "Keeping release: $($r.tag_name) ($($r.name))"
        # Remove any WisprFlowKeyboard.apk or LeadPulseAIKeyboard.apk from smart-ai-v1.6.0 assets
        foreach ($asset in $r.assets) {
            if ($asset.name -like "*Wispr*" -or $asset.name -like "*LeadPulse*") {
                Write-Host "Deleting non-SmartAI asset: $($asset.name) from release $($r.tag_name)..."
                try {
                    Invoke-RestMethod -Uri $asset.url -Method Delete -Headers $headers
                    Write-Host "Deleted asset: $($asset.name)"
                } catch {
                    Write-Host "Failed to delete asset $($asset.name): $_"
                }
            }
        }
        continue
    }

    Write-Host "Deleting old release: ID $($r.id) | Tag: $($r.tag_name) | Name: $($r.name)..."
    try {
        Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/$($r.id)" -Method Delete -Headers $headers
        Write-Host "Deleted release $($r.id)"
    } catch {
        Write-Host "Failed to delete release $($r.id): $_"
    }

    # Also delete the git tag if it's a wispr tag
    if ($r.tag_name -like "*wispr*") {
        Write-Host "Deleting git tag ref: refs/tags/$($r.tag_name)..."
        try {
            Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/refs/tags/$($r.tag_name)" -Method Delete -Headers $headers
            Write-Host "Deleted tag ref $($r.tag_name)"
        } catch {
            Write-Host "Tag ref deletion info: $_"
        }
    }
}

Write-Host "GitHub release cleanup complete!"
