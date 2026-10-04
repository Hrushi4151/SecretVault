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
	_ datasource.DataSource              = &ProjectDataSource{}
	_ datasource.DataSourceWithConfigure = &ProjectDataSource{}
)

// ProjectDataSourceModel defines the model for project data source.
type ProjectDataSourceModel struct {
	ID          types.String `tfsdk:"id"`
	WorkspaceID types.String `tfsdk:"workspace_id"`
	Name        types.String `tfsdk:"name"`
	Slug        types.String `tfsdk:"slug"`
	Description types.String `tfsdk:"description"`
	CreatedAt   types.String `tfsdk:"created_at"`
	UpdatedAt   types.String `tfsdk:"updated_at"`
}

// ProjectDataSource fetches metadata of a SecretVault Project.
type ProjectDataSource struct {
	client *client.Client
}

func NewProjectDataSource() datasource.DataSource {
	return &ProjectDataSource{}
}

func (d *ProjectDataSource) Metadata(_ context.Context, req datasource.MetadataRequest, resp *datasource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_project"
}

func (d *ProjectDataSource) Schema(_ context.Context, _ datasource.SchemaRequest, resp *datasource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Fetches details of an existing SecretVault Project.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The UUID of the project.",
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
			"name": schema.StringAttribute{
				Description: "The name of the project.",
				Computed:    true,
			},
			"slug": schema.StringAttribute{
				Description: "The URL slug of the project.",
				Computed:    true,
			},
			"description": schema.StringAttribute{
				Description: "The description of the project.",
				Computed:    true,
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the project was created.",
				Computed:    true,
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the project was last updated.",
				Computed:    true,
			},
		},
	}
}

func (d *ProjectDataSource) Configure(_ context.Context, req datasource.ConfigureRequest, resp *datasource.ConfigureResponse) {
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

func (d *ProjectDataSource) Read(ctx context.Context, req datasource.ReadRequest, resp *datasource.ReadResponse) {
	var config ProjectDataSourceModel
	diags := req.Config.Get(ctx, &config)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	project, err := d.client.GetProject(ctx, config.WorkspaceID.ValueString(), config.ID.ValueString())
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Project", err)
		return
	}

	config.Name = types.StringValue(project.Name)
	config.Slug = types.StringValue(project.Slug)
	config.Description = types.StringValue(project.Description)
	config.CreatedAt = types.StringValue(project.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	config.UpdatedAt = types.StringValue(project.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, config)
	resp.Diagnostics.Append(diags...)
}
