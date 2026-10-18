param([ValidateSet('verify', 'debug', 'release', 'connected')][string]$Task = 'verify', [switch]$WriteVerification)
$ErrorActionPreference = 'Stop'
$taskProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskSavedEnvironment = @{}
$taskEnvironmentNames = @('JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','ANDROID_USER_HOME','GRADLE_USER_HOME')
foreach ($taskEnvironmentName in $taskEnvironmentNames) { $taskSavedEnvironment[$taskEnvironmentName] = [Environment]::GetEnvironmentVariable($taskEnvironmentName, 'Process') }
Push-Location -LiteralPath $taskProjectRoot
try {
    $taskJavac = Get-Command javac -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath 'tools\jdk-17\bin\java.exe') { $env:JAVA_HOME = Join-Path $taskProjectRoot 'tools\jdk-17' }
    elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { }
    elseif ($taskJavac) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $taskJavac.Source) }
    else { throw 'JDK 17 required. Set JAVA_HOME or install tools/jdk-17.' }
    $env:ANDROID_HOME = Join-Path $taskProjectRoot 'tools\android-sdk'
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:ANDROID_USER_HOME = Join-Path $taskProjectRoot 'tools\android-user'
    $env:GRADLE_USER_HOME = Join-Path $taskProjectRoot 'tools\gradle-user-home'
    if (-not (Test-Path -LiteralPath 'tools\android-sdk\platforms\android-35\android.jar')) { throw 'Install the project-local Android SDK first; see tools/README.md.' }
    if (-not (Test-Path -LiteralPath 'tools\signing\debug.keystore')) { & (Join-Path $PSScriptRoot 'prepare-signing.ps1') }
    $taskArguments = @('--console=plain', '--no-daemon')
    if ($WriteVerification) { $taskArguments += @('--write-verification-metadata', 'sha256') }
    switch ($Task) {
        'verify' { $taskArguments += @(':reader-core:test', ':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest') }
        'debug' { $taskArguments += ':app:assembleDebug' }
        'release' { $taskArguments += @(':app:lintRelease', ':app:assembleRelease') }
        'connected' { $taskArguments += ':app:connectedDebugAndroidTest' }
    }
    if (Test-Path -LiteralPath 'tools\gradle-8.7\bin\gradle.bat') {
        & '.\tools\gradle-8.7\bin\gradle.bat' @taskArguments
    } else {
        & '.\gradlew.bat' @taskArguments
    }
    if ($LASTEXITCODE -ne 0) { throw ('Gradle failed with exit code ' + $LASTEXITCODE) }
} finally {
    Pop-Location
    foreach ($taskEnvironmentName in $taskEnvironmentNames) { [Environment]::SetEnvironmentVariable($taskEnvironmentName, $taskSavedEnvironment[$taskEnvironmentName], 'Process') }
}
