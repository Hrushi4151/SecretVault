package diagnostics

import (
	"fmt"

	"github.com/hashicorp/terraform-plugin-framework/diag"
	"github.com/secretvault/terraform-provider-secretvault/internal/client"
)

// AddErrorFromClient converts an error into a sanitized Terraform diagnostic error.
func AddErrorFromClient(diags *diag.Diagnostics, summary string, err error) {
	if err == nil {
		return
	}

	sanitizedMsg := client.RedactSensitiveInfo(err.Error())

	if client.IsUnauthorized(err) {
		diags.AddError(
			summary,
			fmt.Sprintf("SecretVault authentication failed: %s. Verify SECRET_VAULT_TOKEN or machine credentials.", sanitizedMsg),
		)
		return
	}

	if client.IsForbidden(err) {
		diags.AddError(
			summary,
			fmt.Sprintf("SecretVault authorization denied: %s. Caller lacks required RBAC role or workspace/project permissions.", sanitizedMsg),
		)
		return
	}

	if client.IsNotFound(err) {
		diags.AddError(
			summary,
			fmt.Sprintf("SecretVault resource not found: %s.", sanitizedMsg),
		)
		return
	}

	if client.IsConflict(err) {
		diags.AddError(
			summary,
			fmt.Sprintf("SecretVault resource conflict: %s. The resource or slug may already exist.", sanitizedMsg),
		)
		return
	}

	if client.IsRateLimited(err) {
		diags.AddError(
			summary,
			fmt.Sprintf("SecretVault rate limit exceeded: %s. Bounded retries exhausted.", sanitizedMsg),
		)
		return
	}

	diags.AddError(summary, sanitizedMsg)
}
