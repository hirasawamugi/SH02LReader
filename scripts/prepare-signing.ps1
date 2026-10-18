$ErrorActionPreference = 'Stop'
$taskProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskKeytoolCommand = Get-Command keytool -ErrorAction SilentlyContinue
$taskKeytool = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\keytool.exe'))) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } elseif ($taskKeytoolCommand) { $taskKeytoolCommand.Source } else { throw 'JDK 17 keytool not found.' }
$taskSigningRoot = Join-Path $taskProjectRoot 'tools\signing'
New-Item -ItemType Directory -Path $taskSigningRoot -Force | Out-Null
$taskReleaseKey = Join-Path $taskSigningRoot 'sh02l-release.p12'
$taskReleaseProperties = Join-Path $taskSigningRoot 'release.properties'
if ((Test-Path -LiteralPath $taskReleaseKey) -xor (Test-Path -LiteralPath $taskReleaseProperties)) { throw 'Signing files are incomplete; retain the existing material and repair manually.' }
if (-not (Test-Path -LiteralPath $taskReleaseKey)) {
    $taskRandomBytes = New-Object byte[] 32
    $taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $taskRandom.GetBytes($taskRandomBytes)
    $taskRandom.Dispose()
    $taskSavedPassword = $env:SH02L_STORE_PASSWORD
    try {
        $env:SH02L_STORE_PASSWORD = [Convert]::ToBase64String($taskRandomBytes)
        & $taskKeytool -genkeypair -keystore $taskReleaseKey -storetype PKCS12 -storepass:env SH02L_STORE_PASSWORD -keypass:env SH02L_STORE_PASSWORD -alias sh02l -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=SH02L Reader,OU=Local development'
        if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed.' }
        $taskProperties = @('storeFile=tools/signing/sh02l-release.p12',('storePassword=' + $env:SH02L_STORE_PASSWORD),'keyAlias=sh02l',('keyPassword=' + $env:SH02L_STORE_PASSWORD))
        [System.IO.File]::WriteAllLines($taskReleaseProperties, $taskProperties, [System.Text.Encoding]::ASCII)
    } finally { $env:SH02L_STORE_PASSWORD = $taskSavedPassword }
}
$taskDebugKey = Join-Path $taskSigningRoot 'debug.keystore'
if (-not (Test-Path -LiteralPath $taskDebugKey)) {
    & $taskKeytool -genkeypair -keystore $taskDebugKey -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=SH02L Reader Debug'
    if ($LASTEXITCODE -ne 0) { throw 'Debug key generation failed.' }
}
Write-Output 'Project-local signing material is ready. Back up tools/signing securely; it is ignored by Git.'
