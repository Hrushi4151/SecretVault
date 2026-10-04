package provider

import (
	"context"
	"testing"

	"github.com/hashicorp/terraform-plugin-framework/provider"
	"github.com/hashicorp/terraform-plugin-framework/provider/schema"
)

func TestProvider_Schema(t *testing.T) {
	p := New("1.0.0")()
	var resp provider.SchemaResponse
	p.Schema(context.Background(), provider.SchemaRequest{}, &resp)

	if resp.Diagnostics.HasError() {
		t.Fatalf("Provider Schema() failed: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes

	// Verify token is Sensitive
	tokenAttr, ok := attrs["token"].(schema.StringAttribute)
	if !ok || !tokenAttr.Sensitive {
		t.Errorf("expected 'token' attribute in provider to be Sensitive: true")
	}

	// Verify client_secret is Sensitive
	secretAttr, ok := attrs["client_secret"].(schema.StringAttribute)
	if !ok || !secretAttr.Sensitive {
		t.Errorf("expected 'client_secret' attribute in provider to be Sensitive: true")
	}

	// Verify Resources
	resources := p.Resources(context.Background())
	if len(resources) < 5 {
		t.Errorf("expected at least 5 managed resources, got %d", len(resources))
	}

	// Verify Data Sources
	dataSources := p.DataSources(context.Background())
	if len(dataSources) < 4 {
		t.Errorf("expected at least 4 data sources, got %d", len(dataSources))
	}
}
