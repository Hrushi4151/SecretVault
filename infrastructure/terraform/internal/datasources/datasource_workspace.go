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
	_ datasource.DataSource              = &WorkspaceDataSource{}
	_ datasource.DataSourceWithConfigure = &WorkspaceDataSource{}
)

// WorkspaceDataSourceModel defines the model for workspace data source.
type WorkspaceDataSourceModel struct {
	ID          types.String `tfsdk:"id"`
	Name        types.String `tfsdk:"name"`
	Slug        types.String `tfsdk:"slug"`
	Description types.String `tfsdk:"description"`
	CreatedAt   types.String `tfsdk:"created_at"`
	UpdatedAt   types.String `tfsdk:"updated_at"`
}

// WorkspaceDataSource retrieves metadata for a SecretVault Workspace.
type WorkspaceDataSource struct {
	client *client.Client
}

func NewWorkspaceDataSource() datasource.DataSource {
	return &WorkspaceDataSource{}
}

func (d *WorkspaceDataSource) Metadata(_ context.Context, req datasource.MetadataRequest, resp *datasource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_workspace"
}

func (d *WorkspaceDataSource) Schema(_ context.Context, _ datasource.SchemaRequest, resp *datasource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Fetches details of an existing SecretVault Workspace isolation container.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The UUID of the workspace to read.",
				Required:    true,
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"name": schema.StringAttribute{
				Description: "The name of the workspace.",
				Computed:    true,
			},
			"slug": schema.StringAttribute{
				Description: "The unique URL slug of the workspace.",
				Computed:    true,
			},
			"description": schema.StringAttribute{
				Description: "The description of the workspace.",
				Computed:    true,
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the workspace was created.",
				Computed:    true,
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the workspace was last updated.",
				Computed:    true,
			},
		},
	}
}

func (d *WorkspaceDataSource) Configure(_ context.Context, req datasource.ConfigureRequest, resp *datasource.ConfigureResponse) {
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

func (d *WorkspaceDataSource) Read(ctx context.Context, req datasource.ReadRequest, resp *datasource.ReadResponse) {
	var config WorkspaceDataSourceModel
	diags := req.Config.Get(ctx, &config)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	ws, err := d.client.GetWorkspace(ctx, config.ID.ValueString())
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Workspace", err)
		return
	}

	config.Name = types.StringValue(ws.Name)
	config.Slug = types.StringValue(ws.Slug)
	config.Description = types.StringValue(ws.Description)
	config.CreatedAt = types.StringValue(ws.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	config.UpdatedAt = types.StringValue(ws.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, config)
	resp.Diagnostics.Append(diags...)
}
