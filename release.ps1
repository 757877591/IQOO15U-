# Build and VERIFY a signed, non-debuggable Release APK. Requires JDK 17/21 and Android SDK 35.
[CmdletBinding()]
param([string]$SdkPath = '', [switch]$BootstrapSdk)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = $PSScriptRoot
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath -and $env:LOCALAPPDATA) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if ($BootstrapSdk) {
    $SdkPath = & (Join-Path $Root 'prepare-sdk.ps1') -SdkPath $SdkPath
}
if (-not $SdkPath -or -not (Test-Path (Join-Path $SdkPath 'platforms\android-35\android.jar'))) {
    throw 'Android SDK Platform 35 is missing. Install it and Build-Tools 35.0.0; pass -SdkPath or set ANDROID_HOME.'
}
$SdkPath = (Resolve-Path -LiteralPath $SdkPath).Path
$Tools = Join-Path $SdkPath 'build-tools\35.0.0'
foreach ($File in @('apksigner.bat', 'zipalign.exe', 'aapt.exe')) {
    if (-not (Test-Path (Join-Path $Tools $File))) { throw "Missing Android build tool: $File" }
}
Get-Command java -ErrorAction Stop | Out-Null
Get-Command javac -ErrorAction Stop | Out-Null
Get-Command keytool -ErrorAction Stop | Out-Null
$Signing = Join-Path $Root '.signing'
$Key = Join-Path $Signing 'iqoo-release.p12'
$Credentials = Join-Path $Signing 'credentials.json'
$Names = @('IQOO_KEYSTORE', 'IQOO_STORE_PASSWORD', 'IQOO_KEY_ALIAS', 'IQOO_KEY_PASSWORD')
$Previous = @{}
foreach ($Name in $Names) { $Previous[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process') }
try {
    $ExternalCount = @($Names | Where-Object { -not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($_, 'Process')) }).Count
    if ($ExternalCount -ne 0 -and $ExternalCount -ne 4) { throw 'Supply all four IQOO signing environment variables, or none.' }
    if ($ExternalCount -eq 0) {
        $HasKey = Test-Path -LiteralPath $Key
        $HasCredentials = Test-Path -LiteralPath $Credentials
        if ($HasKey -ne $HasCredentials) { throw 'Incomplete .signing directory. Restore the matching key and credentials; do not regenerate one half.' }
        if (-not $HasKey) { throw 'Signing identity missing. Restore the existing PRIVATE signing backup; never silently generate a replacement.' }
        $Saved = Get-Content -LiteralPath $Credentials -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([string]::IsNullOrEmpty($Saved.alias) -or [string]::IsNullOrEmpty($Saved.password)) { throw 'Invalid signing credentials.' }
        $env:IQOO_KEYSTORE = $Key
        $env:IQOO_KEY_ALIAS = $Saved.alias
        $env:IQOO_STORE_PASSWORD = $Saved.password
        $env:IQOO_KEY_PASSWORD = $Saved.password
    }
    if (-not (Test-Path -LiteralPath $env:IQOO_KEYSTORE)) { throw 'Signing keystore does not exist.' }
    $env:IQOO_KEYSTORE = (Resolve-Path -LiteralPath $env:IQOO_KEYSTORE).Path
    & (Join-Path $Root 'build.ps1') -SdkPath $SdkPath -Tasks @(':app:assembleRelease', ':app:lintRelease')
    $Apk = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $Apk)) { throw 'Signed Release APK was not produced.' }
    $Dist = Join-Path $Root 'dist'
    New-Item -ItemType Directory -Path $Dist -Force | Out-Null
    $CertReport = & (Join-Path $Tools 'apksigner.bat') verify --verbose --print-certs $Apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $CertReport" }
    $Alignment = & (Join-Path $Tools 'zipalign.exe') -c -P 16 -v 4 $Apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw "APK alignment verification failed: $Alignment" }
    $Badging = & (Join-Path $Tools 'aapt.exe') dump badging $Apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw "APK manifest check failed: $Badging" }
    $BadgingText = $Badging -join "`n"
    if ($BadgingText -notmatch "package: name='ls\.iqooaugment\.com'" -or $BadgingText -match 'application-debuggable') {
        throw 'Wrong package name or debuggable APK; not publishing it into dist.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Archive = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try {
        if ($null -eq $Archive.GetEntry('classes.dex')) { throw 'APK has no classes.dex.' }
        $Entry = $Archive.GetEntry('assets/xposed_init')
        if ($null -eq $Entry) { throw 'APK has no Xposed entry.' }
        $Reader = New-Object System.IO.StreamReader($Entry.Open())
        try { $Hook = $Reader.ReadToEnd().Trim() } finally { $Reader.Dispose() }
        if ($Hook -ne 'ls.iqooaugment.com.hook.ShoulderHook') { throw 'Wrong Xposed entry.' }
    } finally { $Archive.Dispose() }
    $MetadataPath = Join-Path $Root 'app\build\outputs\apk\release\output-metadata.json'
    $Metadata = Get-Content -LiteralPath $MetadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $Version = [string]$Metadata.elements[0].versionName
    if ($Version -notmatch '^[A-Za-z0-9.+_-]+$') { throw 'Unsafe version name.' }
    $Name = "IQOOShoulderExtension-v$Version-release.apk"
    $Output = Join-Path $Dist $Name
    Copy-Item -LiteralPath $Apk -Destination $Output -Force
    $Sha = (Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash.ToLowerInvariant()
    "$Sha  $Name" | Set-Content -LiteralPath (Join-Path $Dist 'SHA256SUMS.txt') -Encoding ASCII
    $CertReport | Set-Content -LiteralPath (Join-Path $Dist 'signature-verification.txt') -Encoding UTF8
    $Badging | Set-Content -LiteralPath (Join-Path $Dist 'apk-badging.txt') -Encoding UTF8
    $Alignment | Set-Content -LiteralPath (Join-Path $Dist 'zipalign-verification.txt') -Encoding UTF8
    Write-Host "Verified signed Release APK: $Output"
    Write-Host "SHA-256: $Sha"
} finally {
    foreach ($Name in $Names) { [Environment]::SetEnvironmentVariable($Name, $Previous[$Name], 'Process') }
}
