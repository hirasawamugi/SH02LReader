param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
      [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label = 'device',
      [switch]$LegacyM1)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskAdb = Join-Path $taskRoot 'tools\android-sdk\platform-tools\adb.exe'
$taskLogs = Join-Path $taskRoot 'tools\logs'
$taskFixtures = Join-Path $taskLogs ('release-fixtures-' + $Label)
$taskRemote = '/sdcard/Android/data/com.sh02l.reader/files'
New-Item -ItemType Directory -Path $taskFixtures -Force | Out-Null
function Invoke-ReaderAdb([string[]]$Arguments) {
    $taskResult = @(& $taskAdb -s $Serial @Arguments)
    if ($LASTEXITCODE -ne 0) { throw ('ADB failed: ' + ($Arguments -join ' ')) }
    return $taskResult
}
function Press-ReaderKey([int]$Code) { Invoke-ReaderAdb @('shell','input','keyevent',"$Code") | Out-Null; Start-Sleep -Milliseconds 120 }
function Read-ReaderUi {
    Press-ReaderKey 224
    Invoke-ReaderAdb @('shell','uiautomator','dump','--compressed',"$taskRemote/diagnostics/verification-ui.xml") | Out-Null
    $taskUiFile = Join-Path $taskLogs ('ui-release-' + $Label + '.xml')
    Invoke-ReaderAdb @('pull',"$taskRemote/diagnostics/verification-ui.xml",$taskUiFile) | Out-Null
    return [xml][IO.File]::ReadAllText($taskUiFile)
}
function Wait-ReaderText([string]$Needle, [int]$Seconds = 30) {
    $taskDeadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    do {
        $taskUi = Read-ReaderUi
        if ($taskUi.OuterXml.Contains($Needle)) { return $taskUi }
        Start-Sleep -Milliseconds 150
    } while ([DateTime]::UtcNow -lt $taskDeadline)
    throw ('UI did not show: ' + $Needle)
}
function Read-ReaderSnapshot([string]$Suffix) {
    $taskPath = Join-Path $taskLogs ('snapshot-m1-' + $Label + '-' + $Suffix + '.txt')
    Invoke-ReaderAdb @('pull',"$taskRemote/diagnostics/latest.txt",$taskPath) | Out-Null
    $taskSnapshot = [IO.File]::ReadAllText($taskPath)
    $taskLocator = [regex]::Match($taskSnapshot, '(?m)^locator=txt-v1:([0-9]+)')
    $taskCommit = [regex]::Match($taskSnapshot, '(?m)^committed=([a-f0-9]{64}):([0-9]+)')
    $taskBook = [regex]::Match($taskSnapshot, '(?m)^book=([a-f0-9]{64})')
    if (-not $taskLocator.Success -or -not $taskCommit.Success -or -not $taskBook.Success) { throw 'Snapshot has no committed TXT locator.' }
    if ($taskCommit.Groups[1].Value -ne $taskBook.Groups[1].Value -or $taskCommit.Groups[2].Value -ne $taskLocator.Groups[1].Value) { throw 'Displayed locator has not committed yet.' }
    return [PSCustomObject]@{ book=$taskBook.Groups[1].Value; offset=[long]$taskLocator.Groups[1].Value }
}
$taskRecords = @()
foreach ($taskFormat in @('UTF8','GB18030')) {
    $taskNumber = if ($taskFormat -eq 'UTF8') { '01' } else { '02' }
    $taskName = $taskNumber + '-' + $taskFormat + '.txt'
    $taskMarker = $taskFormat + '验证读本'
    $taskText = [Text.StringBuilder]::new()
    [void]$taskText.AppendLine($taskMarker)
    [void]$taskText.AppendLine('第一章 实体按键与分页')
    for ($taskIndex = 1; $taskIndex -le 1200; $taskIndex++) {
        if ($taskIndex -eq 500) { [void]$taskText.AppendLine('第二章 阅读位置') }
        if ($taskIndex -eq 1000) { [void]$taskText.AppendLine('第三章 目录与书签') }
        [void]$taskText.AppendLine("第 $taskIndex 段：这是新阅读器的原创验证文本。中文、English 和标点应完整显示，前后翻页应连续。")
    }
    $taskEncoding = if ($taskFormat -eq 'UTF8') { [Text.UTF8Encoding]::new($false) } else { [Text.Encoding]::GetEncoding(54936) }
    $taskLocal = Join-Path $taskFixtures $taskName
    [IO.File]::WriteAllText($taskLocal, $taskText.ToString(), $taskEncoding)
    Invoke-ReaderAdb @('shell','mkdir','-p',"$taskRemote/import") | Out-Null
    Invoke-ReaderAdb @('push',$taskLocal,"$taskRemote/import/$taskName") | Out-Null
}
Invoke-ReaderAdb @('shell','input','keyevent','224') | Out-Null
Invoke-ReaderAdb @('shell','wm','dismiss-keyguard') | Out-Null
Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
$taskUi = Read-ReaderUi
if ($taskUi.OuterXml.Contains('正文')) { Press-ReaderKey 134 }
Wait-ReaderText '书架' | Out-Null
foreach ($taskFormat in @('UTF8','GB18030')) {
    Press-ReaderKey 133
    Wait-ReaderText '应用导入文件夹' | Out-Null
    Press-ReaderKey 23
    $taskUi = Wait-ReaderText '01-UTF8.txt'
    # Refuse to select unrelated files if the app import folder has been populated by a user.
    $taskEntries = @($taskUi.SelectNodes('//node') | Where-Object { $_.text -match '\.txt$' } | ForEach-Object { $_.text })
    if ((($taskEntries | Sort-Object) -join ',') -ne '01-UTF8.txt,02-GB18030.txt') { throw 'Use an import folder containing only the two generated verification files.' }
    Press-ReaderKey 20
    if ($taskFormat -eq 'GB18030') { Press-ReaderKey 20 }
    Press-ReaderKey 23
    if (-not $LegacyM1) {
        Wait-ReaderText '复制导入' | Out-Null
        Press-ReaderKey 23
    }
    Wait-ReaderText '原创验证文本' | Out-Null
    for ($taskStep = 0; $taskStep -lt 5; $taskStep++) { Press-ReaderKey 22 }
    Start-Sleep -Milliseconds 700
    Press-ReaderKey 3
    Start-Sleep -Milliseconds 600
    $taskBefore = Read-ReaderSnapshot ($taskFormat + '-before-kill')
    if ($taskBefore.offset -le 0) { throw 'Page turns did not advance the document.' }
    # Return to an actual foreground reading page before force-stop; do not rely on an exit callback.
    Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
    Wait-ReaderText '原创验证文本' | Out-Null
    Invoke-ReaderAdb @('shell','am','force-stop','com.sh02l.reader') | Out-Null
    Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
    Wait-ReaderText '原创验证文本' | Out-Null
    Start-Sleep -Milliseconds 400
    Press-ReaderKey 3
    Start-Sleep -Milliseconds 600
    $taskAfter = Read-ReaderSnapshot ($taskFormat + '-after-kill')
    if ($taskBefore.book -ne $taskAfter.book -or $taskBefore.offset -ne $taskAfter.offset) { throw 'Forced-stop recovery changed the committed locator.' }
    $taskRecords += [PSCustomObject]@{ format=$taskFormat; killMode='foreground-after-commit'; before=$taskBefore; after=$taskAfter; result='pass' }
    Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
    Wait-ReaderText '原创验证文本' | Out-Null
    Press-ReaderKey 134
    Wait-ReaderText '书架' | Out-Null
}
$taskRecords | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskLogs ('release-smoke-m1-' + $Label + '.json')) -Encoding UTF8
$taskRecords | ConvertTo-Json -Depth 5
