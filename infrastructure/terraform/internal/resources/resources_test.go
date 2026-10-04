package resources

import (
	"context"
	"testing"

	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
)

func TestProjectResource_Schema(t *testing.T) {
	res := NewProjectResource()
	var resp resource.SchemaResponse
	res.Schema(context.Background(), resource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("ProjectResource Schema failed: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes
	if _, ok := attrs["workspace_id"].(schema.StringAttribute); !ok {
		t.Errorf("expected workspace_id attribute")
	}
	if _, ok := attrs["slug"].(schema.StringAttribute); !ok {
		t.Errorf("expected slug attribute")
	}
}

func TestEnvironmentResource_Schema(t *testing.T) {
	res := NewEnvironmentResource()
	var resp resource.SchemaResponse
	res.Schema(context.Background(), resource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("EnvironmentResource Schema failed: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes
	if _, ok := attrs["type"].(schema.StringAttribute); !ok {
		t.Errorf("expected type attribute")
	}
	if _, ok := attrs["is_protected"].(schema.BoolAttribute); !ok {
		t.Errorf("expected is_protected attribute")
	}
}

func TestMachineIdentityResource_Schema(t *testing.T) {
	res := NewMachineIdentityResource()
	var resp resource.SchemaResponse
	res.Schema(context.Background(), resource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("MachineIdentityResource Schema failed: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes
	if _, ok := attrs["max_token_ttl_seconds"].(schema.Int64Attribute); !ok {
		t.Errorf("expected max_token_ttl_seconds attribute")
	}
}

func TestProviderIntegrationResource_Schema(t *testing.T) {
	res := NewProviderIntegrationResource()
	var resp resource.SchemaResponse
	res.Schema(context.Background(), resource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("ProviderIntegrationResource Schema failed: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes
	credAttr, ok := attrs["credentials"].(schema.MapAttribute)
	if !ok || !credAttr.Sensitive {
		t.Errorf("credentials attribute in provider integration MUST be marked Sensitive: true")
	}
}
