output "project_id" {
  value       = secretvault_project.backend_service.id
  description = "The UUID of the created project."
}

output "prod_environment_id" {
  value       = secretvault_environment.prod.id
  description = "The UUID of the created production environment."
}

output "secret_version" {
  value       = secretvault_secret.database_url.version
  description = "The current immutable version of the managed secret."
}

output "secret_fingerprint" {
  value       = secretvault_secret.database_url.fingerprint
  description = "SHA256 fingerprint digest of the secret value for external drift detection."
}

output "machine_identity_id" {
  value       = secretvault_machine_identity.k8s_workload.id
  description = "The UUID of the created machine workload identity."
}

output "integration_status" {
  value       = secretvault_provider_integration.render_sync.status
  description = "The connection status of the Render provider integration."
}
