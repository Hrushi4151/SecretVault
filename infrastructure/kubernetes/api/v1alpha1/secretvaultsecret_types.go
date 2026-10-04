package v1alpha1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// VersionPolicy defines how versions of a secret should be tracked
// +kubebuilder:validation:Enum=LATEST;PINNED
type VersionPolicy string

const (
	VersionPolicyLatest VersionPolicy = "LATEST"
	VersionPolicyPinned VersionPolicy = "PINNED"
)

// CreationPolicy defines the lifecycle ownership of the synchronized Kubernetes Secret
// +kubebuilder:validation:Enum=Owner;Merge;None
type CreationPolicy string

const (
	CreationPolicyOwner CreationPolicy = "Owner"
	CreationPolicyMerge CreationPolicy = "Merge"
	CreationPolicyNone  CreationPolicy = "None"
)

// ConsumerType defines the workload classification for lease tracking
// +kubebuilder:validation:Enum=CONTAINER;SERVICE;WORKER;JOB
type ConsumerType string

const (
	ConsumerTypeContainer ConsumerType = "CONTAINER"
	ConsumerTypeService   ConsumerType = "SERVICE"
	ConsumerTypeWorker    ConsumerType = "WORKER"
	ConsumerTypeJob       ConsumerType = "JOB"
)

// ServiceAccountAuthRef defines the Kubernetes ServiceAccount to use for OIDC projected token exchange
type ServiceAccountAuthRef struct {
	// Name of the Kubernetes ServiceAccount in the same namespace
	// +kubebuilder:validation:Required
	// +kubebuilder:validation:MinLength=1
	// +kubebuilder:validation:MaxLength=253
	Name string `json:"name"`

	// Audiences requested in the projected token
	// +kubebuilder:default={"https://api.secretvault.io"}
	// +optional
	Audiences []string `json:"audiences,omitempty"`
}

// SecretVaultAuthSpec defines how the operator authenticates to SecretVault on behalf of this resource
type SecretVaultAuthSpec struct {
	// ServiceAccountRef references a local ServiceAccount for projected token OIDC exchange
	// +optional
	ServiceAccountRef *ServiceAccountAuthRef `json:"serviceAccountRef,omitempty"`

	// MachineIdentity name or UUID registered in SecretVault workspace
	// +optional
	MachineIdentity string `json:"machineIdentity,omitempty"`

	// ProviderId of the OIDC provider configured in SecretVault workspace
	// +optional
	ProviderId string `json:"providerId,omitempty"`
}

// SecretVaultTargetSpec defines the target Kubernetes Secret configuration
type SecretVaultTargetSpec struct {
	// Name of the target Kubernetes Secret. Defaults to the SecretVaultSecret resource name.
	// +kubebuilder:validation:MaxLength=253
	// +optional
	Name string `json:"name,omitempty"`

	// Key is the key name inside the Kubernetes Secret data map. Defaults to secretName.
	// +kubebuilder:validation:MaxLength=253
	// +optional
	Key string `json:"key,omitempty"`

	// CreationPolicy determines whether the operator sets owner references on the target Secret
	// +kubebuilder:default=Owner
	// +optional
	CreationPolicy CreationPolicy `json:"creationPolicy,omitempty"`
}

// SecretVaultLeaseSpec defines dynamic ephemeral lease parameters for runtime consumption
type SecretVaultLeaseSpec struct {
	// EnableLease determines whether an ephemeral SecretVault lease is registered for this secret
	// +kubebuilder:default=false
	// +optional
	EnableLease bool `json:"enableLease,omitempty"`

	// TTL defines the desired lease duration (e.g., 3600s, 1h)
	// +kubebuilder:default="3600s"
	// +kubebuilder:validation:Pattern=`^[0-9]+(s|m|h)$`
	// +optional
	TTL string `json:"ttl,omitempty"`

	// AutoRenew indicates whether the operator should periodically renew the lease before expiration
	// +kubebuilder:default=true
	// +optional
	AutoRenew bool `json:"autoRenew,omitempty"`

	// ConsumerType categorizes the consuming workload in the SecretVault registry
	// +kubebuilder:default=CONTAINER
	// +optional
	ConsumerType ConsumerType `json:"consumerType,omitempty"`
}

// SecretVaultSecretSpec defines the desired state of a single secret reference from SecretVault
type SecretVaultSecretSpec struct {
	// ServerUrl is the base URL of the SecretVault API server.
	// Defaults to in-cluster service endpoint.
	// +kubebuilder:default="http://secretvault-backend.secretvault.svc.cluster.local:8080"
	// +kubebuilder:validation:Pattern=`^https?://.*`
	// +optional
	ServerUrl string `json:"serverUrl,omitempty"`

	// Workspace is the slug or UUID of the target SecretVault workspace.
	// +kubebuilder:validation:Required
	// +kubebuilder:validation:MinLength=1
	// +kubebuilder:validation:MaxLength=64
	Workspace string `json:"workspace"`

	// Project is the slug or UUID of the target SecretVault project.
	// +kubebuilder:validation:Required
	// +kubebuilder:validation:MinLength=1
	// +kubebuilder:validation:MaxLength=64
	Project string `json:"project"`

	// Environment is the slug or tier of the target SecretVault environment (e.g. development, staging, production).
	// +kubebuilder:validation:Required
	// +kubebuilder:validation:MinLength=1
	// +kubebuilder:validation:MaxLength=64
	Environment string `json:"environment"`

	// SecretName is the key name of the secret in SecretVault (e.g., DB_PASSWORD, STRIPE_API_KEY).
	// +kubebuilder:validation:Required
	// +kubebuilder:validation:MinLength=1
	// +kubebuilder:validation:MaxLength=255
	SecretName string `json:"secretName"`

	// Version is an optional pinned historical version number to synchronize.
	// Must be positive when specified.
	// +kubebuilder:validation:Minimum=1
	// +optional
	Version *int32 `json:"version,omitempty"`

	// VersionPolicy specifies whether to track the latest active version or a pinned version.
	// +kubebuilder:default=LATEST
	// +optional
	VersionPolicy VersionPolicy `json:"versionPolicy,omitempty"`

	// Auth specifies authentication parameters for OIDC workload identity exchange.
	// +optional
	Auth *SecretVaultAuthSpec `json:"auth,omitempty"`

	// Target defines destination Kubernetes Secret details.
	// +optional
	Target *SecretVaultTargetSpec `json:"target,omitempty"`

	// RefreshInterval defines how frequently the operator re-evaluates the secret version.
	// +kubebuilder:default="1h"
	// +kubebuilder:validation:Pattern=`^[0-9]+(s|m|h)$`
	// +optional
	RefreshInterval string `json:"refreshInterval,omitempty"`

	// Lease defines optional ephemeral lease configuration.
	// +optional
	Lease *SecretVaultLeaseSpec `json:"lease,omitempty"`
}

// TargetSecretReference contains metadata about the synchronized Kubernetes Secret object
type TargetSecretReference struct {
	// Name of the target Secret
	Name string `json:"name,omitempty"`

	// Namespace of the target Secret
	Namespace string `json:"namespace,omitempty"`

	// UID of the target Secret
	UID string `json:"uid,omitempty"`

	// ResourceVersion of the target Secret
	ResourceVersion string `json:"resourceVersion,omitempty"`
}

// SecretVaultSecretStatus defines the observed state of SecretVaultSecret.
// NOTE: Plaintext secret values are NEVER exposed in status.
type SecretVaultSecretStatus struct {
	// Conditions represent the latest available observations of the resource state
	// +listType=map
	// +listMapKey=type
	// +optional
	Conditions []metav1.Condition `json:"conditions,omitempty"`

	// ObservedGeneration is the most recent generation observed by the controller
	// +optional
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`

	// LastSyncTime is the timestamp of the last successful synchronization
	// +optional
	LastSyncTime *metav1.Time `json:"lastSyncTime,omitempty"`

	// CurrentVersion is the active SecretVault secret version currently synchronized
	// +optional
	CurrentVersion *int32 `json:"currentVersion,omitempty"`

	// SecretFingerprint is the non-sensitive SHA-256 fingerprint hash of the synchronized version
	// +optional
	SecretFingerprint string `json:"secretFingerprint,omitempty"`

	// LeaseId is the active SecretVault lease UUID if leases are enabled
	// +optional
	LeaseId string `json:"leaseId,omitempty"`

	// LeaseExpiresAt is the expiration timestamp of the active lease
	// +optional
	LeaseExpiresAt *metav1.Time `json:"leaseExpiresAt,omitempty"`

	// TargetSecretRef references the generated Kubernetes Secret
	// +optional
	TargetSecretRef *TargetSecretReference `json:"targetSecretRef,omitempty"`
}

// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
// +kubebuilder:printcolumn:name="WORKSPACE",type="string",JSONPath=".spec.workspace",description="Target Workspace"
// +kubebuilder:printcolumn:name="PROJECT",type="string",JSONPath=".spec.project",description="Target Project"
// +kubebuilder:printcolumn:name="ENV",type="string",JSONPath=".spec.environment",description="Target Environment"
// +kubebuilder:printcolumn:name="SECRET",type="string",JSONPath=".spec.secretName",description="Secret Key Name"
// +kubebuilder:printcolumn:name="VERSION",type="integer",JSONPath=".status.currentVersion",description="Synchronized Version"
// +kubebuilder:printcolumn:name="SYNCED",type="string",JSONPath=".status.conditions[?(@.type=='Synced')].status",description="Sync Status"
// +kubebuilder:printcolumn:name="AGE",type="date",JSONPath=".metadata.creationTimestamp"

// SecretVaultSecret is the Schema for the secretvaultsecrets API.
// It defines a desired reference to a single secret managed in SecretVault.
type SecretVaultSecret struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec   SecretVaultSecretSpec   `json:"spec,omitempty"`
	Status SecretVaultSecretStatus `json:"status,omitempty"`
}

// +kubebuilder:object:root=true

// SecretVaultSecretList contains a list of SecretVaultSecret
type SecretVaultSecretList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []SecretVaultSecret `json:"items"`
}

func init() {
	SchemeBuilder.Register(&SecretVaultSecret{}, &SecretVaultSecretList{})
}
