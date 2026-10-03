# SecretVault CLI Reference — Phase 13 Subcommands

## 1. Global Options
```bash
secretvault --profile <name> --server <url> --workspace <id|slug> --json --quiet --no-color
```

## 2. Events Command (`secretvault events`)

```bash
# List events in active workspace
secretvault events list --type ROTATION_FAILED --limit 25

# Inspect specific event payload
secretvault events get a78d0f19-913b-419b-98b7-6a4ec701e85a

# Replay events
secretvault events replay --type SECRET_COMPROMISED --reexecute=true

# List replay history
secretvault events replays --limit 10
```

## 3. Automation Command (`secretvault automation`)

```bash
# List policies
secretvault automation policies

# Inspect policy details and DSL conditions
secretvault automation get 89c7d4e2-6321-42ab-bb34-9218bfad901a

# Enable or disable policy
secretvault automation enable 89c7d4e2-6321-42ab-bb34-9218bfad901a
secretvault automation disable 89c7d4e2-6321-42ab-bb34-9218bfad901a

# List pending approvals
secretvault automation approvals --status PENDING

# Approve or reject sensitive automation actions
secretvault automation approve b37a1c89-9182-411a-821a-641527ab8902
secretvault automation reject b37a1c89-9182-411a-821a-641527ab8902 --reason "Not authorized during freeze"

# View execution audit logs
secretvault automation executions --status FAILED
```

## 4. Webhook Command (`secretvault webhook`)

```bash
# List webhooks
secretvault webhook list

# Create webhook endpoint
secretvault webhook create --name "Security Alerts" --url "https://api.mycompany.com/webhooks/sv" --events "ROTATION_FAILED,SECRET_COMPROMISED"

# Inspect webhook endpoint
secretvault webhook get 29cf8a10-b183-4a12-88ef-9182bf901234

# List deliveries
secretvault webhook deliveries --webhook-id 29cf8a10-b183-4a12-88ef-9182bf901234

# Replay failed delivery
secretvault webhook replay 91fa0b22-8172-4781-a912-827162541829
```

## 5. Incident Command (`secretvault incident`)

```bash
# List security incidents
secretvault incident list --status OPEN --severity CRITICAL

# Declare security incident
secretvault incident create --title "Suspected prod leak" --severity CRITICAL --category COMPROMISE

# Inspect incident details
secretvault incident get e18a2091-8812-42bb-9182-127389102938

# Transition status through triage
secretvault incident update-status e18a2091-8812-42bb-9182-127389102938 --status CONTAINED --summary "Leases revoked and credentials isolated"
```

## 6. Notification Command (`secretvault notification`)

```bash
# List unread notifications
secretvault notification list --status UNREAD

# Mark single notification as read
secretvault notification read 71ca9012-9982-4411-a881-817265142531

# Mark all as read
secretvault notification read-all
```
