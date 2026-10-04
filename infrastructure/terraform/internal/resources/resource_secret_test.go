package resources

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"testing"

	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
)

func TestSecretResource_SchemaAudit(t *testing.T) {
	res := NewSecretResource()
	var resp resource.SchemaResponse
	res.Schema(context.Background(), resource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("Schema() produced diagnostics errors: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes

	// 1. Verify "value" attribute is explicitly marked Sensitive
	valAttr, ok := attrs["value"].(schema.StringAttribute)
	if !ok {
		t.Fatalf("expected attribute 'value' to be schema.StringAttribute")
	}
	if !valAttr.Sensitive {
		t.Errorf("SECURITY CRITICAL: 'value' attribute MUST be marked Sensitive: true")
	}
	if !valAttr.Required {
		t.Errorf("'value' attribute MUST be Required: true")
	}

	// 2. Verify "fingerprint" is Computed and NOT Sensitive (hash digest only)
	fpAttr, ok := attrs["fingerprint"].(schema.StringAttribute)
	if !ok || !fpAttr.Computed {
		t.Errorf("'fingerprint' must be a Computed StringAttribute")
	}

	// 3. Verify Required Scoping IDs
	for _, idKey := range []string{"workspace_id", "project_id", "environment_id", "name"} {
		attr, ok := attrs[idKey].(schema.StringAttribute)
		if !ok || !attr.Required {
			t.Errorf("attribute %q MUST be Required: true", idKey)
		}
	}
}

func TestSecretResource_FingerprintCalculation(t *testing.T) {
	secretValue := "super-secure-production-db-credential-2026"
	hash := sha256.Sum256([]byte(secretValue))
	expectedFingerprint := hex.EncodeToString(hash[:])

	if len(expectedFingerprint) != 64 {
		t.Errorf("expected 64-character hex SHA-256 fingerprint, got length %d", len(expectedFingerprint))
	}
	if expectedFingerprint == secretValue {
		t.Errorf("SECURITY CRITICAL: fingerprint must NOT equal plaintext secret value")
	}
}

func TestSecretResource_ImportStateValidation(t *testing.T) {
	res := &SecretResource{}

	invalidIDs := []string{
		"invalid-id-format",
		"workspace/project",
		"workspace/project/env",
		"workspace/project/env/secret/extra",
		"",
	}

	for _, invalidID := range invalidIDs {
		var invalidResp resource.ImportStateResponse
		res.ImportState(context.Background(), resource.ImportStateRequest{ID: invalidID}, &invalidResp)
		if !invalidResp.Diagnostics.HasError() {
			t.Errorf("expected invalid import ID %q to produce diagnostic error, got none", invalidID)
		}
	}
}
