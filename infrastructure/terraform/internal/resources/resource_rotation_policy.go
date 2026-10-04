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
	_ resource.Resource                = &RotationPolicyResource{}
	_ resource.ResourceWithConfigure   = &RotationPolicyResource{}
	_ resource.ResourceWithImportState = &RotationPolicyResource{}
)

// RotationPolicyResourceModel defines the Terraform state model for a secret rotation policy.
type RotationPolicyResourceModel struct {
	ID                    types.String `tfsdk:"id"`
	WorkspaceID           types.String `tfsdk:"workspace_id"`
	ProjectID             types.String `tfsdk:"project_id"`
	EnvironmentID         types.String `tfsdk:"environment_id"`
	SecretID              types.String `tfsdk:"secret_id"`
	Enabled               types.Bool   `tfsdk:"enabled"`
	Strategy              types.String `tfsdk:"strategy"`
	SecretType            types.String `tfsdk:"secret_type"`
	IntervalSeconds       types.Int64  `tfsdk:"interval_seconds"`
	MinIntervalSeconds    types.Int64  `tfsdk:"min_interval_seconds"`
	MaxSecretAgeSeconds   types.Int64  `tfsdk:"max_secret_age_seconds"`
	RotationWindowSeconds types.Int64  `tfsdk:"rotation_window_seconds"`
	CronExpression        types.String `tfsdk:"cron_expression"`
	Timezone              types.String `tfsdk:"timezone"`
	MaxRetries            types.Int64  `tfsdk:"max_retries"`
	RetryBackoffSeconds   types.Int64  `tfsdk:"retry_backoff_seconds"`
	ValidationType        types.String `tfsdk:"validation_type"`
	RolloutStrategy       types.String `tfsdk:"rollout_strategy"`
	GracePeriodSeconds    types.Int64  `tfsdk:"grace_period_seconds"`
	AutoRevokePrevious    types.Bool   `tfsdk:"auto_revoke_previous"`
	AutoRollbackOnFailure types.Bool   `tfsdk:"auto_rollback_on_failure"`
	RequireApproval       types.Bool   `tfsdk:"require_approval"`
	RequireJitApproval    types.Bool   `tfsdk:"require_jit_approval"`
	SecretGeneratorConfig types.String `tfsdk:"secret_generator_config"`
	LastRotatedAt         types.String `tfsdk:"last_rotated_at"`
	NextRotationDueAt     types.String `tfsdk:"next_rotation_due_at"`
	CreatedAt             types.String `tfsdk:"created_at"`
	UpdatedAt             types.String `tfsdk:"updated_at"`
}

// RotationPolicyResource manages secret rotation policies.
type RotationPolicyResource struct {
	client *client.Client
}

func NewRotationPolicyResource() resource.Resource {
	return &RotationPolicyResource{}
}

func (r *RotationPolicyResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_rotation_policy"
}

func (r *RotationPolicyResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Manages an automated or scheduled Secret Rotation Policy in SecretVault. Strictly contains policy configuration metadata with zero plaintext secrets.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The unique identifier of the rotation policy (UUID).",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"workspace_id": schema.StringAttribute{
				Description: "The UUID of the parent workspace.",
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
			"environment_id": schema.StringAttribute{
				Description: "The UUID of the target environment.",
				Required:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"secret_id": schema.StringAttribute{
				Description: "The UUID of the secret governed by this rotation policy.",
				Required:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
				Validators: []validators.String{
					validators.UUIDValidator(),
				},
			},
			"enabled": schema.BoolAttribute{
				Description: "Whether automated rotation scheduling is active for this secret.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(true),
			},
			"strategy": schema.StringAttribute{
				Description: "The rotation trigger strategy: 'SCHEDULED', 'MANUAL', 'EVENT_DRIVEN', or 'EMERGENCY'. Default is 'SCHEDULED'.",
				Optional:    true,
				Computed:    true,
				Default:     stringdefault.StaticString("SCHEDULED"),
				Validators: []validator.String{
					stringvalidator.OneOf("SCHEDULED", "MANUAL", "EVENT_DRIVEN", "EMERGENCY"),
				},
			},
			"secret_type": schema.StringAttribute{
				Description: "The secret type classification: 'PASSWORD', 'API_KEY', 'DATABASE_CREDENTIAL', 'OAUTH_TOKEN', 'SSH_KEY', or 'TLS_CERTIFICATE'.",
				Optional:    true,
				Computed:    true,
				Default:     stringdefault.StaticString("PASSWORD"),
				Validators: []validator.String{
					stringvalidator.OneOf("PASSWORD", "API_KEY", "DATABASE_CREDENTIAL", "OAUTH_TOKEN", "SSH_KEY", "TLS_CERTIFICATE"),
				},
			},
			"interval_seconds": schema.Int64Attribute{
				Description: "Rotation cadence interval in seconds (e.g. 2592000 for 30 days).",
				Optional:    true,
				Computed:    true,
				Default:     int64default.StaticInt64(2592000),
			},
			"min_interval_seconds": schema.Int64Attribute{
				Description: "Minimum allowed interval between consecutive rotations in seconds (default 3600).",
				Optional:    true,
				Computed:    true,
				Default:     int64default.StaticInt64(3600),
			},
			"max_secret_age_seconds": schema.Int64Attribute{
				Description: "Hard maximum lifetime in seconds before forced rotation.",
				Optional:    true,
			},
			"rotation_window_seconds": schema.Int64Attribute{
				Description: "Permitted maintenance window duration for rotation execution in seconds (default 86400).",
				Optional:    true,
				Computed:    true,
				Default:     int64default.StaticInt64(86400),
			},
			"cron_expression": schema.StringAttribute{
				Description: "Optional standard 5-part cron schedule expression for calendar-based rotation.",
				Optional:    true,
			},
			"timezone": schema.StringAttribute{
				Description: "Timezone identifier for cron-based schedule evaluation (default 'UTC').",
				Optional:    true,
				Computed:    true,
				Default:     stringdefault.StaticString("UTC"),
			},
			"max_retries": schema.Int64Attribute{
				Description: "Maximum retry attempts upon rotation failure before marking job FAILED (default 3).",
				Optional:    true,
				Computed:    true,
				Default:     int64default.StaticInt64(3),
			},
			"retry_backoff_seconds": schema.Int64Attribute{
				Description: "Exponential backoff base delay between retries in seconds (default 300).",
				Optional:    true,
				Computed:    true,
				Default:     int64default.StaticInt64(300),
			},
			"validation_type": schema.StringAttribute{
				Description: "Pre-activation credential validation level: 'NONE', 'SYNTAX', 'AUTHENTICATION', 'FUNCTIONAL_READ', 'FUNCTIONAL_WRITE', or 'COMPREHENSIVE'.",
				Optional:    true,
				Computed:    true,
				Default:     stringdefault.StaticString("AUTHENTICATION"),
				Validators: []validator.String{
					stringvalidator.OneOf("NONE", "SYNTAX", "AUTHENTICATION", "FUNCTIONAL_READ", "FUNCTIONAL_WRITE", "COMPREHENSIVE"),
				},
			},
			"rollout_strategy": schema.StringAttribute{
				Description: "Target credential cutover rollout model: 'IMMEDIATE', 'STAGED', 'BLUE_GREEN', or 'CANARY'.",
				Optional:    true,
				Computed:    true,
				Default:     stringdefault.StaticString("STAGED"),
				Validators: []validator.String{
					stringvalidator.OneOf("IMMEDIATE", "STAGED", "BLUE_GREEN", "CANARY"),
				},
			},
			"grace_period_seconds": schema.Int64Attribute{
				Description: "Grace period in seconds where both new and previous credentials remain active before old-user revocation (default 1800).",
				Optional:    true,
				Computed:    true,
				Default:     int64default.StaticInt64(1800),
			},
			"auto_revoke_previous": schema.BoolAttribute{
				Description: "Automatically revoke previous credential versions once the grace period expires.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(true),
			},
			"auto_rollback_on_failure": schema.BoolAttribute{
				Description: "Automatically trigger safe rollback if validation or activation fails.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(true),
			},
			"require_approval": schema.BoolAttribute{
				Description: "Require dual-custody administrator sign-off before rotation execution.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(false),
			},
			"require_jit_approval": schema.BoolAttribute{
				Description: "Require active JIT elevated access ticket for manual rotation triggers.",
				Optional:    true,
				Computed:    true,
				Default:     booldefault.StaticBool(false),
			},
			"secret_generator_config": schema.StringAttribute{
				Description: "Optional JSON generator configuration (e.g. character sets, length, database host metadata).",
				Optional:    true,
			},
			"last_rotated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp of the last successful rotation.",
				Computed:    true,
			},
			"next_rotation_due_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the next rotation cycle is scheduled.",
				Computed:    true,
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the rotation policy was created.",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the rotation policy was last modified.",
				Computed:    true,
			},
		},
	}
}

func (r *RotationPolicyResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *RotationPolicyResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan RotationPolicyResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	createReq := client.CreateRotationPolicyRequest{
		SecretID:              plan.SecretID.ValueString(),
		Enabled:               plan.Enabled.ValueBool(),
		Strategy:              plan.Strategy.ValueString(),
		SecretType:            plan.SecretType.ValueString(),
		IntervalSeconds:       plan.IntervalSeconds.ValueInt64(),
		MinIntervalSeconds:    plan.MinIntervalSeconds.ValueInt64(),
		RotationWindowSeconds: plan.RotationWindowSeconds.ValueInt64(),
		CronExpression:        plan.CronExpression.ValueString(),
		Timezone:              plan.Timezone.ValueString(),
		MaxRetries:            int(plan.MaxRetries.ValueInt64()),
		RetryBackoffSeconds:   int(plan.RetryBackoffSeconds.ValueInt64()),
		ValidationType:        plan.ValidationType.ValueString(),
		RolloutStrategy:       plan.RolloutStrategy.ValueString(),
		GracePeriodSeconds:    plan.GracePeriodSeconds.ValueInt64(),
		AutoRevokePrevious:    plan.AutoRevokePrevious.ValueBool(),
		AutoRollbackOnFailure: plan.AutoRollbackOnFailure.ValueBool(),
		RequireApproval:       plan.RequireApproval.ValueBool(),
		RequireJitApproval:    plan.RequireJitApproval.ValueBool(),
		SecretGeneratorConfig: plan.SecretGeneratorConfig.ValueString(),
	}

	if !plan.MaxSecretAgeSeconds.IsNull() && !plan.MaxSecretAgeSeconds.IsUnknown() {
		val := plan.MaxSecretAgeSeconds.ValueInt64()
		createReq.MaxSecretAgeSeconds = &val
	}

	policy, err := r.client.CreateRotationPolicy(
		ctx,
		plan.WorkspaceID.ValueString(),
		plan.ProjectID.ValueString(),
		plan.EnvironmentID.ValueString(),
		plan.SecretID.ValueString(),
		createReq,
	)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to create Rotation Policy", err)
		return
	}

	plan.ID = types.StringValue(policy.ID)
	plan.CreatedAt = types.StringValue(policy.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	plan.UpdatedAt = types.StringValue(policy.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))
	if policy.LastRotatedAt != nil {
		plan.LastRotatedAt = types.StringValue(policy.LastRotatedAt.Format("2006-01-02T15:04:05Z07:00"))
	}
	if policy.NextRotationDueAt != nil {
		plan.NextRotationDueAt = types.StringValue(policy.NextRotationDueAt.Format("2006-01-02T15:04:05Z07:00"))
	}

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *RotationPolicyResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state RotationPolicyResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	policy, err := r.client.GetRotationPolicy(
		ctx,
		state.WorkspaceID.ValueString(),
		state.SecretID.ValueString(),
	)
	if err != nil {
		if client.IsNotFound(err) {
			resp.State.RemoveResource(ctx)
			return
		}
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Rotation Policy", err)
		return
	}

	state.ID = types.StringValue(policy.ID)
	state.Enabled = types.BoolValue(policy.Enabled)
	state.Strategy = types.StringValue(policy.Strategy)
	state.SecretType = types.StringValue(policy.SecretType)
	state.IntervalSeconds = types.Int64Value(policy.IntervalSeconds)
	state.MinIntervalSeconds = types.Int64Value(policy.MinIntervalSeconds)
	state.RotationWindowSeconds = types.Int64Value(policy.RotationWindowSeconds)
	state.Timezone = types.StringValue(policy.Timezone)
	state.MaxRetries = types.Int64Value(int64(policy.MaxRetries))
	state.RetryBackoffSeconds = types.Int64Value(int64(policy.RetryBackoffSeconds))
	state.ValidationType = types.StringValue(policy.ValidationType)
	state.RolloutStrategy = types.StringValue(policy.RolloutStrategy)
	state.GracePeriodSeconds = types.Int64Value(policy.GracePeriodSeconds)
	state.AutoRevokePrevious = types.BoolValue(policy.AutoRevokePrevious)
	state.AutoRollbackOnFailure = types.BoolValue(policy.AutoRollbackOnFailure)
	state.RequireApproval = types.BoolValue(policy.RequireApproval)
	state.RequireJitApproval = types.BoolValue(policy.RequireJitApproval)
	state.CreatedAt = types.StringValue(policy.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	state.UpdatedAt = types.StringValue(policy.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	if policy.MaxSecretAgeSeconds != nil {
		state.MaxSecretAgeSeconds = types.Int64Value(*policy.MaxSecretAgeSeconds)
	}
	if policy.CronExpression != "" {
		state.CronExpression = types.StringValue(policy.CronExpression)
	}
	if policy.SecretGeneratorConfig != "" {
		state.SecretGeneratorConfig = types.StringValue(policy.SecretGeneratorConfig)
	}
	if policy.LastRotatedAt != nil {
		state.LastRotatedAt = types.StringValue(policy.LastRotatedAt.Format("2006-01-02T15:04:05Z07:00"))
	}
	if policy.NextRotationDueAt != nil {
		state.NextRotationDueAt = types.StringValue(policy.NextRotationDueAt.Format("2006-01-02T15:04:05Z07:00"))
	}

	diags = resp.State.Set(ctx, state)
	resp.Diagnostics.Append(diags...)
}

func (r *RotationPolicyResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan RotationPolicyResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	updateReq := client.UpdateRotationPolicyRequest{
		Enabled:               plan.Enabled.ValueBool(),
		Strategy:              plan.Strategy.ValueString(),
		SecretType:            plan.SecretType.ValueString(),
		IntervalSeconds:       plan.IntervalSeconds.ValueInt64(),
		MinIntervalSeconds:    plan.MinIntervalSeconds.ValueInt64(),
		RotationWindowSeconds: plan.RotationWindowSeconds.ValueInt64(),
		CronExpression:        plan.CronExpression.ValueString(),
		Timezone:              plan.Timezone.ValueString(),
		MaxRetries:            int(plan.MaxRetries.ValueInt64()),
		RetryBackoffSeconds:   int(plan.RetryBackoffSeconds.ValueInt64()),
		ValidationType:        plan.ValidationType.ValueString(),
		RolloutStrategy:       plan.RolloutStrategy.ValueString(),
		GracePeriodSeconds:    plan.GracePeriodSeconds.ValueInt64(),
		AutoRevokePrevious:    plan.AutoRevokePrevious.ValueBool(),
		AutoRollbackOnFailure: plan.AutoRollbackOnFailure.ValueBool(),
		RequireApproval:       plan.RequireApproval.ValueBool(),
		RequireJitApproval:    plan.RequireJitApproval.ValueBool(),
		SecretGeneratorConfig: plan.SecretGeneratorConfig.ValueString(),
	}

	if !plan.MaxSecretAgeSeconds.IsNull() && !plan.MaxSecretAgeSeconds.IsUnknown() {
		val := plan.MaxSecretAgeSeconds.ValueInt64()
		updateReq.MaxSecretAgeSeconds = &val
	}

	policy, err := r.client.UpdateRotationPolicy(
		ctx,
		plan.WorkspaceID.ValueString(),
		plan.SecretID.ValueString(),
		updateReq,
	)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to update Rotation Policy", err)
		return
	}

	plan.UpdatedAt = types.StringValue(policy.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))
	if policy.LastRotatedAt != nil {
		plan.LastRotatedAt = types.StringValue(policy.LastRotatedAt.Format("2006-01-02T15:04:05Z07:00"))
	}
	if policy.NextRotationDueAt != nil {
		plan.NextRotationDueAt = types.StringValue(policy.NextRotationDueAt.Format("2006-01-02T15:04:05Z07:00"))
	}

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *RotationPolicyResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state RotationPolicyResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	err := r.client.DeleteRotationPolicy(
		ctx,
		state.WorkspaceID.ValueString(),
		state.SecretID.ValueString(),
	)
	if err != nil && !client.IsNotFound(err) {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to delete/disable Rotation Policy", err)
		return
	}
}

func (r *RotationPolicyResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	// Import format can be:
	// <workspace_id>/<project_id>/<environment_id>/<secret_id>
	// or <workspace_id>/<secret_id>
	parts := strings.Split(req.ID, "/")
	if len(parts) == 4 && parts[0] != "" && parts[1] != "" && parts[2] != "" && parts[3] != "" {
		resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("workspace_id"), parts[0])...)
		resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("project_id"), parts[1])...)
		resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("environment_id"), parts[2])...)
		resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("secret_id"), parts[3])...)
		return
	}

	if len(parts) == 2 && parts[0] != "" && parts[1] != "" {
		resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("workspace_id"), parts[0])...)
		resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("secret_id"), parts[1])...)
		return
	}

	resp.Diagnostics.AddError(
		"Invalid Import ID",
		fmt.Sprintf("Expected import ID in format 'workspace_id/project_id/environment_id/secret_id' or 'workspace_id/secret_id', got: %s", req.ID),
	)
}
