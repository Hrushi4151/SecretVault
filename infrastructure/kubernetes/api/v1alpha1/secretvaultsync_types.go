package v1alpha1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// RotationAction defines the remediation behavior when an upstream secret rotates
// +kubebuilder:validation:Enum=RestartWorkload;NotifyOnly;SyncOnly
type RotationAction string

const (
	RotationActionRestartWorkload RotationAction = "RestartWorkload"
	RotationActionNotifyOnly      RotationAction = "NotifyOnly"
	RotationActionSyncOnly        RotationAction = "SyncOnly"
)

// DriftPolicy defines how the operator reconciles unexpected changes made to the Kubernetes Secret
// +kubebuilder:validation:Enum=Enforce;DetectOnly;Ignore
type DriftPolicy string

const (
	DriftPolicyEnforce    DriftPolicy = "Enforce"
	DriftPolicyDetectOnly DriftPolicy = "DetectOnly"
	DriftPolicyIgnore     DriftPolicy = "Ignore"
)

// SyncFilterSpec defines filtering criteria for bulk secret synchronization
type SyncFilterSpec struct {
	// IncludeKeys is an explicit whitelist of SecretVault secret names to synchronize
	// +optional
	IncludeKeys []string `json:"includeKeys,omitempty"`

	// ExcludeKeys is a blacklist of SecretVault secret names to exclude from synchronization
	// +optional
	ExcludeKeys []string `json:"excludeKeys,omitempty"`

	// Tags is a list of metadata tags; only secrets matching all specified tags will be synced
	// +optional
	Tags []string `json:"tags,omitempty"`
}

// TargetSecretTemplate defines metadata templates for the generated Kubernetes Secret
type TargetSecretTemplate struct {
	// Type of the Kubernetes Secret (e.g. Opaque, kubernetes.io/tls)
	// +kubebuilder:default=Opaque
	// +optional
	Type string `json:"type,omitempty"`

	// Metadata to attach to the generated Kubernetes Secret
	// +optional
	Metadata *metav1.ObjectMeta `json:"metadata,omitempty"`
}

// SyncTargetSpec defines the target Kubernetes Secret destination for bulk synchronization
type SyncTargetSpec struct {
	// SecretName is the name of the destination Kubernetes Secret containing all synced keys
	// +kubebuilder:validation:Required
	// +kubebuilder:validation:MinLength=1
	// +kubebuilder:validation:MaxLength=253
	SecretName string `json:"secretName"`

	// CreationPolicy determines whether the operator sets owner references on the target Secret
	// +kubebuilder:default=Owner
	// +optional
	CreationPolicy CreationPolicy `json:"creationPolicy,omitempty"`

	// Template allows customization of the generated Secret metadata and type
	// +optional
	Template *TargetSecretTemplate `json:"template,omitempty"`
}

// RotationPolicySpec defines actions when upstream secrets undergo rotation
type RotationPolicySpec struct {
	// OnRotation specifies the action to perform upon detecting a secret rotation
	// +kubebuilder:default=SyncOnly
	// +optional
	OnRotation RotationAction `json:"onRotation,omitempty"`

	// WorkloadSelector selects Deployments, StatefulSets, or DaemonSets to trigger rolling restarts
	// +optional
	WorkloadSelector *metav1.LabelSelector `json:"workloadSelector,omitempty"`
}

// SecretVaultSyncSpec defines the desired state of bulk secret synchronization from SecretVault
type SecretVaultSyncSpec struct {
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

	// Auth specifies authentication parameters for OIDC workload identity exchange.
	// +optional
	Auth *SecretVaultAuthSpec `json:"auth,omitempty"`

	// Target defines destination Kubernetes Secret details.
	// +kubebuilder:validation:Required
	Target SyncTargetSpec `json:"target"`

	// Filter defines whitelists, blacklists, or tag filters for secrets in the environment.
	// +optional
	Filter *SyncFilterSpec `json:"filter,omitempty"`

	// RefreshInterval defines how frequently the operator re-evaluates all secrets in the scope.
	// +kubebuilder:default="15m"
	// +kubebuilder:validation:Pattern=`^[0-9]+(s|m|h)$`
	// +optional
	RefreshInterval string `json:"refreshInterval,omitempty"`

	// RotationPolicy defines automated workload actions on upstream rotation.
	// +optional
	RotationPolicy *RotationPolicySpec `json:"rotationPolicy,omitempty"`

	// DriftPolicy defines how the operator reconciles modifications to the target Kubernetes Secret.
	// +kubebuilder:default=Enforce
	// +optional
	DriftPolicy DriftPolicy `json:"driftPolicy,omitempty"`
}

// SecretVaultSyncStatus defines the observed state of bulk SecretVaultSync.
// NOTE: Plaintext secret values are NEVER exposed in status.
type SecretVaultSyncStatus struct {
	// Conditions represent the latest available observations of the sync state
	// +listType=map
	// +listMapKey=type
	// +optional
	Conditions []metav1.Condition `json:"conditions,omitempty"`

	// ObservedGeneration is the most recent generation observed by the controller
	// +optional
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`

	// LastSyncTime is the timestamp of the last successful bulk synchronization
	// +optional
	LastSyncTime *metav1.Time `json:"lastSyncTime,omitempty"`

	// SyncedSecretCount is the number of individual secrets currently synchronized
	// +optional
	SyncedSecretCount int32 `json:"syncedSecretCount,omitempty"`

	// DriftDetected indicates whether external modification to the target Secret was detected
	// +optional
	DriftDetected bool `json:"driftDetected,omitempty"`

	// DriftSummary provides non-sensitive diagnostic details regarding detected drift
	// +optional
	DriftSummary string `json:"driftSummary,omitempty"`

	// TargetSecretRef references the generated Kubernetes Secret
	// +optional
	TargetSecretRef *TargetSecretReference `json:"targetSecretRef,omitempty"`
}

// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
// +kubebuilder:printcolumn:name="WORKSPACE",type="string",JSONPath=".spec.workspace",description="Target Workspace"
// +kubebuilder:printcolumn:name="PROJECT",type="string",JSONPath=".spec.project",description="Target Project"
// +kubebuilder:printcolumn:name="ENV",type="string",JSONPath=".spec.environment",description="Target Environment"
// +kubebuilder:printcolumn:name="TARGET",type="string",JSONPath=".spec.target.secretName",description="Target K8s Secret"
// +kubebuilder:printcolumn:name="SECRETS",type="integer",JSONPath=".status.syncedSecretCount",description="Number of Synced Secrets"
// +kubebuilder:printcolumn:name="SYNCED",type="string",JSONPath=".status.conditions[?(@.type=='Synced')].status",description="Sync Status"
// +kubebuilder:printcolumn:name="DRIFT",type="boolean",JSONPath=".status.driftDetected",description="Drift State"
// +kubebuilder:printcolumn:name="AGE",type="date",JSONPath=".metadata.creationTimestamp"

// SecretVaultSync is the Schema for the secretvaultsyncs API.
// It defines a bulk environment secret synchronization policy into a Kubernetes namespace.
type SecretVaultSync struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	Spec   SecretVaultSyncSpec   `json:"spec,omitempty"`
	Status SecretVaultSyncStatus `json:"status,omitempty"`
}

// +kubebuilder:object:root=true

// SecretVaultSyncList contains a list of SecretVaultSync
type SecretVaultSyncList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []SecretVaultSync `json:"items"`
}

func init() {
	SchemeBuilder.Register(&SecretVaultSync{}, &SecretVaultSyncList{})
}
