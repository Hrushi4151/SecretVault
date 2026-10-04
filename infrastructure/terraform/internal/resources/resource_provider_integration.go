package resources

import (
	"context"
	"fmt"
	"strings"

	"github.com/hashicorp/terraform-plugin-framework-validators/stringvalidator"
	"github.com/hashicorp/terraform-plugin-framework/path"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/booldefault"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/planmodifier"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/stringplanmodifier"
	"github.com/hashicorp/terraform-plugin-framework/schema/validator"
	"github.com/hashicorp/terraform-plugin-framework/types"
	"github.com/secretvault/terraform-provider-secretvault/internal/client"
	"github.com/secretvault/terraform-provider-secretvault/internal/diagnostics"
	"github.com/secretvault/terraform-provider-secretvault/internal/validators"
)

var (
	_ resource.Resource                = &ProviderIntegrationResource{}
	_ resource.ResourceWithConfigure   = &ProviderIntegrationResource{}
	_ resource.ResourceWithImportState = &ProviderIntegrationResource{}
)

// ProviderIntegrationResourceModel defines the Terraform state model for an external integration.
type ProviderIntegrationResourceModel struct {
	ID           types.String `tfsdk:"id"`
	WorkspaceID  types.String `tfsdk:"workspace_id"`
	Name         types.String `tfsdk:"name"`
	ProviderType types.String `tfsdk:"provider_type"`
	Config       types.Map    `tfsdk:"config"`
	Credentials  types.Map    `tfsdk:"credentials"`
	Enabled      types.Bool   `tfsdk:"enabled"`
	Status       types.String `tfsdk:"status"`
	CreatedAt    types.String `tfsdk:"created_at"`
	UpdatedAt    types.String `tfsdk:"updated_at"`
}

// ProviderIntegrationResource manages external provider connections (Render, Vercel, GitHub, AWS, etc.).
type ProviderIntegrationResource struct {
	client *client.Client
}

func NewProviderIntegrationResource() resource.Resource {
	return &ProviderIntegrationResource{}
}

func (r *ProviderIntegrationResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_provider_integration"
}

func (r *ProviderIntegrationResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Manages a SecretVault Provider Integration connecting external platforms (Render, Vercel, etc.) for automated secret synchronization.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The unique UUID identifier of the integration.",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"workspace_id": schema.StringAttribute{
				Description: "The UUID of the parent workspace container.",
				Required:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"name": schema.StringAttribute{
				Description: "The name identifier for this integration connection.",
				Required:    true,
			},
			"provider_type": schema.StringAttribute{
				Description: "The external platform type: 'RENDER', 'VERCEL', 'GITHUB', 'AWS_SECRETS_MANAGER', or 'CLOUDFLARE'.",
				Required:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
				Validators: []validator.String{
					stringvalidator.OneOf("RENDER", "VERCEL", "GITHUB", "AWS_SECRETS_MANAGER", "CLOUDFLARE", "CUSTOM"),
				},
			},
			"config": schema.MapAttribute{
				Description: "Non-sensitive provider configuration map (e.g. project_id, team_id, region).",
				Optional:    true,
				ElementType: types.StringType,
			},
			"credentials": schema.MapAttribute{
				Description: "Sensitive provider authentication credentials map (e.g. api_key, access_token). Marked Sensitive: true.",
				Optional:    true,
				Sensitive:   true,
				ElementType: types.StringType,
			},
			"enabled": schema.BoolAttribute{
				Description: "Whether the integration is active.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(true),
			},
			"status": schema.StringAttribute{
				Description: "The health status of the connection (e.g. 'HEALTHY', 'PENDING', 'ERROR').",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the integration was created.",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the integration was last updated.",
				Computed:    true,
			},
		},
	}
}

func (r *ProviderIntegrationResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
	if req.ProviderData == nil {
		return
	}
	c, ok := req.ProviderData.(*client.Client)
	if !ok {
		resp.Diagnostics.AddError("Unexpected Resource Configure Type", fmt.Sprintf("Expected *client.Client, got: %T", req.ProviderData))
		return
	}
	r.client = c
}

func (r *ProviderIntegrationResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan ProviderIntegrationResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	configMap := make(map[string]string)
	if !plan.Config.IsNull() && !plan.Config.IsUnknown() {
		elements := make(map[string]types.String, len(plan.Config.Elements()))
		plan.Config.ElementsAs(ctx, &elements, false)
		for k, v := range elements {
			configMap[k] = v.ValueString()
		}
	}

	credentialsMap := make(map[string]string)
	if !plan.Credentials.IsNull() && !plan.Credentials.IsUnknown() {
		elements := make(map[string]types.String, len(plan.Credentials.Elements()))
		plan.Credentials.ElementsAs(ctx, &elements, false)
		for k, v := range elements {
			credentialsMap[k] = v.ValueString()
		}
	}

	createReq := client.CreateProviderIntegrationRequest{
		Name:         plan.Name.ValueString(),
		ProviderType: plan.ProviderType.ValueString(),
		Config:       configMap,
		Credentials:  credentialsMap,
	}

	integration, err := r.client.CreateProviderIntegration(ctx, plan.WorkspaceID.ValueString(), createReq)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to create Provider Integration", err)
		return
	}

	plan.ID = types.StringValue(integration.ID)
	plan.Status = types.StringValue(integration.Status)
	plan.CreatedAt = types.StringValue(integration.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	plan.UpdatedAt = types.StringValue(integration.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *ProviderIntegrationResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state ProviderIntegrationResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	integration, err := r.client.GetProviderIntegration(ctx, state.WorkspaceID.ValueString(), state.ID.ValueString())
	if err != nil {
		if client.IsNotFound(err) {
			resp.State.RemoveResource(ctx)
			return
		}
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Provider Integration", err)
		return
	}

	state.Name = types.StringValue(integration.Name)
	state.ProviderType = types.StringValue(integration.ProviderType)
	state.Status = types.StringValue(integration.Status)
	state.CreatedAt = types.StringValue(integration.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	state.UpdatedAt = types.StringValue(integration.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, state)
	resp.Diagnostics.Append(diags...)
}

func (r *ProviderIntegrationResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan ProviderIntegrationResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	configMap := make(map[string]string)
	if !plan.Config.IsNull() && !plan.Config.IsUnknown() {
		elements := make(map[string]types.String, len(plan.Config.Elements()))
		plan.Config.ElementsAs(ctx, &elements, false)
		for k, v := range elements {
			configMap[k] = v.ValueString()
		}
	}

	credentialsMap := make(map[string]string)
	if !plan.Credentials.IsNull() && !plan.Credentials.IsUnknown() {
		elements := make(map[string]types.String, len(plan.Credentials.Elements()))
		plan.Credentials.ElementsAs(ctx, &elements, false)
		for k, v := range elements {
			credentialsMap[k] = v.ValueString()
		}
	}

	enabled := plan.Enabled.ValueBool()
	updateReq := client.UpdateProviderIntegrationRequest{
		Name:        plan.Name.ValueString(),
		Config:      configMap,
		Credentials: credentialsMap,
		Enabled:     &enabled,
	}

	integration, err := r.client.UpdateProviderIntegration(ctx, plan.WorkspaceID.ValueString(), plan.ID.ValueString(), updateReq)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to update Provider Integration", err)
		return
	}

	plan.Status = types.StringValue(integration.Status)
	plan.UpdatedAt = types.StringValue(integration.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *ProviderIntegrationResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state ProviderIntegrationResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	err := r.client.DeleteProviderIntegration(ctx, state.WorkspaceID.ValueString(), state.ID.ValueString())
	if err != nil && !client.IsNotFound(err) {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to delete Provider Integration", err)
		return
	}
}

func (r *ProviderIntegrationResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	// Import format: <workspace_id>/<integration_id>
	parts := strings.Split(req.ID, "/")
	if len(parts) != 2 || parts[0] == "" || parts[1] == "" {
		resp.Diagnostics.AddError(
			"Invalid Import ID",
			fmt.Sprintf("Expected import ID in format 'workspace_id/integration_id', got: %s", req.ID),
		)
		return
	}

	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("workspace_id"), parts[0])...)
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("id"), parts[1])...)
}
