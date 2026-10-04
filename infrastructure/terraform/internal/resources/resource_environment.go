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
	_ resource.Resource                = &EnvironmentResource{}
	_ resource.ResourceWithConfigure   = &EnvironmentResource{}
	_ resource.ResourceWithImportState = &EnvironmentResource{}
)

// EnvironmentResourceModel defines the Terraform state model for an environment.
type EnvironmentResourceModel struct {
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

// EnvironmentResource represents a SecretVault Environment resource.
type EnvironmentResource struct {
	client *client.Client
}

func NewEnvironmentResource() resource.Resource {
	return &EnvironmentResource{}
}

func (r *EnvironmentResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_environment"
}

func (r *EnvironmentResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Manages a SecretVault Environment (deployment tier) within a Project.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The unique UUID identifier of the environment.",
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
			"project_id": schema.StringAttribute{
				Description: "The UUID of the parent project.",
				Required:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"name": schema.StringAttribute{
				Description: "The human-readable display name of the environment (e.g. 'Production').",
				Required:    true,
			},
			"slug": schema.StringAttribute{
				Description: "The unique URL-friendly slug identifier (e.g. 'prod', 'staging', 'dev').",
				Required:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
				Validators: []validators.String{
					validators.SlugValidator(),
				},
			},
			"type": schema.StringAttribute{
				Description: "The environment tier classification: 'DEVELOPMENT', 'STAGING', 'PRODUCTION', or 'CUSTOM'.",
				Required:    true,
				Validators: []validator.String{
					stringvalidator.OneOf("DEVELOPMENT", "STAGING", "PRODUCTION", "CUSTOM", "PREVIEW", "LOCAL"),
				},
			},
			"is_protected": schema.BoolAttribute{
				Description: "Whether the environment requires elevated privileges and strict JIT/step-up approval for reveals.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(false),
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the environment was created.",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the environment was last updated.",
				Computed:    true,
			},
		},
	}
}

func (r *EnvironmentResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *EnvironmentResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan EnvironmentResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	createReq := client.CreateEnvironmentRequest{
		Name:        plan.Name.ValueString(),
		Slug:        plan.Slug.ValueString(),
		Type:        plan.Type.ValueString(),
		IsProtected: plan.IsProtected.ValueBool(),
	}

	env, err := r.client.CreateEnvironment(ctx, plan.WorkspaceID.ValueString(), plan.ProjectID.ValueString(), createReq)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to create Environment", err)
		return
	}

	plan.ID = types.StringValue(env.ID)
	plan.CreatedAt = types.StringValue(env.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	plan.UpdatedAt = types.StringValue(env.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *EnvironmentResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state EnvironmentResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	env, err := r.client.GetEnvironment(ctx, state.WorkspaceID.ValueString(), state.ProjectID.ValueString(), state.ID.ValueString())
	if err != nil {
		if client.IsNotFound(err) {
			resp.State.RemoveResource(ctx)
			return
		}
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Environment", err)
		return
	}

	state.Name = types.StringValue(env.Name)
	state.Slug = types.StringValue(env.Slug)
	state.Type = types.StringValue(env.Type)
	state.IsProtected = types.BoolValue(env.IsProtected)
	state.CreatedAt = types.StringValue(env.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	state.UpdatedAt = types.StringValue(env.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, state)
	resp.Diagnostics.Append(diags...)
}

func (r *EnvironmentResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan EnvironmentResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	isProtected := plan.IsProtected.ValueBool()
	updateReq := client.UpdateEnvironmentRequest{
		Name:        plan.Name.ValueString(),
		Type:        plan.Type.ValueString(),
		IsProtected: &isProtected,
	}

	env, err := r.client.UpdateEnvironment(ctx, plan.WorkspaceID.ValueString(), plan.ProjectID.ValueString(), plan.ID.ValueString(), updateReq)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to update Environment", err)
		return
	}

	plan.UpdatedAt = types.StringValue(env.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *EnvironmentResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state EnvironmentResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	err := r.client.DeleteEnvironment(ctx, state.WorkspaceID.ValueString(), state.ProjectID.ValueString(), state.ID.ValueString())
	if err != nil && !client.IsNotFound(err) {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to delete Environment", err)
		return
	}
}

func (r *EnvironmentResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	// Import format: <workspace_id>/<project_id>/<environment_id>
	parts := strings.Split(req.ID, "/")
	if len(parts) != 3 || parts[0] == "" || parts[1] == "" || parts[2] == "" {
		resp.Diagnostics.AddError(
			"Invalid Import ID",
			fmt.Sprintf("Expected import ID in format 'workspace_id/project_id/environment_id', got: %s", req.ID),
		)
		return
	}

	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("workspace_id"), parts[0])...)
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("project_id"), parts[1])...)
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("id"), parts[2])...)
}
