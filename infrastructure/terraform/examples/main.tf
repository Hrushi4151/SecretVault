terraform {
  required_version = ">= 1.5.0"
  required_providers {
    secretvault = {
      source  = "registry.terraform.io/secretvault/secretvault"
      version = "~> 1.0.0"
    }
  }
}

provider "secretvault" {
  address      = var.secretvault_address
  token        = var.secretvault_token
  workspace_id = var.workspace_id

  tls {
    insecure_skip_verify = false
    ca_cert_file         = var.ca_cert_file
  }

  timeout_seconds   = 30
  max_retries       = 3
  retry_wait_min_ms = 500
}

# 1. Fetch parent Workspace details
data "secretvault_workspace" "core" {
  id = var.workspace_id
}

# 2. Create Project
resource "secretvault_project" "backend_service" {
  workspace_id = data.secretvault_workspace.core.id
  name         = "Backend API Service"
  slug         = "backend-api"
  description  = "Core payment and transaction engine"
}

# 3. Create Production Environment
resource "secretvault_environment" "prod" {
  workspace_id = data.secretvault_workspace.core.id
  project_id   = secretvault_project.backend_service.id
  name         = "Production"
  slug         = "prod"
  type         = "PRODUCTION"
  is_protected = true
}

# 4. Create Development Environment
resource "secretvault_environment" "dev" {
  workspace_id = data.secretvault_workspace.core.id
  project_id   = secretvault_project.backend_service.id
  name         = "Development"
  slug         = "dev"
  type         = "DEVELOPMENT"
  is_protected = false
}

# 5. Create Versioned Secret (Zero-Plaintext Read Lifecycle)
resource "secretvault_secret" "database_url" {
  workspace_id   = data.secretvault_workspace.core.id
  project_id     = secretvault_project.backend_service.id
  environment_id = secretvault_environment.prod.id
  name           = "DATABASE_URL"
  value          = var.db_connection_string # Sensitive variable
  content_type   = "TEXT"
  comment        = "Managed via Terraform IaC pipeline"
}

# 6. Read Secret Metadata (STRICTLY Zero-Plaintext)
data "secretvault_secret" "database_url_meta" {
  workspace_id   = data.secretvault_workspace.core.id
  project_id     = secretvault_project.backend_service.id
  environment_id = secretvault_environment.prod.id
  id             = secretvault_secret.database_url.id
}

# 7. Create Machine Identity for Kubernetes / CI/CD
resource "secretvault_machine_identity" "k8s_workload" {
  workspace_id          = data.secretvault_workspace.core.id
  name                  = "k8s-payment-operator"
  description           = "ServiceAccount token exchange identity for EKS cluster"
  role                  = "WORKSPACE_MEMBER"
  max_token_ttl_seconds = 3600
  enabled               = true
}

# 8. Create Provider Integration for Render / External Cloud Sync
resource "secretvault_provider_integration" "render_sync" {
  workspace_id  = data.secretvault_workspace.core.id
  name          = "render-us-east-prod"
  provider_type = "RENDER"
  config = {
    team_id    = "tea-example12345"
    service_id = "srv-example67890"
  }
  credentials = {
    api_key = var.render_api_key # Sensitive credential
  }
  enabled = true
}
