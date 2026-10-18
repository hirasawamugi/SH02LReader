param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
      [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label = 'device')
$ErrorActionPreference = 'Stop'
$taskProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
New-Item -ItemType Directory -Path (Join-Path $taskProjectRoot 'tools\logs') -Force | Out-Null
$taskAdb = Join-Path $taskProjectRoot 'tools\android-sdk\platform-tools\adb.exe'
$taskApk = Join-Path $taskProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$taskTests = Join-Path $taskProjectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
if (-not (Test-Path -LiteralPath $taskApk) -or -not (Test-Path -LiteralPath $taskTests)) { throw 'Run scripts/build.ps1 -Task verify first.' }
& $taskAdb -s $Serial shell input keyevent 224
& $taskAdb -s $Serial shell wm dismiss-keyguard
$taskPowerState = @(& $taskAdb -s $Serial shell dumpsys power) -join "`n"
if ($taskPowerState -notmatch 'mWakefulness=Awake') { throw 'Open the flip phone, wake and unlock its display before native key injection tests.' }
& $taskAdb -s $Serial install -r -t $taskApk
if ($LASTEXITCODE -ne 0) { throw 'Debug application install failed.' }
& $taskAdb -s $Serial install -r -t $taskTests
if ($LASTEXITCODE -ne 0) { throw 'Instrumentation test install failed.' }
$taskTestOutput = @(& $taskAdb -s $Serial shell am instrument -w -r 'com.sh02l.reader.debug.test/android.test.InstrumentationTestRunner')
$taskExit = $LASTEXITCODE
$taskLog = Join-Path $taskProjectRoot ('tools\logs\instrumentation-' + $Label + '.txt')
$taskTestOutput | Set-Content -LiteralPath $taskLog -Encoding UTF8
$taskTestOutput
if ($taskExit -ne 0 -or -not (($taskTestOutput -join "`n") -match 'OK \([0-9]+ tests?\)') -or (($taskTestOutput -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed')) {
    throw ('Instrumentation failed; see ' + $taskLog)
}
