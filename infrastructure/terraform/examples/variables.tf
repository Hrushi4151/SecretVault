variable "secretvault_address" {
  type        = string
  description = "The base URL address of the SecretVault API server."
  default     = "https://127.0.0.1:8443"
}

variable "secretvault_token" {
  type        = string
  description = "Authentication token for SecretVault provider operations."
  sensitive   = true
  default     = null
}

variable "workspace_id" {
  type        = string
  description = "Target workspace UUID container."
  default     = "00000000-0000-0000-0000-000000000001"
}

variable "ca_cert_file" {
  type        = string
  description = "Optional path to custom CA certificate file for TLS validation."
  default     = null
}

variable "db_connection_string" {
  type        = string
  description = "Database connection string secret value."
  sensitive   = true
  default     = "postgres://app_user:temporary_placeholder@db.internal:5432/app"
}

variable "render_api_key" {
  type        = string
  description = "Render API integration key."
  sensitive   = true
  default     = "rnd_placeholder_key"
}
