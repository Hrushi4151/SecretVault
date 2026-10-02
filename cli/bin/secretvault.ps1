# SecretVault CLI launcher for Windows PowerShell
$PSScriptRoot = Split-Path -Parent -Path $MyInvocation.MyCommand.Definition
$CliJar = Join-Path $PSScriptRoot "..\target\secretvault.jar"

if (-not (Test-Path $CliJar)) {
    $CliJar = Join-Path $PSScriptRoot "..\target\secretvault-cli-1.0.0.jar"
}

if (-not (Test-Path $CliJar)) {
    Write-Error "SecretVault CLI JAR not found. Please build using 'mvn clean package -DskipTests' in cli directory."
    exit 1
}

& java -jar $CliJar $args
exit $LASTEXITCODE
