# SecretVault CLI — Installation & Packaging Guide

## System Requirements

- **Runtime:** Java 21+ (OpenJDK, Temurin, Corretto, or Oracle)
- **Platforms:** macOS (Apple Silicon / Intel), Linux (x86_64 / aarch64), Windows (x86_64)

---

## 1. Building the Fat Executable JAR

To compile and package the standalone executable JAR:

```bash
cd cli
mvn clean package -DskipTests
```

The resulting all-in-one shaded JAR is created at:
```
cli/target/secretvault.jar
```

---

## 2. Platform Installation

### macOS / Linux

1. Add the CLI launcher directory to your `~/.zshrc` or `~/.bashrc`:

```bash
export PATH="/path/to/SecretVault/cli/bin:$PATH"
```

2. Make sure the launcher script is executable:

```bash
chmod +x /path/to/SecretVault/cli/bin/secretvault
```

3. Reload your shell and verify:

```bash
source ~/.zshrc
secretvault version
```

---

### Windows (PowerShell / CMD)

1. Add the `cli\bin` folder to your system environment variables (`PATH`):
   - In PowerShell:
   ```powershell
   $env:Path += ";C:\path\to\SecretVault\cli\bin"
   ```
2. Verify in Command Prompt or PowerShell:
   ```cmd
   secretvault version
   ```

---

## 3. Shell Autocompletion Setup

SecretVault CLI provides completion for `bash`, `zsh`, `fish`, and `PowerShell`.

### Bash
```bash
eval "$(secretvault completion bash)"
```

### Zsh
```zsh
eval "$(secretvault completion zsh)"
```

### Fish
```fish
secretvault completion fish | source
```

### PowerShell
```powershell
secretvault completion powershell | Out-String | Invoke-Expression
```

---

## 4. Verification

Run the diagnostics command to ensure connectivity and runtime health:

```bash
secretvault doctor
```
