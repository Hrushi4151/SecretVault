# Environment Files & Safe Synchronization (`env pull` / `env push`)

When integrating with legacy tooling or tools that mandate physical `.env` files on disk, SecretVault CLI provides secure `pull` and `push` mechanisms designed with strict safeguards.

---

## 1. Safe `.env` Pull (`secretvault env pull`)

The `env pull` command fetches active secrets from the environment and outputs them in structured formats.

### Safe Output Options:

```bash
# 1. Output directly to terminal stdout in standard KEY=value format
secretvault env pull --format env

# 2. Output as shell export commands (useful for eval in temporary subshells)
secretvault env pull --format shell

# 3. Output as JSON for automated pipelines
secretvault env pull --format json
```

### Writing to `.env` on Disk:

```bash
secretvault env pull --output .env
```

#### Automatic Security Protections:
1. **Plaintext Warning:** Warns the user that plaintext secrets are about to be written to disk.
2. **`.gitignore` Check:** Inspects the current repository's `.gitignore` file. If `.env` is NOT listed in `.gitignore`, outputs a prominent warning to prevent accidental git commits.
3. **Restricted Permissions:** Automatically applies `chmod 600` (read/write only by owner) to the created file.
4. **Atomic Write:** Writes to a secure temporary file first before atomically moving it to the destination to prevent partial file corruption.

---

## 2. Safe `.env` Push (`secretvault env push`)

The `env push` command imports key-value pairs from a local file into SecretVault.

### Diff Preview with `--dry-run`:

Before modifying remote secrets, run with `--dry-run` to inspect the planned changes:

```bash
secretvault env push --file .env --dry-run
```

Output:
```
SecretVault .env push preview (Zero Plaintext Displayed):
ACTION          SECRET NAME      
-------------   --------------   
CREATE (v1)     REDIS_HOST       
CREATE (v1)     REDIS_PORT       
UPDATE (vN+1)   DATABASE_URL

Summary: 2 new secrets, 1 updates.
Dry run completed. No remote changes were applied.
```

### Applying Changes:

```bash
secretvault env push --file .env --yes
```

---

## 3. Safe Parser Security Guarantees

The SecretVault CLI `.env` parser is a pure data tokenizer:
- **No Command Execution:** Strings like `KEY=$(whoami)` or `KEY=`whoami`` are stored as literal string values and NEVER executed in a shell.
- **Size Limits:** Enforces a 5MB maximum file size to prevent denial-of-service or unbounded memory allocation.
- **Encoding & Escaping:** Correctly unescapes quotes (`\"`), newlines (`\n`), tabs (`\t`), and handles Windows CRLF line endings.
