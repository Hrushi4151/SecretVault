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
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/int64default"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/planmodifier"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/stringdefault"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/stringplanmodifier"
	"github.com/hashicorp/terraform-plugin-framework/schema/validator"
	"github.com/hashicorp/terraform-plugin-framework/types"
	"github.com/secretvault/terraform-provider-secretvault/internal/client"
	"github.com/secretvault/terraform-provider-secretvault/internal/diagnostics"
	"github.com/secretvault/terraform-provider-secretvault/internal/validators"
)

var (
	_ resource.Resource                = &MachineIdentityResource{}
	_ resource.ResourceWithConfigure   = &MachineIdentityResource{}
	_ resource.ResourceWithImportState = &MachineIdentityResource{}
)

// MachineIdentityResourceModel defines the Terraform state model for a machine identity.
type MachineIdentityResourceModel struct {
	ID                 types.String `tfsdk:"id"`
	WorkspaceID        types.String `tfsdk:"workspace_id"`
	Name               types.String `tfsdk:"name"`
	Description        types.String `tfsdk:"description"`
	Role               types.String `tfsdk:"role"`
	MaxTokenTTLSeconds types.Int64  `tfsdk:"max_token_ttl_seconds"`
	Enabled            types.Bool   `tfsdk:"enabled"`
	Status             types.String `tfsdk:"status"`
	CreatedAt          types.String `tfsdk:"created_at"`
	UpdatedAt          types.String `tfsdk:"updated_at"`
}

// MachineIdentityResource manages machine/workload identity credentials.
type MachineIdentityResource struct {
	client *client.Client
}

func NewMachineIdentityResource() resource.Resource {
	return &MachineIdentityResource{}
}

func (r *MachineIdentityResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_machine_identity"
}

func (r *MachineIdentityResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Manages a SecretVault Machine Identity for automated workloads, CI/CD, and Kubernetes workloads.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The unique UUID identifier of the machine identity.",
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
				Description: "The human-readable name of the machine identity.",
				Required:    true,
			},
			"description": schema.StringAttribute{
				Description: "Optional description explaining the workload's purpose.",
				Optional:    true,
			},
			"role": schema.StringAttribute{
				Description: "The RBAC role assigned to this machine: 'WORKSPACE_MEMBER', 'WORKSPACE_ADMIN', or 'CUSTOM'.",
				Optional:    true,
				Computed:    true,
				Default:     stringdefault.StaticString("WORKSPACE_MEMBER"),
				Validators: []validator.String{
					stringvalidator.OneOf("WORKSPACE_MEMBER", "WORKSPACE_ADMIN", "WORKSPACE_OWNER", "READ_ONLY"),
				},
			},
			"max_token_ttl_seconds": schema.Int64Attribute{
				Description: "Maximum lifetime allowed for minted session tokens in seconds (default 3600).",
				Optional:    true,
				Computed:    true,
				Default:     int64default.StaticInt64(3600),
			},
			"enabled": schema.BoolAttribute{
				Description: "Whether this machine identity is currently enabled to exchange tokens.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(true),
			},
			"status": schema.StringAttribute{
				Description: "The operational status of the machine identity (e.g. 'ACTIVE', 'REVOKED').",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the machine identity was created.",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the machine identity was last updated.",
				Computed:    true,
			},
		},
	}
}

func (r *MachineIdentityResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *MachineIdentityResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan MachineIdentityResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	createReq := client.CreateMachineIdentityRequest{
		Name:               plan.Name.ValueString(),
		Description:        plan.Description.ValueString(),
		Role:               plan.Role.ValueString(),
		MaxTokenTtlSeconds: plan.MaxTokenTTLSeconds.ValueInt64(),
	}

	machine, err := r.client.CreateMachineIdentity(ctx, plan.WorkspaceID.ValueString(), createReq)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to create Machine Identity", err)
		return
	}

	plan.ID = types.StringValue(machine.ID)
	plan.Status = types.StringValue(machine.Status)
	plan.CreatedAt = types.StringValue(machine.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	plan.UpdatedAt = types.StringValue(machine.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *MachineIdentityResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state MachineIdentityResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	machine, err := r.client.GetMachineIdentity(ctx, state.WorkspaceID.ValueString(), state.ID.ValueString())
	if err != nil {
		if client.IsNotFound(err) {
			resp.State.RemoveResource(ctx)
			return
		}
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Machine Identity", err)
		return
	}

	state.Name = types.StringValue(machine.Name)
	state.Description = types.StringValue(machine.Description)
	state.Role = types.StringValue(machine.Role)
	state.MaxTokenTTLSeconds = types.Int64Value(machine.MaxTokenTtlSeconds)
	state.Status = types.StringValue(machine.Status)
	state.CreatedAt = types.StringValue(machine.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	state.UpdatedAt = types.StringValue(machine.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, state)
	resp.Diagnostics.Append(diags...)
}

func (r *MachineIdentityResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan MachineIdentityResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	enabled := plan.Enabled.ValueBool()
	updateReq := client.UpdateMachineIdentityRequest{
		Name:               plan.Name.ValueString(),
		Description:        plan.Description.ValueString(),
		Role:               plan.Role.ValueString(),
		MaxTokenTtlSeconds: plan.MaxTokenTTLSeconds.ValueInt64(),
		Enabled:            &enabled,
	}

	machine, err := r.client.UpdateMachineIdentity(ctx, plan.WorkspaceID.ValueString(), plan.ID.ValueString(), updateReq)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to update Machine Identity", err)
		return
	}

	plan.Status = types.StringValue(machine.Status)
	plan.UpdatedAt = types.StringValue(machine.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *MachineIdentityResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state MachineIdentityResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	err := r.client.DeleteMachineIdentity(ctx, state.WorkspaceID.ValueString(), state.ID.ValueString())
	if err != nil && !client.IsNotFound(err) {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to delete Machine Identity", err)
		return
	}
}

func (r *MachineIdentityResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	// Import format: <workspace_id>/<machine_id>
	parts := strings.Split(req.ID, "/")
	if len(parts) != 2 || parts[0] == "" || parts[1] == "" {
		resp.Diagnostics.AddError(
			"Invalid Import ID",
			fmt.Sprintf("Expected import ID in format 'workspace_id/machine_id', got: %s", req.ID),
		)
		return
	}

	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("workspace_id"), parts[0])...)
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("id"), parts[1])...)
}
