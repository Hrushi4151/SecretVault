@echo off
REM SecretVault CLI launcher for Windows Command Prompt
setlocal

set "DIR=%~dp0"
set "CLI_JAR=%DIR%..\target\secretvault.jar"

if not exist "%CLI_JAR%" (
    set "CLI_JAR=%DIR%..\target\secretvault-cli-1.0.0.jar"
)

if not exist "%CLI_JAR%" (
    echo ERROR: SecretVault CLI JAR not found. Please build using 'mvn clean package -DskipTests' in cli directory. >&2
    exit /b 1
)

java -jar "%CLI_JAR%" %*
exit /b %ERRORLEVEL%
