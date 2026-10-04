package datasources

import (
	"context"
	"fmt"

	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/datasource/schema"
	"github.com/hashicorp/terraform-plugin-framework/types"
	"github.com/secretvault/terraform-provider-secretvault/internal/client"
	"github.com/secretvault/terraform-provider-secretvault/internal/diagnostics"
	"github.com/secretvault/terraform-provider-secretvault/internal/validators"
)

var (
	_ datasource.DataSource              = &SecretDataSource{}
	_ datasource.DataSourceWithConfigure = &SecretDataSource{}
)

// SecretDataSourceModel defines the model for secret metadata data source.
// CRITICAL: This data source contains ZERO plaintext secret data.
type SecretDataSourceModel struct {
	ID            types.String `tfsdk:"id"`
	WorkspaceID   types.String `tfsdk:"workspace_id"`
	ProjectID     types.String `tfsdk:"project_id"`
	EnvironmentID types.String `tfsdk:"environment_id"`
	Name          types.String `tfsdk:"name"`
	ContentType   types.String `tfsdk:"content_type"`
	Version       types.Int64  `tfsdk:"version"`
	Status        types.String `tfsdk:"status"`
	Comment       types.String `tfsdk:"comment"`
	CreatedAt     types.String `tfsdk:"created_at"`
	UpdatedAt     types.String `tfsdk:"updated_at"`
}

// SecretDataSource reads non-sensitive metadata for a SecretVault secret.
type SecretDataSource struct {
	client *client.Client
}

func NewSecretDataSource() datasource.DataSource {
	return &SecretDataSource{}
}

func (d *SecretDataSource) Metadata(_ context.Context, req datasource.MetadataRequest, resp *datasource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_secret"
}

func (d *SecretDataSource) Schema(_ context.Context, _ datasource.SchemaRequest, resp *datasource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Reads non-sensitive metadata for a SecretVault Secret (version, timestamps, content-type). STRICTLY NEVER exposes plaintext secret values.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The UUID identifier of the secret.",
				Required:    true,
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"workspace_id": schema.StringAttribute{
				Description: "The UUID of the parent workspace.",
				Required:    true,
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"project_id": schema.StringAttribute{
				Description: "The UUID of the parent project.",
				Required:    true,
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"environment_id": schema.StringAttribute{
				Description: "The UUID of the target environment.",
				Required:    true,
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"name": schema.StringAttribute{
				Description: "The key name of the secret.",
				Computed:    true,
			},
			"content_type": schema.StringAttribute{
				Description: "The content format classification of the secret.",
				Computed:    true,
			},
			"version": schema.Int64Attribute{
				Description: "The current version number of the secret.",
				Computed:    true,
			},
			"status": schema.StringAttribute{
				Description: "The status of the secret ('ACTIVE', etc.).",
				Computed:    true,
			},
			"comment": schema.StringAttribute{
				Description: "The audit trail note or description attached to this secret.",
				Computed:    true,
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the secret was created.",
				Computed:    true,
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the secret was last updated.",
				Computed:    true,
			},
		},
	}
}

func (d *SecretDataSource) Configure(_ context.Context, req datasource.ConfigureRequest, resp *datasource.ConfigureResponse) {
	if req.ProviderData == nil {
		return
	}
	c, ok := req.ProviderData.(*client.Client)
	if !ok {
		resp.Diagnostics.AddError("Unexpected Data Source Configure Type", fmt.Sprintf("Expected *client.Client, got: %T", req.ProviderData))
		return
	}
	d.client = c
}

func (d *SecretDataSource) Read(ctx context.Context, req datasource.ReadRequest, resp *datasource.ReadResponse) {
	var config SecretDataSourceModel
	diags := req.Config.Get(ctx, &config)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	meta, err := d.client.GetSecretMetadata(
		ctx,
		config.WorkspaceID.ValueString(),
		config.ProjectID.ValueString(),
		config.EnvironmentID.ValueString(),
		config.ID.ValueString(),
	)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Secret metadata", err)
		return
	}

	config.Name = types.StringValue(meta.Name)
	config.ContentType = types.StringValue(meta.ContentType)
	config.Version = types.Int64Value(meta.Version)
	config.Status = types.StringValue(meta.Status)
	config.Comment = types.StringValue(meta.Comment)
	config.CreatedAt = types.StringValue(meta.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	config.UpdatedAt = types.StringValue(meta.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, config)
	resp.Diagnostics.Append(diags...)
}
