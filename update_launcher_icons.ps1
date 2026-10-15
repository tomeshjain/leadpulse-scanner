Add-Type -AssemblyName System.Drawing

$srcPath = "C:\Users\Dell\.gemini\antigravity-ide\brain\2411c2a6-361e-4a47-afa6-50b50b501233\leadpulse_app_logo_1792066404778.jpg"
$baseRes = "c:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\wispr-flow-keyboard\app\src\main\res"

if (-not (Test-Path $srcPath)) {
    Write-Host "Source image not found: $srcPath"
    exit 1
}

$srcBitmap = [System.Drawing.Image]::FromFile($srcPath)

$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($folder in $sizes.Keys) {
    $dim = $sizes[$folder]
    $targetDir = Join-Path $baseRes $folder
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    $destBitmap = New-Object System.Drawing.Bitmap($dim, $dim)
    $graphics = [System.Drawing.Graphics]::FromImage($destBitmap)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

    $graphics.DrawImage($srcBitmap, 0, 0, $dim, $dim)
    $graphics.Dispose()

    $squarePath = Join-Path $targetDir "ic_launcher.png"
    $roundPath = Join-Path $targetDir "ic_launcher_round.png"

    $destBitmap.Save($squarePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $destBitmap.Save($roundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $destBitmap.Dispose()

    Write-Host "Saved $dim x $dim icons to $folder"
}

$rootIcon192 = "c:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icon-192.png"
$rootIcon512 = "c:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icon-512.png"
$rootMask192 = "c:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icon-maskable-192.png"
$rootMask512 = "c:\Users\Dell\.gemini\antigravity-ide\scratch\sales-card-scanner-app\icon-maskable-512.png"

$b192 = New-Object System.Drawing.Bitmap(192, 192)
$g192 = [System.Drawing.Graphics]::FromImage($b192)
$g192.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g192.DrawImage($srcBitmap, 0, 0, 192, 192)
$g192.Dispose()
$b192.Save($rootIcon192, [System.Drawing.Imaging.ImageFormat]::Png)
$b192.Save($rootMask192, [System.Drawing.Imaging.ImageFormat]::Png)
$b192.Dispose()

$b512 = New-Object System.Drawing.Bitmap(512, 512)
$g512 = [System.Drawing.Graphics]::FromImage($b512)
$g512.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g512.DrawImage($srcBitmap, 0, 0, 512, 512)
$g512.Dispose()
$b512.Save($rootIcon512, [System.Drawing.Imaging.ImageFormat]::Png)
$b512.Save($rootMask512, [System.Drawing.Imaging.ImageFormat]::Png)
$b512.Dispose()

$srcBitmap.Dispose()
Write-Host "All launcher and web icons successfully updated!"
