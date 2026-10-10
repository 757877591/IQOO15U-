# Local helper. Downloads a checksum-verified Gradle; uses an already installed Android SDK.
[CmdletBinding()]
param(
    [string]$SdkPath = "",
    [string[]]$Tasks = @(':app:assembleDebug', ':app:lintDebug')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = $PSScriptRoot
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath -and $env:LOCALAPPDATA) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $SdkPath -or -not (Test-Path (Join-Path $SdkPath 'platforms\android-35\android.jar')) -or -not (Test-Path (Join-Path $SdkPath 'build-tools\35.0.0'))) {
    throw 'Install Android SDK Platform 35 and Build-Tools 35.0.0. Pass -SdkPath or set ANDROID_HOME.'
}
Get-Command java -ErrorAction Stop | Out-Null
Get-Command javac -ErrorAction Stop | Out-Null
$Version = '8.11.1'
$Expected = 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'
$Cache = Join-Path $Root '.build-tools'
$Zip = Join-Path $Cache "gradle-$Version-bin.zip"
$Gradle = Join-Path $Cache "gradle-$Version\bin\gradle.bat"
New-Item -ItemType Directory -Path $Cache -Force | Out-Null
if (-not (Test-Path $Gradle)) {
    if (-not (Test-Path $Zip)) {
        Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$Version-bin.zip" -OutFile "$Zip.part"
        Move-Item -LiteralPath "$Zip.part" -Destination $Zip -Force
    }
    $Actual = (Get-FileHash -LiteralPath $Zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Actual -ne $Expected) { throw 'Gradle checksum mismatch. Delete the cached ZIP and retry.' }
    Expand-Archive -LiteralPath $Zip -DestinationPath $Cache -Force
}
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $SdkPath).Path
Push-Location $Root
try {
    & $Gradle --no-daemon --stacktrace @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
    Write-Host 'Requested Gradle tasks completed. APK variants are under app\build\outputs\apk\.'
} finally { Pop-Location }
