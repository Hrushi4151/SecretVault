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
	_ datasource.DataSource              = &EnvironmentDataSource{}
	_ datasource.DataSourceWithConfigure = &EnvironmentDataSource{}
)

// EnvironmentDataSourceModel defines the model for environment data source.
type EnvironmentDataSourceModel struct {
	ID          types.String `tfsdk:"id"`
	WorkspaceID types.String `tfsdk:"workspace_id"`
	ProjectID   types.String `tfsdk:"project_id"`
	Name        types.String `tfsdk:"name"`
	Slug        types.String `tfsdk:"slug"`
	Type        types.String `tfsdk:"type"`
	IsProtected types.Bool   `tfsdk:"is_protected"`
	CreatedAt   types.String `tfsdk:"created_at"`
	UpdatedAt   types.String `tfsdk:"updated_at"`
}

// EnvironmentDataSource fetches metadata of a SecretVault Environment.
type EnvironmentDataSource struct {
	client *client.Client
}

func NewEnvironmentDataSource() datasource.DataSource {
	return &EnvironmentDataSource{}
}

func (d *EnvironmentDataSource) Metadata(_ context.Context, req datasource.MetadataRequest, resp *datasource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_environment"
}

func (d *EnvironmentDataSource) Schema(_ context.Context, _ datasource.SchemaRequest, resp *datasource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Fetches details of an existing SecretVault Environment (deployment tier).",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The UUID of the environment.",
				Required:    true,
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"workspace_id": schema.StringAttribute{
				Description: "The UUID of the parent workspace container.",
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
			"name": schema.StringAttribute{
				Description: "The name of the environment.",
				Computed:    true,
			},
			"slug": schema.StringAttribute{
				Description: "The URL slug of the environment.",
				Computed:    true,
			},
			"type": schema.StringAttribute{
				Description: "The tier classification of the environment ('DEVELOPMENT', 'STAGING', 'PRODUCTION', 'CUSTOM').",
				Computed:    true,
			},
			"is_protected": schema.BoolAttribute{
				Description: "Whether the environment requires strict reveal approvals.",
				Computed:    true,
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the environment was created.",
				Computed:    true,
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the environment was last updated.",
				Computed:    true,
			},
		},
	}
}

func (d *EnvironmentDataSource) Configure(_ context.Context, req datasource.ConfigureRequest, resp *datasource.ConfigureResponse) {
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

func (d *EnvironmentDataSource) Read(ctx context.Context, req datasource.ReadRequest, resp *datasource.ReadResponse) {
	var config EnvironmentDataSourceModel
	diags := req.Config.Get(ctx, &config)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	env, err := d.client.GetEnvironment(
		ctx,
		config.WorkspaceID.ValueString(),
		config.ProjectID.ValueString(),
		config.ID.ValueString(),
	)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Environment", err)
		return
	}

	config.Name = types.StringValue(env.Name)
	config.Slug = types.StringValue(env.Slug)
	config.Type = types.StringValue(env.Type)
	config.IsProtected = types.BoolValue(env.IsProtected)
	config.CreatedAt = types.StringValue(env.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	config.UpdatedAt = types.StringValue(env.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, config)
	resp.Diagnostics.Append(diags...)
}
