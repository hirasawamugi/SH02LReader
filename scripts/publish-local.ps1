param([string]$Version = '0.4.0-m2.5')
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:-[a-z0-9.]+)?$') { throw 'Invalid version.' }
$taskProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskCommit = & git -C $taskProjectRoot rev-parse --verify HEAD 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Commit the source before publishing.' }
$taskSourceStatus = @(& git -C $taskProjectRoot status --porcelain)
if ($LASTEXITCODE -ne 0 -or $taskSourceStatus.Count -ne 0) { throw 'Source must be clean before publishing.' }
$taskOutputRoot = [System.IO.Path]::GetFullPath((Join-Path $taskProjectRoot ('dist\v' + $Version)))
if (-not $taskOutputRoot.StartsWith($taskProjectRoot + '\dist\', [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Output outside project dist.' }
$taskSourceApk = Join-Path $taskProjectRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $taskSourceApk)) { throw 'Build release first.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [IO.Compression.ZipFile]::OpenRead($taskSourceApk)
try {
    $taskVcsEntry = $taskArchive.GetEntry('META-INF/version-control-info.textproto')
    if ($null -eq $taskVcsEntry) { throw 'APK has no embedded source revision.' }
    $taskVcsReader = [IO.StreamReader]::new($taskVcsEntry.Open())
    try { $taskVcsText = $taskVcsReader.ReadToEnd() } finally { $taskVcsReader.Dispose() }
} finally { $taskArchive.Dispose() }
$taskVcsMatch = [regex]::Match($taskVcsText,'revision: "([0-9a-f]{40})"')
if (-not $taskVcsMatch.Success) { throw 'APK source revision is invalid.' }
$taskBuildCommit = $taskVcsMatch.Groups[1].Value
# Documentation and verification scripts may follow a tested APK; compiled sources must match.
& git -C $taskProjectRoot diff --quiet $taskBuildCommit HEAD -- app reader-core reader-android engine-txt engine-epub build.gradle settings.gradle gradle.properties gradle
if ($LASTEXITCODE -ne 0) { throw 'Compiled source differs from APK; rebuild release.' }
$taskApkName = 'SH02LReader-v' + $Version + '.apk'
$taskApk = Join-Path $taskOutputRoot $taskApkName
$taskBuildTools = Join-Path $taskProjectRoot 'tools\android-sdk\build-tools\34.0.0'
$taskSignature = @(& (Join-Path $taskBuildTools 'apksigner.bat') verify --verbose --print-certs --min-sdk-version 27 $taskSourceApk)
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
& (Join-Path $taskBuildTools 'zipalign.exe') -c -p 4 $taskSourceApk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed.' }
$taskBadging = & (Join-Path $taskBuildTools 'aapt.exe') dump badging $taskSourceApk
if ($LASTEXITCODE -ne 0 -or -not ($taskBadging -match ('versionName=''' + [regex]::Escape($Version) + ''''))) { throw 'APK version does not match release directory.' }
$taskHash = (Get-FileHash -LiteralPath $taskSourceApk -Algorithm SHA256).Hash.ToLowerInvariant()
if (Test-Path -LiteralPath $taskApk) {
    $taskExistingRecordPath = Join-Path $taskOutputRoot 'build-info.json'
    if (-not (Test-Path -LiteralPath $taskExistingRecordPath)) { throw 'Existing release has no source record; use a new version.' }
    $taskExistingRecord = [IO.File]::ReadAllText($taskExistingRecordPath) | ConvertFrom-Json
    if ($taskExistingRecord.sourceCommit -ne $taskBuildCommit -or (Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskHash) {
        throw 'An existing release is immutable; increment the version before publishing changed content.'
    }
}
New-Item -ItemType Directory -Path $taskOutputRoot -Force | Out-Null
Copy-Item -LiteralPath $taskSourceApk -Destination $taskApk -Force
$taskSignature | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'signature.txt') -Encoding UTF8
$taskBadging | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'apk-info.txt') -Encoding UTF8
($taskHash + '  ' + $taskApkName) | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'SHA256SUMS.txt') -Encoding ASCII
$taskDocumentPrefix = if ($Version -match '-m2\.5$') { 'M2_5' } elseif ($Version -match '-m2$') { 'M2' } else { 'M1_5' }
Copy-Item -LiteralPath (Join-Path $taskProjectRoot ('docs\' + $taskDocumentPrefix + '_VALIDATION.md')) -Destination (Join-Path $taskOutputRoot 'VALIDATION.md') -Force
Copy-Item -LiteralPath (Join-Path $taskProjectRoot ('docs\' + $taskDocumentPrefix + '_RELEASE_NOTES.md')) -Destination (Join-Path $taskOutputRoot 'CHANGELOG.md') -Force
foreach ($taskDocument in @(($taskDocumentPrefix + '_VALIDATION.md'),($taskDocumentPrefix + '_RELEASE_NOTES.md'),($taskDocumentPrefix + '_REVIEW.md'),'CODE_STRUCTURE.md','MATVT_DIAGNOSIS.md')) {
    $taskDocumentPath = Join-Path $taskProjectRoot ('docs\' + $taskDocument)
    if (Test-Path -LiteralPath $taskDocumentPath) { Copy-Item -LiteralPath $taskDocumentPath -Destination (Join-Path $taskOutputRoot $taskDocument) -Force }
}
Copy-Item -LiteralPath (Join-Path $taskProjectRoot 'docs\TXT_STORAGE.md') -Destination (Join-Path $taskOutputRoot 'TXT_STORAGE.md') -Force
$taskImageFolder = if ($taskDocumentPrefix -eq 'M2_5') { 'm2-5' } elseif ($taskDocumentPrefix -eq 'M2') { 'm2' } else { 'm1-5' }
$taskImageOutput = Join-Path $taskOutputRoot ('images\' + $taskImageFolder)
New-Item -ItemType Directory -Path $taskImageOutput -Force | Out-Null
foreach ($taskImage in Get-ChildItem -LiteralPath (Join-Path $taskProjectRoot ('docs\images\' + $taskImageFolder)) -Filter '*.png' -File -ErrorAction SilentlyContinue) {
    Copy-Item -LiteralPath $taskImage.FullName -Destination (Join-Path $taskImageOutput $taskImage.Name) -Force
}
$taskRecord = [ordered]@{
    version=$Version; applicationId='com.sh02l.reader'; minSdk=27; targetSdk=35
    apk=$taskApkName; bytes=(Get-Item -LiteralPath $taskApk).Length; sha256=$taskHash
    sourceCommit=$taskBuildCommit; publicationCommit=$taskCommit; sourceClean=($taskSourceStatus.Count -eq 0)
    stage=$(if ($taskDocumentPrefix -eq 'M2_5') { 'M2.5 shared architecture, keypad menus and EPUB' } elseif ($taskDocumentPrefix -eq 'M2') { 'M2 daily reading and storage management' } else { 'M1.5 themes, keypad UI and safe import' }); toolchain='AGP 8.6.1 / Gradle 8.7 / JDK 17'
}
$taskRecord | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutputRoot 'build-info.json') -Encoding UTF8
$taskRecord | ConvertTo-Json
