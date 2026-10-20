param([string[]]$Paths,[switch]$Check)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskJar=Join-Path $taskRoot 'tools\downloads\google-java-format-1.24.0-all-deps.jar'
$taskExpected='812f805f58112460edf01bf202a8e61d0fd1f35c0d4fabd54220640776ec57a1'
if(-not (Test-Path -LiteralPath $taskJar)){
    New-Item -ItemType Directory -Path (Split-Path -Parent $taskJar) -Force | Out-Null
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar' -OutFile $taskJar -TimeoutSec 30
}
if((Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskExpected){throw 'Formatter checksum mismatch.'}
if(-not $Paths){
    $Paths=@(& git -C $taskRoot diff --name-only --diff-filter=AM HEAD) + @(& git -C $taskRoot ls-files --others --exclude-standard)
}
$taskFiles=@($Paths | Sort-Object -Unique | Where-Object { $_ -like '*.java' } | ForEach-Object {
    $taskFile=[IO.Path]::GetFullPath((Join-Path $taskRoot $_))
    if(-not $taskFile.StartsWith($taskRoot+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Source outside workspace.'}
    if(-not (Test-Path -LiteralPath $taskFile -PathType Leaf)){throw ('Missing source: '+$_)}
    $taskFile
})
if($taskFiles.Count -eq 0){Write-Output 'No changed Java files.';exit 0}
$taskOptions=@('--aosp')
if($Check){$taskOptions+=@('--dry-run','--set-exit-if-changed')}else{$taskOptions+='--replace'}
& java -jar $taskJar @taskOptions @taskFiles
if($LASTEXITCODE -ne 0){throw 'Java formatting failed.'}
Write-Output ('Formatted/checked '+$taskFiles.Count+' Java sources.')
