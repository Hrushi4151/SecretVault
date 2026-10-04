package provider

import (
	"context"
	"os"
	"strconv"
	"time"

	"github.com/hashicorp/terraform-plugin-framework/datasource"
	"github.com/hashicorp/terraform-plugin-framework/provider"
	"github.com/hashicorp/terraform-plugin-framework/provider/schema"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/types"
	"github.com/secretvault/terraform-provider-secretvault/internal/client"
	"github.com/secretvault/terraform-provider-secretvault/internal/datasources"
	"github.com/secretvault/terraform-provider-secretvault/internal/diagnostics"
	"github.com/secretvault/terraform-provider-secretvault/internal/resources"
)

var (
	_ provider.Provider = &SecretVaultProvider{}
)

// SecretVaultProviderModel defines the root provider configuration model.
type SecretVaultProviderModel struct {
	Address        types.String `tfsdk:"address"`
	Token          types.String `tfsdk:"token"`
	ClientID       types.String `tfsdk:"client_id"`
	ClientSecret   types.String `tfsdk:"client_secret"`
	WorkspaceID    types.String `tfsdk:"workspace_id"`
	TLS            *TLSModel    `tfsdk:"tls"`
	TimeoutSeconds types.Int64  `tfsdk:"timeout_seconds"`
	MaxRetries     types.Int64  `tfsdk:"max_retries"`
	RetryWaitMinMs types.Int64  `tfsdk:"retry_wait_min_ms"`
	RetryWaitMaxMs types.Int64  `tfsdk:"retry_wait_max_ms"`
}

// TLSModel defines nested TLS settings.
type TLSModel struct {
	CACert             types.String `tfsdk:"ca_cert"`
	CACertFile         types.String `tfsdk:"ca_cert_file"`
	InsecureSkipVerify types.Bool   `tfsdk:"insecure_skip_verify"`
	ServerName         types.String `tfsdk:"server_name"`
}

// SecretVaultProvider is the HashiCorp Terraform Plugin Framework provider.
type SecretVaultProvider struct {
	version string
}

// New instantiates a new SecretVault provider with a specific version.
func New(version string) func() provider.Provider {
	return func() provider.Provider {
		return &SecretVaultProvider{
			version: version,
		}
	}
}

func (p *SecretVaultProvider) Metadata(_ context.Context, _ provider.MetadataRequest, resp *provider.MetadataResponse) {
	resp.TypeName = "secretvault"
	resp.Version = p.version
}

func (p *SecretVaultProvider) Schema(_ context.Context, _ provider.SchemaRequest, resp *provider.SchemaResponse) {
	resp.Schema = schema.Schema{
		Description: "Production Terraform Provider for SecretVault DevSecOps Secret Management & Control Plane.",
		Attributes: map[string]schema.Attribute{
			"address": schema.StringAttribute{
				Description: "The base URL address of the SecretVault API server (e.g. 'https://vault.internal:8443'). May also be set via SECRET_VAULT_ADDR environment variable.",
				Optional:    true,
			},
			"token": schema.StringAttribute{
				Description: "The static API bearer token for authentication. Marked Sensitive: true. May also be set via SECRET_VAULT_TOKEN environment variable.",
				Optional:    true,
				Sensitive:   true,
			},
			"client_id": schema.StringAttribute{
				Description: "The Machine Identity / OIDC client identifier. May also be set via SECRET_VAULT_CLIENT_ID.",
				Optional:    true,
			},
			"client_secret": schema.StringAttribute{
				Description: "The Machine Identity / OIDC secret credential. Marked Sensitive: true. May also be set via SECRET_VAULT_CLIENT_SECRET.",
				Optional:    true,
				Sensitive:   true,
			},
			"workspace_id": schema.StringAttribute{
				Description: "Default workspace container ID to scope operations. May also be set via SECRET_VAULT_WORKSPACE_ID.",
				Optional:    true,
			},
			"timeout_seconds": schema.Int64Attribute{
				Description: "HTTP request timeout duration in seconds (default 30).",
				Optional:    true,
			},
			"max_retries": schema.Int64Attribute{
				Description: "Maximum retry attempts for transient network failures and 429/503 responses (default 3).",
				Optional:    true,
			},
			"retry_wait_min_ms": schema.Int64Attribute{
				Description: "Minimum exponential backoff wait duration in milliseconds (default 500).",
				Optional:    true,
			},
			"retry_wait_max_ms": schema.Int64Attribute{
				Description: "Maximum exponential backoff wait duration in milliseconds (default 5000).",
				Optional:    true,
			},
		},
		Blocks: map[string]schema.Block{
			"tls": schema.SingleNestedBlock{
				Description: "Custom TLS certificate validation and trust bundle settings.",
				Attributes: map[string]schema.Attribute{
					"ca_cert": schema.StringAttribute{
						Description: "Custom root CA certificate in PEM format.",
						Optional:    true,
					},
					"ca_cert_file": schema.StringAttribute{
						Description: "Path to a custom root CA certificate file.",
						Optional:    true,
					},
					"insecure_skip_verify": schema.BoolAttribute{
						Description: "Disable TLS certificate validation. UNSAFE for production (default false).",
						Optional:    true,
					},
					"server_name": schema.StringAttribute{
						Description: "Server name indicator (SNI) override for TLS handshake verification.",
						Optional:    true,
					},
				},
			},
		},
	}
}

func (p *SecretVaultProvider) Configure(ctx context.Context, req provider.ConfigureRequest, resp *provider.ConfigureResponse) {
	var config SecretVaultProviderModel
	diags := req.Config.Get(ctx, &config)
	resp.Diagnostics.Append(diags...)
	if resp.Diagnostics.HasError() {
		return
	}

	// Address resolution
	address := config.Address.ValueString()
	if address == "" {
		address = os.Getenv("SECRET_VAULT_ADDR")
		if address == "" {
			address = os.Getenv("SECRETVAULT_ADDR")
		}
		if address == "" {
			address = os.Getenv("SECRET_VAULT_SERVER_URL")
		}
	}
	if address == "" {
		address = "https://127.0.0.1:8443"
	}

	// Token resolution
	token := config.Token.ValueString()
	if token == "" {
		token = os.Getenv("SECRET_VAULT_TOKEN")
		if token == "" {
			token = os.Getenv("SECRETVAULT_TOKEN")
		}
	}

	// Machine / OIDC credentials resolution
	clientID := config.ClientID.ValueString()
	if clientID == "" {
		clientID = os.Getenv("SECRET_VAULT_CLIENT_ID")
		if clientID == "" {
			clientID = os.Getenv("SECRETVAULT_CLIENT_ID")
		}
	}

	clientSecret := config.ClientSecret.ValueString()
	if clientSecret == "" {
		clientSecret = os.Getenv("SECRET_VAULT_CLIENT_SECRET")
		if clientSecret == "" {
			clientSecret = os.Getenv("SECRETVAULT_CLIENT_SECRET")
		}
	}

	// Workspace resolution
	workspaceID := config.WorkspaceID.ValueString()
	if workspaceID == "" {
		workspaceID = os.Getenv("SECRET_VAULT_WORKSPACE_ID")
		if workspaceID == "" {
			workspaceID = os.Getenv("SECRETVAULT_WORKSPACE_ID")
		}
	}

	// Timeout
	timeoutSec := int64(30)
	if !config.TimeoutSeconds.IsNull() && config.TimeoutSeconds.ValueInt64() > 0 {
		timeoutSec = config.TimeoutSeconds.ValueInt64()
	}

	// Retries
	maxRetries := 3
	if !config.MaxRetries.IsNull() {
		maxRetries = int(config.MaxRetries.ValueInt64())
	}

	retryMinMs := int64(500)
	if !config.RetryWaitMinMs.IsNull() && config.RetryWaitMinMs.ValueInt64() > 0 {
		retryMinMs = config.RetryWaitMinMs.ValueInt64()
	}

	retryMaxMs := int64(5000)
	if !config.RetryWaitMaxMs.IsNull() && config.RetryWaitMaxMs.ValueInt64() > 0 {
		retryMaxMs = config.RetryWaitMaxMs.ValueInt64()
	}

	// TLS
	tlsCfg := client.TLSConfig{
		InsecureSkipVerify: false,
	}
	if config.TLS != nil {
		tlsCfg.CACert = config.TLS.CACert.ValueString()
		tlsCfg.CACertFile = config.TLS.CACertFile.ValueString()
		tlsCfg.InsecureSkipVerify = config.TLS.InsecureSkipVerify.ValueBool()
		tlsCfg.ServerName = config.TLS.ServerName.ValueString()
	}
	if !tlsCfg.InsecureSkipVerify {
		if envInsecure := os.Getenv("SECRET_VAULT_INSECURE_SKIP_VERIFY"); envInsecure != "" {
			if parsed, err := strconv.ParseBool(envInsecure); err == nil {
				tlsCfg.InsecureSkipVerify = parsed
			}
		}
	}
	if tlsCfg.CACertFile == "" {
		tlsCfg.CACertFile = os.Getenv("SECRET_VAULT_CA_CERT_FILE")
	}
	if tlsCfg.CACert == "" {
		tlsCfg.CACert = os.Getenv("SECRET_VAULT_CA_CERT")
	}

	// Setup AuthProvider
	var auth client.AuthProvider
	if token != "" {
		auth = &client.StaticTokenAuth{Token: token}
	} else if clientID != "" && clientSecret != "" {
		auth = &client.MachineIdentityAuth{
			WorkspaceID:  workspaceID,
			Audience:     clientID,
			SubjectToken: clientSecret,
		}
	}

	clientCfg := client.Config{
		BaseURL:            address,
		Auth:               auth,
		DefaultWorkspaceID: workspaceID,
		TLS:                tlsCfg,
		Timeout:            time.Duration(timeoutSec) * time.Second,
		MaxRetries:         maxRetries,
		RetryWaitMin:       time.Duration(retryMinMs) * time.Millisecond,
		RetryWaitMax:       time.Duration(retryMaxMs) * time.Millisecond,
	}

	apiClient, err := client.NewClient(clientCfg)
	if err != nil {
		diagnostics.AddErrorFromClient(&resp.Diagnostics, "Failed to initialize SecretVault API Client", err)
		return
	}

	resp.DataSourceData = apiClient
	resp.ResourceData = apiClient
}

func (p *SecretVaultProvider) Resources(_ context.Context) []func() resource.Resource {
	return []func() resource.Resource{
		resources.NewProjectResource,
		resources.NewEnvironmentResource,
		resources.NewSecretResource,
		resources.NewMachineIdentityResource,
		resources.NewProviderIntegrationResource,
	}
}

func (p *SecretVaultProvider) DataSources(_ context.Context) []func() datasource.DataSource {
	return []func() datasource.DataSource{
		datasources.NewWorkspaceDataSource,
		datasources.NewProjectDataSource,
		datasources.NewEnvironmentDataSource,
		datasources.NewSecretDataSource,
	}
}
