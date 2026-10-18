param([string]$Version = '0.1.0-m0')
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:-[a-z0-9.]+)?$') { throw 'Invalid version.' }
$taskProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskOutputRoot = [System.IO.Path]::GetFullPath((Join-Path $taskProjectRoot ('dist\v' + $Version)))
if (-not $taskOutputRoot.StartsWith($taskProjectRoot + '\dist\', [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Output outside project dist.' }
$taskSourceApk = Join-Path $taskProjectRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $taskSourceApk)) { throw 'Build release first.' }
New-Item -ItemType Directory -Path $taskOutputRoot -Force | Out-Null
$taskApkName = 'SH02LReader-v' + $Version + '.apk'
$taskApk = Join-Path $taskOutputRoot $taskApkName
Copy-Item -LiteralPath $taskSourceApk -Destination $taskApk -Force
$taskBuildTools = Join-Path $taskProjectRoot 'tools\android-sdk\build-tools\34.0.0'
& (Join-Path $taskBuildTools 'apksigner.bat') verify --verbose --print-certs --min-sdk-version 27 $taskApk | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'signature.txt') -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
& (Join-Path $taskBuildTools 'zipalign.exe') -c -p 4 $taskApk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed.' }
$taskBadging = & (Join-Path $taskBuildTools 'aapt.exe') dump badging $taskApk
if ($LASTEXITCODE -ne 0 -or -not ($taskBadging -match ('versionName=''' + [regex]::Escape($Version) + ''''))) { throw 'APK version does not match release directory.' }
$taskBadging | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'apk-info.txt') -Encoding UTF8
$taskHash = (Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash.ToLowerInvariant()
($taskHash + '  ' + $taskApkName) | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'SHA256SUMS.txt') -Encoding ASCII
$taskCommit = & git -C $taskProjectRoot rev-parse --verify HEAD 2>$null
if ($LASTEXITCODE -ne 0) { $taskCommit = 'uncommitted' }
$taskSourceStatus = @(& git -C $taskProjectRoot status --porcelain)
$taskRecord = [ordered]@{
    version=$Version; applicationId='com.sh02l.reader'; minSdk=27; targetSdk=35
    apk=$taskApkName; bytes=(Get-Item -LiteralPath $taskApk).Length; sha256=$taskHash
    sourceCommit=$taskCommit; sourceClean=($taskSourceStatus.Count -eq 0)
    stage='M0 input and focus prototype'; toolchain='AGP 8.6.1 / Gradle 8.7 / JDK 17'
}
$taskRecord | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'build-info.json') -Encoding UTF8
$taskRecord | ConvertTo-Json
