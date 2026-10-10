# Downloads Google's checksum-verified command-line tools into this project.
# SDK license decisions remain interactive; this script never auto-accepts them.
[CmdletBinding()]
param([string]$SdkPath = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Get-Command java -ErrorAction Stop | Out-Null
Get-Command javac -ErrorAction Stop | Out-Null
if (-not $SdkPath) { $SdkPath = Join-Path $PSScriptRoot '.build-tools\android-sdk' }
New-Item -ItemType Directory -Path $SdkPath -Force | Out-Null
$SdkPath = (Resolve-Path -LiteralPath $SdkPath).Path
$Manager = Join-Path $SdkPath 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $Manager)) {
    $Cache = Join-Path $PSScriptRoot '.build-tools'
    New-Item -ItemType Directory -Path $Cache -Force | Out-Null
    $Zip = Join-Path $Cache 'commandlinetools-win-15859902_latest.zip'
    $Expected = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    if (-not (Test-Path -LiteralPath $Zip)) {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile "$Zip.part"
        Move-Item -LiteralPath "$Zip.part" -Destination $Zip -Force
    }
    if ((Get-FileHash -LiteralPath $Zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Expected) {
        throw 'Android command-line tools checksum mismatch. Delete the cached ZIP and retry.'
    }
    $Stage = Join-Path $Cache ('sdk-stage-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $Stage -Force | Out-Null
    try {
        Expand-Archive -LiteralPath $Zip -DestinationPath $Stage
        $Destination = Join-Path $SdkPath 'cmdline-tools\latest'
        if (Test-Path -LiteralPath $Destination) { throw 'An incomplete cmdline-tools/latest already exists; inspect it before retrying.' }
        New-Item -ItemType Directory -Path (Split-Path -Parent $Destination) -Force | Out-Null
        Move-Item -LiteralPath (Join-Path $Stage 'cmdline-tools') -Destination $Destination
    } finally { Remove-Item -LiteralPath $Stage -Recurse -Force }
}
Write-Host 'Review SDK license prompts below. Package installation needs your acceptance of the applicable licenses.'
& $Manager "--sdk_root=$SdkPath" --licenses | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'SDK license review did not finish successfully.' }
& $Manager "--sdk_root=$SdkPath" 'platforms;android-35' 'build-tools;35.0.0' | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed.' }
if (-not (Test-Path (Join-Path $SdkPath 'platforms\android-35\android.jar')) -or -not (Test-Path (Join-Path $SdkPath 'build-tools\35.0.0\apksigner.bat'))) {
    throw 'Required SDK files were not installed.'
}
Write-Host "Android SDK ready: $SdkPath"
Write-Output $SdkPath
