package resources

import (
	"context"
	"testing"

	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
)

func TestRotationPolicyResource_SchemaAudit(t *testing.T) {
	res := NewRotationPolicyResource()
	var resp resource.SchemaResponse
	res.Schema(context.Background(), resource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("Schema() produced diagnostics errors: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes

	// 1. Verify scoping attributes are required
	for _, reqKey := range []string{"workspace_id", "project_id", "environment_id", "secret_id"} {
		attr, ok := attrs[reqKey].(schema.StringAttribute)
		if !ok || !attr.Required {
			t.Errorf("expected attribute %q to be a Required StringAttribute", reqKey)
		}
	}

	// 2. Verify policy attributes are present and configurable
	if _, ok := attrs["strategy"].(schema.StringAttribute); !ok {
		t.Errorf("expected 'strategy' attribute")
	}
	if _, ok := attrs["secret_type"].(schema.StringAttribute); !ok {
		t.Errorf("expected 'secret_type' attribute")
	}
	if _, ok := attrs["interval_seconds"].(schema.Int64Attribute); !ok {
		t.Errorf("expected 'interval_seconds' attribute")
	}
	if _, ok := attrs["grace_period_seconds"].(schema.Int64Attribute); !ok {
		t.Errorf("expected 'grace_period_seconds' attribute")
	}

	// 3. Security Invariant: Verify NO plaintext secret attributes exist in rotation policy schema
	for attrName, attr := range attrs {
		if strAttr, ok := attr.(schema.StringAttribute); ok {
			if attrName == "value" || attrName == "password" || attrName == "secret" || attrName == "dek" {
				t.Errorf("SECURITY VIOLATION: Rotation policy schema must NEVER contain secret material attribute %q", attrName)
			}
			if strAttr.Sensitive && attrName != "value" {
				// Policy metadata should be non-secret configuration
			}
		}
	}
}

func TestRotationPolicyResource_ImportStateValidation(t *testing.T) {
	res := &RotationPolicyResource{}

	invalidIDs := []string{
		"invalid-id",
		"single-segment",
		"workspace/project/env",
		"workspace/project/env/secret/extra/extra2",
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
