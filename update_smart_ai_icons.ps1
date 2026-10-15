Add-Type -AssemblyName System.Drawing

$src = 'C:\Users\Dell\.gemini\antigravity-ide\brain\2411c2a6-361e-4a47-afa6-50b50b501233\smart_ai_logo_1792074088552.jpg'
if (-not (Test-Path $src)) {
    Write-Error "Source image not found at $src"
    exit 1
}

$bmpSrc = [System.Drawing.Bitmap]::FromFile($src)
Write-Host "Loaded source image: $($bmpSrc.Width)x$($bmpSrc.Height)"

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

# 1. Update wispr-flow-keyboard launcher mipmaps
$kbRes = 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\wispr-flow-keyboard\app\src\main\res'
$densities = @{
    'mipmap-mdpi' = 48
    'mipmap-hdpi' = 72
    'mipmap-xhdpi' = 96
    'mipmap-xxhdpi' = 144
    'mipmap-xxxhdpi' = 192
}

foreach ($d in $densities.Keys) {
    $size = $densities[$d]
    $dir = Join-Path $kbRes $d
    Save-Resized-Icon -source $bmpSrc -size $size -destination (Join-Path $dir 'ic_launcher.png')
    Save-Resized-Icon -source $bmpSrc -size $size -destination (Join-Path $dir 'ic_launcher_round.png')
}

# 2. Save high-res drawable for Settings & About screen
$kbDrawable = Join-Path $kbRes 'drawable\smart_ai_logo.png'
Save-Resized-Icon -source $bmpSrc -size 512 -destination $kbDrawable

# 3. Update android app (hybrid/wrapper) if present
$androidRes = 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\res'
if (Test-Path $androidRes) {
    foreach ($d in $densities.Keys) {
        $size = $densities[$d]
        $dir = Join-Path $androidRes $d
        Save-Resized-Icon -source $bmpSrc -size $size -destination (Join-Path $dir 'ic_launcher.png')
        Save-Resized-Icon -source $bmpSrc -size $size -destination (Join-Path $dir 'ic_launcher_round.png')
    }
}

# 4. Update web assets
$webAssets = @(
    'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icons\icon-192.png',
    'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icons\icon-512.png',
    'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\assets\icon-192.png',
    'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\assets\icon-512.png'
)

Save-Resized-Icon -source $bmpSrc -size 192 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icons\icon-192.png'
Save-Resized-Icon -source $bmpSrc -size 512 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icons\icon-512.png'
Save-Resized-Icon -source $bmpSrc -size 192 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\assets\icon-192.png'
Save-Resized-Icon -source $bmpSrc -size 512 -destination 'C:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\android\app\src\main\assets\icon-512.png'

$bmpSrc.Dispose()
Write-Host "All icons generated successfully with high quality!"
