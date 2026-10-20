param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
      [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Label = 'device')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskAdb=Join-Path $taskRoot 'tools\android-sdk\platform-tools\adb.exe'
$taskLogs=Join-Path $taskRoot 'tools\logs'
$taskRemote='/sdcard/Android/data/com.sh02l.reader/files/diagnostics'
New-Item -ItemType Directory -Path $taskLogs -Force | Out-Null
function Invoke-ReaderAdb([string[]]$Arguments) {
    $taskResult=@(& $taskAdb -s $Serial @Arguments)
    if($LASTEXITCODE -ne 0){throw ('ADB failed: '+($Arguments -join ' '))}
    return $taskResult
}
function Press-ReaderKey([int]$Code){Invoke-ReaderAdb @('shell','input','keyevent',"$Code") | Out-Null;Start-Sleep -Milliseconds 200}
function Read-ReaderUi {
    Invoke-ReaderAdb @('shell','uiautomator','dump','--compressed',"$taskRemote/verification-ui.xml") | Out-Null
    $taskUiFile=Join-Path $taskLogs ('ui-m25-release-'+$Label+'.xml')
    Invoke-ReaderAdb @('pull',"$taskRemote/verification-ui.xml",$taskUiFile) | Out-Null
    return [xml][IO.File]::ReadAllText($taskUiFile)
}
function Has-ReaderBody($Ui){return @($Ui.SelectNodes('//node') | Where-Object { ([string]$_.'content-desc').StartsWith("正文`n") }).Count -gt 0}
function Wait-ReaderUi([string]$Text,[switch]$Body) {
    $taskDeadline=[DateTime]::UtcNow.AddSeconds(25)
    do {
        $taskUi=Read-ReaderUi
        if(($Body -and (Has-ReaderBody $taskUi)) -or (-not $Body -and $taskUi.OuterXml.Contains($Text))){return $taskUi}
        Start-Sleep -Milliseconds 200
    }while([DateTime]::UtcNow -lt $taskDeadline)
    throw ('UI not ready: '+$Text)
}
function Read-ReaderSnapshot([string]$Suffix) {
    $taskPath=Join-Path $taskLogs ('snapshot-m25-'+$Label+'-'+$Suffix+'.txt')
    Invoke-ReaderAdb @('pull',"$taskRemote/latest.txt",$taskPath) | Out-Null
    $taskSnapshot=[IO.File]::ReadAllText($taskPath)
    $taskBook=[regex]::Match($taskSnapshot,'(?m)^book=([a-f0-9]{64})\r?$')
    $taskLocation=[regex]::Match($taskSnapshot,'(?m)^locator=([^\r\n]+)')
    $taskCommit=[regex]::Match($taskSnapshot,'(?m)^committed=([a-f0-9]{64}):([^\r\n]+)')
    if(-not $taskBook.Success -or -not $taskLocation.Success -or -not $taskCommit.Success){throw 'No committed document location in snapshot.'}
    if($taskCommit.Groups[1].Value -ne $taskBook.Groups[1].Value -or $taskCommit.Groups[2].Value -ne $taskLocation.Groups[1].Value){throw 'Displayed location has not committed.'}
    return [PSCustomObject]@{book=$taskBook.Groups[1].Value;location=$taskLocation.Groups[1].Value}
}
Invoke-ReaderAdb @('shell','input','keyevent','224') | Out-Null
Invoke-ReaderAdb @('shell','wm','dismiss-keyguard') | Out-Null
Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
$taskUi=Read-ReaderUi
$taskStartedReading=Has-ReaderBody $taskUi
if($taskStartedReading){Press-ReaderKey 134}
$taskShelf=Wait-ReaderUi '书架'
Press-ReaderKey 133
Wait-ReaderUi '通用菜单' | Out-Null
Press-ReaderKey 134
Press-ReaderKey 7
Wait-ReaderUi '按键说明' | Out-Null
Press-ReaderKey 134
$taskUi=Read-ReaderUi
$taskRecords=[ordered]@{menu='pass';help='pass';importsAdded=0;recovery='not checked: library empty'}
# Only open a user's existing book. Never create, import or remove a release fixture.
# Confirm the selected row is a book before activating it. This works whether the
# shelf initially selects the import row or the most recent existing book.
$taskTitleNode=@($taskUi.SelectNodes('//node') | Where-Object { $_.text -match '^书架 · [1-9][0-9]* 本' })
if($taskTitleNode.Count -gt 0){
    Press-ReaderKey 131
    $taskBookMenu=Read-ReaderUi
    if(-not $taskBookMenu.OuterXml.Contains('设置 / 更换封面')){Press-ReaderKey 20;Press-ReaderKey 131;$taskBookMenu=Read-ReaderUi}
    if(-not $taskBookMenu.OuterXml.Contains('设置 / 更换封面')){throw 'Could not select an existing book safely.'}
    Press-ReaderKey 134
    Press-ReaderKey 23
    Wait-ReaderUi '' -Body | Out-Null
    Press-ReaderKey 131
    Wait-ReaderUi '关闭菜单' | Out-Null
    Press-ReaderKey 134
    Press-ReaderKey 3
    Start-Sleep -Milliseconds 600
    $taskBefore=Read-ReaderSnapshot 'before-stop'
    Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
    Wait-ReaderUi '' -Body | Out-Null
    Invoke-ReaderAdb @('shell','am','force-stop','com.sh02l.reader') | Out-Null
    Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
    Wait-ReaderUi '' -Body | Out-Null
    Press-ReaderKey 3
    Start-Sleep -Milliseconds 600
    $taskAfter=Read-ReaderSnapshot 'after-stop'
    if($taskBefore.book -ne $taskAfter.book -or $taskBefore.location -ne $taskAfter.location){throw 'Foreground restart changed the committed location.'}
    $taskRecords.recovery='pass';$taskRecords.before=$taskBefore;$taskRecords.after=$taskAfter
    Invoke-ReaderAdb @('shell','am','start','-W','-n','com.sh02l.reader/.MainActivity') | Out-Null
    Wait-ReaderUi '' -Body | Out-Null
    if(-not $taskStartedReading){Press-ReaderKey 134;Wait-ReaderUi '书架' | Out-Null}
}
$taskRecords | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskLogs ('release-smoke-m25-'+$Label+'.json')) -Encoding UTF8
$taskRecords | ConvertTo-Json -Depth 5
