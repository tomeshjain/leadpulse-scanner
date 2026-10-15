Add-Type -AssemblyName System.Drawing

$srcSlate = 'C:\Users\Dell\.gemini\antigravity-ide\brain\2411c2a6-361e-4a47-afa6-50b50b501233\smart_ai_keyboard_app_icon_1792079875891.jpg'
$srcLight = 'C:\Users\Dell\.gemini\antigravity-ide\brain\2411c2a6-361e-4a47-afa6-50b50b501233\smart_ai_keyboard_light_icon_1792079897507.jpg'
$srcOled  = 'C:\Users\Dell\.gemini\antigravity-ide\brain\2411c2a6-361e-4a47-afa6-50b50b501233\smart_ai_keyboard_oled_icon_1792079920247.jpg'
$srcCyber = 'C:\Users\Dell\.gemini\antigravity-ide\brain\2411c2a6-361e-4a47-afa6-50b50b501233\smart_ai_keyboard_cyberpunk_icon_1792079940040.jpg'

function Save-Resized-Icon {
    param(
        [System.Drawing.Bitmap]$source,
        [int]$size,
        [string]$destination
    )
    $destBmp = New-Object System.Drawing.Bitmap $size, $size
    $graphics = [System.Drawing.Graphics]::FromImage($destBmp)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.DrawImage($source, 0, 0, $size, $size)
    $graphics.Dispose()
    
    $parent = Split-Path -Parent $destination
    if (-not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    
    $destBmp.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
    $destBmp.Dispose()
    Write-Host "Saved: $destination ($size x $size)"
}

$bmpSlate = [System.Drawing.Bitmap]::FromFile($srcSlate)
$bmpLight = [System.Drawing.Bitmap]::FromFile($srcLight)
$bmpOled  = [System.Drawing.Bitmap]::FromFile($srcOled)
$bmpCyber = [System.Drawing.Bitmap]::FromFile($srcCyber)

$kbRes = 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\wispr-flow-keyboard\app\src\main\res'
$densities = @{
    'mipmap-mdpi' = 48
    'mipmap-hdpi' = 72
    'mipmap-xhdpi' = 96
    'mipmap-xxhdpi' = 144
    'mipmap-xxxhdpi' = 192
}

# 1. Launcher icons (Slate primary)
foreach ($d in $densities.Keys) {
    $size = $densities[$d]
    $dir = Join-Path $kbRes $d
    Save-Resized-Icon -source $bmpSlate -size $size -destination (Join-Path $dir 'ic_launcher.png')
    Save-Resized-Icon -source $bmpSlate -size $size -destination (Join-Path $dir 'ic_launcher_round.png')
}

# 2. Save high-res drawables for all 4 theme variants
Save-Resized-Icon -source $bmpSlate -size 512 -destination (Join-Path $kbRes 'drawable\smart_ai_logo.png')
Save-Resized-Icon -source $bmpLight -size 512 -destination (Join-Path $kbRes 'drawable\smart_ai_logo_light.png')
Save-Resized-Icon -source $bmpOled  -size 512 -destination (Join-Path $kbRes 'drawable\smart_ai_logo_oled.png')
Save-Resized-Icon -source $bmpCyber -size 512 -destination (Join-Path $kbRes 'drawable\smart_ai_logo_cyberpunk.png')

# 3. Update android app wrapper if present
$androidRes = 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\res'
if (Test-Path $androidRes) {
    foreach ($d in $densities.Keys) {
        $size = $densities[$d]
        $dir = Join-Path $androidRes $d
        Save-Resized-Icon -source $bmpSlate -size $size -destination (Join-Path $dir 'ic_launcher.png')
        Save-Resized-Icon -source $bmpSlate -size $size -destination (Join-Path $dir 'ic_launcher_round.png')
    }
}

# 4. Web assets
Save-Resized-Icon -source $bmpSlate -size 192 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icons\icon-192.png'
Save-Resized-Icon -source $bmpSlate -size 512 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icons\icon-512.png'
Save-Resized-Icon -source $bmpSlate -size 192 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\assets\icon-192.png'
Save-Resized-Icon -source $bmpSlate -size 512 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\assets\icon-512.png'

$bmpSlate.Dispose()
$bmpLight.Dispose()
$bmpOled.Dispose()
$bmpCyber.Dispose()

Write-Host "All theme-adaptive clean keyboard AI icons generated successfully!"
