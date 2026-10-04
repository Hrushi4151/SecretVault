package datasources

import (
	"context"
	"testing"

	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/datasource/schema"
)

func TestSecretDataSource_SchemaAudit(t *testing.T) {
	ds := NewSecretDataSource()
	var resp datasource.SchemaResponse
	ds.Schema(context.Background(), datasource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("SecretDataSource Schema() produced diagnostics errors: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes

	// Verify that secret data source NEVER defines a "value" or "plaintext" attribute
	if _, exists := attrs["value"]; exists {
		t.Errorf("CRITICAL SECURITY VIOLATION: SecretDataSource must NEVER expose a 'value' attribute")
	}
	if _, exists := attrs["plaintext"]; exists {
		t.Errorf("CRITICAL SECURITY VIOLATION: SecretDataSource must NEVER expose a 'plaintext' attribute")
	}

	// Verify that it only exposes metadata attributes
	for _, metaKey := range []string{"id", "workspace_id", "project_id", "environment_id", "name", "version", "status", "content_type", "created_at", "updated_at"} {
		if _, exists := attrs[metaKey]; !exists {
			t.Errorf("expected metadata attribute %q to exist in SecretDataSource", metaKey)
		}
	}
}

func TestWorkspaceDataSource_Schema(t *testing.T) {
	ds := NewWorkspaceDataSource()
	var resp datasource.SchemaResponse
	ds.Schema(context.Background(), datasource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("WorkspaceDataSource Schema() failed: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes
	if _, ok := attrs["id"].(schema.StringAttribute); !ok {
		t.Errorf("expected id attribute in WorkspaceDataSource")
	}
}

func TestProjectDataSource_Schema(t *testing.T) {
	ds := NewProjectDataSource()
	var resp datasource.SchemaResponse
	ds.Schema(context.Background(), datasource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("ProjectDataSource Schema() failed: %v", resp.Diagnostics)
	}
}

func TestEnvironmentDataSource_Schema(t *testing.T) {
	ds := NewEnvironmentDataSource()
	var resp datasource.SchemaResponse
	ds.Schema(context.Background(), datasource.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("EnvironmentDataSource Schema() failed: %v", resp.Diagnostics)
	}
}
