package resources

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"

	"github.com/hashicorp/terraform-plugin-framework-validators/stringvalidator"
	"github.com/hashicorp/terraform-plugin-framework/path"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource/schema/int64planmodifier"
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
	_ resource.Resource                = &SecretResource{}
	_ resource.ResourceWithConfigure   = &SecretResource{}
	_ resource.ResourceWithImportState = &SecretResource{}
)

// SecretResourceModel defines the Terraform state model for a secret.
type SecretResourceModel struct {
	ID            types.String `tfsdk:"id"`
	WorkspaceID   types.String `tfsdk:"workspace_id"`
	ProjectID     types.String `tfsdk:"project_id"`
	EnvironmentID types.String `tfsdk:"environment_id"`
	Name          types.String `tfsdk:"name"`
	Value         types.String `tfsdk:"value"`
	ContentType   types.String `tfsdk:"content_type"`
	Comment       types.String `tfsdk:"comment"`
	Version       types.Int64  `tfsdk:"version"`
	Status        types.String `tfsdk:"status"`
	Fingerprint   types.String `tfsdk:"fingerprint"`
	CreatedAt     types.String `tfsdk:"created_at"`
	UpdatedAt     types.String `tfsdk:"updated_at"`
}

// SecretResource manages the lifecycle and versioning of secrets.
// Critical Security Invariant: Read() strictly queries metadata and never populates plaintext from remote APIs.
type SecretResource struct {
	client *client.Client
}

func NewSecretResource() resource.Resource {
	return &SecretResource{}
}

func (r *SecretResource) Metadata(_ context.Context, req resource.MetadataRequest, resp *resource.MetadataResponse) {
	resp.TypeName = req.ProviderTypeName + "_secret"
}

func (r *SecretResource) Schema(_ context.Context, _ resource.SchemaRequest, resp *resource.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Manages a versioned Secret in SecretVault with zero-plaintext Read protection and write-only security.",
		Attributes: map[string]schema.Attribute{
			"id": schema.StringAttribute{
				Description: "The unique identifier of the secret (UUID).",
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
			"name": schema.StringAttribute{
				Description: "The key name of the secret (e.g., 'DATABASE_URL', 'API_KEY').",
				Required:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.RequiresReplace(),
				},
				Validators: []validators.String{
					validators.SecretNameValidator(),
				},
			},
			"value": schema.StringAttribute{
				Description: "The secret payload. Marked Sensitive: true. Read() never fetches this value from the server.",
				Required:    true,
				Sensitive:   true,
			},
			"content_type": schema.StringAttribute{
				Description: "The data format classification: 'TEXT', 'JSON', 'YAML', 'BINARY', or 'ENV'. Default is 'TEXT'.",
				Optional:    true,
				Computed:    true,
				Default:     stringdefault.StaticString("TEXT"),
				Validators: []validator.String{
					stringvalidator.OneOf("TEXT", "JSON", "YAML", "BINARY", "ENV"),
				},
			},
			"comment": schema.StringAttribute{
				Description: "Optional audit trail note or description for this secret version.",
				Optional:    true,
			},
			"version": schema.Int64Attribute{
				Description: "The current immutable version number assigned by the SecretVault backend.",
				Computed:    true,
				PlanModifiers: []planmodifier.Int64{
					int64planmodifier.UseStateForUnknown(),
				},
			},
			"status": schema.StringAttribute{
				Description: "The lifecycle status of the secret (e.g. 'ACTIVE').",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"fingerprint": schema.StringAttribute{
				Description: "SHA256 hash digest of the written value, enabling drift detection without exposing plaintext.",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"created_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the secret was first created.",
				Computed:    true,
				PlanModifiers: []planmodifier.String{
					stringplanmodifier.UseStateForUnknown(),
				},
			},
			"updated_at": schema.StringAttribute{
				Description: "RFC3339 timestamp when the secret was last modified.",
				Computed:    true,
			},
		},
	}
}

func (r *SecretResource) Configure(_ context.Context, req resource.ConfigureRequest, resp *resource.ConfigureResponse) {
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

func (r *SecretResource) Create(ctx context.Context, req resource.CreateRequest, resp *resource.CreateResponse) {
	var plan SecretResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	secretVal := plan.Value.ValueString()
	createReq := client.CreateSecretRequest{
		Name:        plan.Name.ValueString(),
		Value:       secretVal,
		ContentType: plan.ContentType.ValueString(),
		Comment:     plan.Comment.ValueString(),
	}

	meta, err := r.client.CreateSecret(
		ctx,
		plan.WorkspaceID.ValueString(),
		plan.ProjectID.ValueString(),
		plan.EnvironmentID.ValueString(),
		createReq,
	)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to create Secret", err)
		return
	}

	// Compute fingerprint for drift detection
	hash := sha256.Sum256([]byte(secretVal))
	fingerprintHex := hex.EncodeToString(hash[:])

	plan.ID = types.StringValue(meta.ID)
	plan.Version = types.Int64Value(meta.Version)
	plan.Status = types.StringValue(meta.Status)
	plan.Fingerprint = types.StringValue(fingerprintHex)
	plan.CreatedAt = types.StringValue(meta.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	plan.UpdatedAt = types.StringValue(meta.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *SecretResource) Read(ctx context.Context, req resource.ReadRequest, resp *resource.ReadResponse) {
	var state SecretResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	// Read STRICTLY calls metadata endpoint - NEVER calls /reveal
	meta, err := r.client.GetSecretMetadata(
		ctx,
		state.WorkspaceID.ValueString(),
		state.ProjectID.ValueString(),
		state.EnvironmentID.ValueString(),
		state.ID.ValueString(),
	)
	if err != nil {
		if client.IsNotFound(err) {
			resp.State.RemoveResource(ctx)
			return
		}
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to read Secret metadata", err)
		return
	}

	state.Name = types.StringValue(meta.Name)
	state.ContentType = types.StringValue(meta.ContentType)
	state.Version = types.Int64Value(meta.Version)
	state.Status = types.StringValue(meta.Status)
	state.CreatedAt = types.StringValue(meta.CreatedAt.Format("2006-01-02T15:04:05Z07:00"))
	state.UpdatedAt = types.StringValue(meta.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))
	// Note: state.Value is preserved in state without fetching plaintext from server

	diags = resp.State.Set(ctx, state)
	resp.Diagnostics.Append(diags...)
}

func (r *SecretResource) Update(ctx context.Context, req resource.UpdateRequest, resp *resource.UpdateResponse) {
	var plan SecretResourceModel
	diags := req.Plan.Get(ctx, &plan)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	secretVal := plan.Value.ValueString()
	updateReq := client.UpdateSecretRequest{
		Value:       secretVal,
		ContentType: plan.ContentType.ValueString(),
		Comment:     plan.Comment.ValueString(),
	}

	meta, err := r.client.UpdateSecret(
		ctx,
		plan.WorkspaceID.ValueString(),
		plan.ProjectID.ValueString(),
		plan.EnvironmentID.ValueString(),
		plan.ID.ValueString(),
		updateReq,
	)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to update Secret", err)
		return
	}

	hash := sha256.Sum256([]byte(secretVal))
	fingerprintHex := hex.EncodeToString(hash[:])

	plan.Version = types.Int64Value(meta.Version)
	plan.Status = types.StringValue(meta.Status)
	plan.Fingerprint = types.StringValue(fingerprintHex)
	plan.UpdatedAt = types.StringValue(meta.UpdatedAt.Format("2006-01-02T15:04:05Z07:00"))

	diags = resp.State.Set(ctx, plan)
	resp.Diagnostics.Append(diags...)
}

func (r *SecretResource) Delete(ctx context.Context, req resource.DeleteRequest, resp *resource.DeleteResponse) {
	var state SecretResourceModel
	diags := req.State.Get(ctx, &state)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	err := r.client.DeleteSecret(
		ctx,
		state.WorkspaceID.ValueString(),
		state.ProjectID.ValueString(),
		state.EnvironmentID.ValueString(),
		state.ID.ValueString(),
	)
	if err != nil && !client.IsNotFound(err) {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to delete Secret", err)
		return
	}
}

func (r *SecretResource) ImportState(ctx context.Context, req resource.ImportStateRequest, resp *resource.ImportStateResponse) {
	// Import format: <workspace_id>/<project_id>/<environment_id>/<secret_id>
	parts := strings.Split(req.ID, "/")
	if len(parts) != 4 || parts[0] == "" || parts[1] == "" || parts[2] == "" || parts[3] == "" {
		resp.Diagnostics.AddError(
			"Invalid Import ID",
			fmt.Sprintf("Expected import ID in format 'workspace_id/project_id/environment_id/secret_id', got: %s", req.ID),
		)
		return
	}

	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("workspace_id"), parts[0])...)
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("project_id"), parts[1])...)
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("environment_id"), parts[2])...)
	resp.Diagnostics.Append(resp.State.SetAttribute(ctx, path.Root("id"), parts[3])...)
}
