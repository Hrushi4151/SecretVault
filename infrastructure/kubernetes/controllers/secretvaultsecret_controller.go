package controllers

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/go-logr/logr"
	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"

	secretvaultv1alpha1 "github.com/secretvault/operator/api/v1alpha1"
	"github.com/secretvault/operator/pkg/auth"
	svclient "github.com/secretvault/operator/pkg/client"
	"github.com/secretvault/operator/pkg/metrics"
)

const (
	// Condition Types
	ConditionReady  = "Ready"
	ConditionSynced = "Synced"
	ConditionError  = "Error"

	// Condition Reasons
	ReasonConfigInvalid      = "ConfigurationInvalid"
	ReasonAuthFailed         = "AuthenticationFailed"
	ReasonSecretNotFound     = "SecretNotFound"
	ReasonAuthDenied         = "AuthorizationDenied"
	ReasonReferenceValidated = "ReferenceValidated"
	ReasonSecretSynchronized = "SecretSynchronized"
	ReasonBackendError       = "BackendUnavailable"
	ReasonConflictError      = "KubernetesConflict"

	// Default Reconcile Intervals
	DefaultSecretRefreshInterval = 1 * time.Hour

	// Finalizer for Lease & Resource Cleanup
	SecretVaultFinalizer = "secretvault.io/finalizer"
)

// SecretVaultSecretReconciler reconciles a SecretVaultSecret object.
type SecretVaultSecretReconciler struct {
	client.Client
	Log           logr.Logger
	Scheme        *runtime.Scheme
	Recorder      record.EventRecorder
	ClientFactory svclient.ClientFactory
	Metrics       *metrics.OperatorMetrics
}

// +kubebuilder:rbac:groups=secretvault.io,resources=secretvaultsecrets,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups=secretvault.io,resources=secretvaultsecrets/status,verbs=get;update;patch
// +kubebuilder:rbac:groups="",resources=secrets,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups="",resources=events,verbs=create;patch

// Reconcile handles the desired-state reconciliation cycle for SecretVaultSecret.
func (r *SecretVaultSecretReconciler) Reconcile(ctx context.Context, req reconcile.Request) (ctrl.Result, error) {
	startTime := time.Now()
	log := r.Log.WithValues("secretvaultsecret", req.NamespacedName)

	// 1. Fetch the SecretVaultSecret instance from Kubernetes
	var svs secretvaultv1alpha1.SecretVaultSecret
	if err := r.Get(ctx, req.NamespacedName, &svs); err != nil {
		if apierrors.IsNotFound(err) {
			log.Info("SecretVaultSecret resource deleted from cluster")
			return ctrl.Result{}, nil
		}
		log.Error(err, "Failed to get SecretVaultSecret from cache")
		return ctrl.Result{}, err
	}

	// 2. Resolve Workload Authentication & SecretVault Client early for cleanup & sync
	svClient, err := r.resolveClient(ctx, &svs)
	if err != nil && svs.ObjectMeta.DeletionTimestamp.IsZero() {
		log.Info("Authentication resolution failed", "reason", err.Error())
		r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonAuthFailed, err.Error())
		r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonAuthFailed, err.Error())
		r.emitEvent(&svs, corev1.EventTypeWarning, ReasonAuthFailed, err.Error())
		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(false, time.Since(startTime))
		r.recordSecretSync(false)
		return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
	}

	// 3. Handle Deletion and Finalizer Cleanup
	if !svs.ObjectMeta.DeletionTimestamp.IsZero() {
		if controllerutil.ContainsFinalizer(&svs, SecretVaultFinalizer) {
			// Clean up active lease if present
			if svClient != nil && svs.Status.LeaseId != "" {
				_ = svClient.RevokeLease(ctx, svs.Spec.Workspace, svs.Status.LeaseId)
				r.recordLeaseRevoked()
			}
			controllerutil.RemoveFinalizer(&svs, SecretVaultFinalizer)
			if err := r.Update(ctx, &svs); err != nil {
				return ctrl.Result{}, err
			}
		}
		return ctrl.Result{}, nil
	}

	// Ensure finalizer is present
	if !controllerutil.ContainsFinalizer(&svs, SecretVaultFinalizer) {
		controllerutil.AddFinalizer(&svs, SecretVaultFinalizer)
		if err := r.Update(ctx, &svs); err != nil {
			return ctrl.Result{}, err
		}
	}

	// 4. Spec Validation
	if err := r.validateSpec(&svs.Spec); err != nil {
		log.Info("Configuration invalid", "reason", err.Error())
		r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonConfigInvalid, err.Error())
		r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonConfigInvalid, err.Error())
		r.emitEvent(&svs, corev1.EventTypeWarning, ReasonConfigInvalid, err.Error())
		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(false, time.Since(startTime))
		r.recordSecretSync(false)
		return ctrl.Result{}, nil // Non-retryable spec error
	}

	// 5. Query Secret Metadata First (Metadata-First Version Check, zero plaintext)
	var reqVersion *int
	if svs.Spec.VersionPolicy == secretvaultv1alpha1.VersionPolicyPinned && svs.Spec.Version != nil {
		v := int(*svs.Spec.Version)
		reqVersion = &v
	}

	meta, err := svClient.GetSecretMetadata(
		ctx,
		svs.Spec.Workspace,
		svs.Spec.Project,
		svs.Spec.Environment,
		svs.Spec.SecretName,
		reqVersion,
	)
	if err != nil {
		r.recordBackendRequest(true)
		if strings.Contains(err.Error(), "not found") {
			r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonSecretNotFound, "Secret or pinned version does not exist in SecretVault")
			r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonSecretNotFound, "Secret not found")
			r.emitEvent(&svs, corev1.EventTypeWarning, ReasonSecretNotFound, "Referenced secret not found in SecretVault")
			_ = r.updateStatusIfChanged(ctx, &svs)
			r.recordMetric(false, time.Since(startTime))
			r.recordSecretSync(false)
			return ctrl.Result{RequeueAfter: 1 * time.Minute}, nil
		}
		if strings.Contains(err.Error(), "unauthorized") || strings.Contains(err.Error(), "forbidden") {
			r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonAuthDenied, "Workload not authorized to access secret scope")
			r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonAuthDenied, "Authorization denied")
			r.emitEvent(&svs, corev1.EventTypeWarning, ReasonAuthDenied, "SecretVault authorization denied for workload")
			_ = r.updateStatusIfChanged(ctx, &svs)
			r.recordMetric(false, time.Since(startTime))
			r.recordSecretSync(false)
			return ctrl.Result{}, nil // Avoid aggressive retries on permission denial
		}

		r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonBackendError, "SecretVault backend communication error")
		r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonBackendError, err.Error())
		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(false, time.Since(startTime))
		r.recordSecretSync(false)
		return ctrl.Result{RequeueAfter: 15 * time.Second}, nil
	}
	r.recordBackendRequest(false)

	targetSecretName := svs.Name
	if svs.Spec.Target != nil && svs.Spec.Target.Name != "" {
		targetSecretName = svs.Spec.Target.Name
	}
	targetKey := svs.Spec.SecretName
	if svs.Spec.Target != nil && svs.Spec.Target.Key != "" {
		targetKey = svs.Spec.Target.Key
	}
	creationPolicy := secretvaultv1alpha1.CreationPolicyOwner
	if svs.Spec.Target != nil && svs.Spec.Target.CreationPolicy != "" {
		creationPolicy = svs.Spec.Target.CreationPolicy
	}

	// 6. Inspect Target Kubernetes Secret State
	targetNamespacedName := types.NamespacedName{
		Namespace: svs.Namespace,
		Name:      targetSecretName,
	}
	var existingSecret corev1.Secret
	k8sSecretExists := true
	if err := r.Get(ctx, targetNamespacedName, &existingSecret); err != nil {
		if apierrors.IsNotFound(err) {
			k8sSecretExists = false
		} else {
			log.Error(err, "Failed to read target Kubernetes Secret")
			return ctrl.Result{}, err
		}
	}

	targetHasKey := false
	if k8sSecretExists && existingSecret.Data != nil {
		if _, ok := existingSecret.Data[targetKey]; ok {
			targetHasKey = true
		}
	}

	metaVersionInt32 := int32(meta.Version)
	versionUnchanged := svs.Status.CurrentVersion != nil && *svs.Status.CurrentVersion == metaVersionInt32

	// 7. Version-Aware Optimization: Avoid reveal if secret is unchanged and Kubernetes Secret is present
	if k8sSecretExists && targetHasKey && versionUnchanged {
		// Version is unchanged; handle lease maintenance without retrieving plaintext
		if svs.Spec.Lease != nil && svs.Spec.Lease.EnableLease {
			r.reconcileLease(ctx, svClient, &svs, meta.ID)
		}

		r.setCondition(&svs, ConditionReady, metav1.ConditionTrue, ReasonSecretSynchronized, "Secret synchronized and up to date")
		r.setCondition(&svs, ConditionSynced, metav1.ConditionTrue, ReasonSecretSynchronized, "Target Secret matches SecretVault state")
		r.setCondition(&svs, ConditionError, metav1.ConditionFalse, ReasonSecretSynchronized, "No active errors")

		now := metav1.Now()
		svs.Status.LastSyncTime = &now
		svs.Status.ObservedGeneration = svs.Generation
		svs.Status.TargetSecretRef = &secretvaultv1alpha1.TargetSecretReference{
			Name:            existingSecret.Name,
			Namespace:       existingSecret.Namespace,
			UID:             string(existingSecret.UID),
			ResourceVersion: existingSecret.ResourceVersion,
		}

		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(true, time.Since(startTime))
		r.recordSecretSync(true)

		refreshDuration := r.parseRefreshInterval(svs.Spec.RefreshInterval)
		return ctrl.Result{RequeueAfter: refreshDuration}, nil
	}

	// 8. Version Changed or Target Missing -> Retrieve Plaintext via Authorized Reveal API
	if svs.Status.CurrentVersion != nil && *svs.Status.CurrentVersion != metaVersionInt32 {
		r.recordVersionChange()
	}

	secretResp, err := svClient.RevealSecret(
		ctx,
		svs.Spec.Workspace,
		svs.Spec.Project,
		svs.Spec.Environment,
		svs.Spec.SecretName,
		&meta.Version,
	)
	if err != nil {
		r.recordBackendRequest(true)
		log.Info("Secret reveal failed", "reason", err.Error())
		r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonAuthDenied, "Workload denied secret reveal access")
		r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonAuthDenied, err.Error())
		r.emitEvent(&svs, corev1.EventTypeWarning, ReasonAuthDenied, "SecretVault denied secret reveal authorization")
		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(false, time.Since(startTime))
		r.recordSecretSync(false)
		return ctrl.Result{RequeueAfter: 1 * time.Minute}, nil
	}
	r.recordBackendRequest(false)

	// Memory safety: buffer payload
	secretValueBytes := []byte(secretResp.Value)

	// 9. Synchronize to Kubernetes Secret according to CreationPolicy
	if !k8sSecretExists {
		newSecret := &corev1.Secret{
			ObjectMeta: metav1.ObjectMeta{
				Name:      targetSecretName,
				Namespace: svs.Namespace,
				Labels: map[string]string{
					"app.kubernetes.io/managed-by": "secretvault-operator",
					"secretvault.io/secret":        svs.Spec.SecretName,
				},
				Annotations: map[string]string{
					"secretvault.io/version": fmt.Sprintf("%d", meta.Version),
				},
			},
			Type: corev1.SecretTypeOpaque,
			Data: map[string][]byte{
				targetKey: secretValueBytes,
			},
		}

		if creationPolicy == secretvaultv1alpha1.CreationPolicyOwner {
			if err := ctrl.SetControllerReference(&svs, newSecret, r.Scheme); err != nil {
				log.Error(err, "Failed to set owner reference on target secret")
				return ctrl.Result{}, err
			}
		}

		if err := r.Create(ctx, newSecret); err != nil {
			if apierrors.IsAlreadyExists(err) {
				// Retry on race
				return ctrl.Result{Requeue: true}, nil
			}
			log.Error(err, "Failed to create target Kubernetes Secret")
			r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonConflictError, "Failed to create target Secret")
			r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonConflictError, err.Error())
			_ = r.updateStatusIfChanged(ctx, &svs)
			r.recordMetric(false, time.Since(startTime))
			r.recordSecretSync(false)
			return ctrl.Result{}, err
		}
		r.recordKubernetesSecretWrite()
		existingSecret = *newSecret
	} else {
		// Existing Secret: Handle Merge, Owner, and Orphan
		if existingSecret.Data == nil {
			existingSecret.Data = make(map[string][]byte)
		}

		if creationPolicy == secretvaultv1alpha1.CreationPolicyMerge {
			// MERGE policy: only mutate the managed key; preserve all other external keys
			existingSecret.Data[targetKey] = secretValueBytes
		} else {
			// Owner or None/Orphan: set the managed key
			existingSecret.Data[targetKey] = secretValueBytes
			if creationPolicy == secretvaultv1alpha1.CreationPolicyOwner {
				_ = ctrl.SetControllerReference(&svs, &existingSecret, r.Scheme)
			}
		}

		if existingSecret.Annotations == nil {
			existingSecret.Annotations = make(map[string]string)
		}
		existingSecret.Annotations["secretvault.io/version"] = fmt.Sprintf("%d", meta.Version)

		if err := r.Update(ctx, &existingSecret); err != nil {
			if apierrors.IsConflict(err) {
				log.Info("Target secret modified concurrently; requeueing for safe retry")
				return ctrl.Result{RequeueAfter: 500 * time.Millisecond}, nil
			}
			log.Error(err, "Failed to update target Kubernetes Secret")
			r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonConflictError, "Failed to update target Secret")
			r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonConflictError, err.Error())
			_ = r.updateStatusIfChanged(ctx, &svs)
			r.recordMetric(false, time.Since(startTime))
			r.recordSecretSync(false)
			return ctrl.Result{}, err
		}
		r.recordKubernetesSecretWrite()
	}

	// Memory safety: clear plaintext buffer
	for i := range secretValueBytes {
		secretValueBytes[i] = 0
	}
	secretValueBytes = nil

	// 10. Ephemeral Lease Lifecycle Integration
	if svs.Spec.Lease != nil && svs.Spec.Lease.EnableLease {
		r.reconcileLease(ctx, svClient, &svs, meta.ID)
	}

	// 11. Update Status with Non-Sensitive Metadata
	svs.Status.CurrentVersion = &metaVersionInt32
	if meta.Fingerprint != "" {
		svs.Status.SecretFingerprint = meta.Fingerprint
	}
	now := metav1.Now()
	svs.Status.LastSyncTime = &now
	svs.Status.ObservedGeneration = svs.Generation
	svs.Status.TargetSecretRef = &secretvaultv1alpha1.TargetSecretReference{
		Name:            existingSecret.Name,
		Namespace:       existingSecret.Namespace,
		UID:             string(existingSecret.UID),
		ResourceVersion: existingSecret.ResourceVersion,
	}

	r.setCondition(&svs, ConditionReady, metav1.ConditionTrue, ReasonSecretSynchronized, "Secret synchronized successfully")
	r.setCondition(&svs, ConditionSynced, metav1.ConditionTrue, ReasonSecretSynchronized, fmt.Sprintf("Synchronized version %d", meta.Version))
	r.setCondition(&svs, ConditionError, metav1.ConditionFalse, ReasonSecretSynchronized, "No active errors")

	if err := r.updateStatusIfChanged(ctx, &svs); err != nil {
		log.Error(err, "Failed to update SecretVaultSecret status")
		return ctrl.Result{}, err
	}

	r.emitEvent(&svs, corev1.EventTypeNormal, ReasonSecretSynchronized, fmt.Sprintf("Synchronized secret '%s' (v%d) to Secret '%s'", svs.Spec.SecretName, meta.Version, targetSecretName))
	r.recordMetric(true, time.Since(startTime))
	r.recordSecretSync(true)

	refreshDuration := r.parseRefreshInterval(svs.Spec.RefreshInterval)
	return ctrl.Result{RequeueAfter: refreshDuration}, nil
}

func (r *SecretVaultSecretReconciler) reconcileLease(
	ctx context.Context,
	svClient *svclient.SecretVaultClient,
	svs *secretvaultv1alpha1.SecretVaultSecret,
	secretId string,
) {
	ttlSeconds := int64(3600)
	if svs.Spec.Lease.TTL != "" {
		if d, err := time.ParseDuration(svs.Spec.Lease.TTL); err == nil && d > 0 {
			ttlSeconds = int64(d.Seconds())
		}
	}
	consumerType := string(svs.Spec.Lease.ConsumerType)
	if consumerType == "" {
		consumerType = "CONTAINER"
	}

	// Create lease if not established
	if svs.Status.LeaseId == "" {
		leaseResp, err := svClient.CreateLease(ctx, svs.Spec.Workspace, secretId, ttlSeconds, consumerType)
		if err == nil && leaseResp != nil {
			svs.Status.LeaseId = leaseResp.LeaseID
			exp := metav1.NewTime(leaseResp.ExpiresAt)
			svs.Status.LeaseExpiresAt = &exp
			r.recordLeaseCreated()
		}
		return
	}

	// Check if lease renewal is required (renew before expiration using safety margin)
	if svs.Spec.Lease.AutoRenew && svs.Status.LeaseExpiresAt != nil {
		timeRemaining := time.Until(svs.Status.LeaseExpiresAt.Time)
		renewalThreshold := time.Duration(ttlSeconds/2) * time.Second
		if renewalThreshold > 15*time.Minute {
			renewalThreshold = 15 * time.Minute
		}

		if timeRemaining <= renewalThreshold {
			leaseResp, err := svClient.RenewLease(ctx, svs.Spec.Workspace, svs.Status.LeaseId, ttlSeconds)
			if err == nil && leaseResp != nil {
				exp := metav1.NewTime(leaseResp.ExpiresAt)
				svs.Status.LeaseExpiresAt = &exp
				r.recordLeaseRenewed()
			}
		}
	}
}

func (r *SecretVaultSecretReconciler) validateSpec(spec *secretvaultv1alpha1.SecretVaultSecretSpec) error {
	if strings.TrimSpace(spec.Workspace) == "" {
		return fmt.Errorf("spec.workspace is required")
	}
	if strings.TrimSpace(spec.Project) == "" {
		return fmt.Errorf("spec.project is required")
	}
	if strings.TrimSpace(spec.Environment) == "" {
		return fmt.Errorf("spec.environment is required")
	}
	if strings.TrimSpace(spec.SecretName) == "" {
		return fmt.Errorf("spec.secretName is required")
	}
	if spec.VersionPolicy == secretvaultv1alpha1.VersionPolicyPinned {
		if spec.Version == nil || *spec.Version <= 0 {
			return fmt.Errorf("spec.version must be > 0 when versionPolicy is PINNED")
		}
	}
	return nil
}

func (r *SecretVaultSecretReconciler) resolveClient(ctx context.Context, svs *secretvaultv1alpha1.SecretVaultSecret) (*svclient.SecretVaultClient, error) {
	if r.ClientFactory == nil {
		return nil, fmt.Errorf("client factory is not configured")
	}

	authOpts := auth.TokenProviderOptions{}
	var providerID, issuer string

	if svs.Spec.Auth != nil {
		if len(svs.Spec.Auth.Audiences) > 0 {
			authOpts.ExpectedAudience = svs.Spec.Auth.Audiences[0]
		}
		if svs.Spec.Auth.ProviderID != nil {
			providerID = *svs.Spec.Auth.ProviderID
		}
	}

	return r.ClientFactory.GetClient(ctx, svs.Namespace, authOpts, providerID, issuer)
}

func (r *SecretVaultSecretReconciler) setCondition(
	svs *secretvaultv1alpha1.SecretVaultSecret,
	condType string,
	status metav1.ConditionStatus,
	reason, message string,
) {
	now := metav1.Now()
	for i, c := range svs.Status.Conditions {
		if c.Type == condType {
			if c.Status != status || c.Reason != reason || c.Message != message {
				svs.Status.Conditions[i].Status = status
				svs.Status.Conditions[i].Reason = reason
				svs.Status.Conditions[i].Message = message
				svs.Status.Conditions[i].LastTransitionTime = now
				svs.Status.Conditions[i].ObservedGeneration = svs.Generation
			}
			return
		}
	}
	svs.Status.Conditions = append(svs.Status.Conditions, metav1.Condition{
		Type:               condType,
		Status:             status,
		Reason:             reason,
		Message:            message,
		LastTransitionTime: now,
		ObservedGeneration: svs.Generation,
	})
}

func (r *SecretVaultSecretReconciler) updateStatusIfChanged(ctx context.Context, svs *secretvaultv1alpha1.SecretVaultSecret) error {
	return r.Status().Update(ctx, svs)
}

func (r *SecretVaultSecretReconciler) parseRefreshInterval(intervalStr string) time.Duration {
	if intervalStr == "" {
		return DefaultSecretRefreshInterval
	}
	d, err := time.ParseDuration(intervalStr)
	if err != nil || d <= 0 {
		return DefaultSecretRefreshInterval
	}
	return d
}

func (r *SecretVaultSecretReconciler) emitEvent(svs *secretvaultv1alpha1.SecretVaultSecret, eventType, reason, message string) {
	if r.Recorder != nil {
		r.Recorder.Event(svs, eventType, reason, message)
	}
}

func (r *SecretVaultSecretReconciler) recordMetric(success bool, duration time.Duration) {
	if r.Metrics != nil {
		r.Metrics.RecordSecretReconcile(success, duration)
	}
}

func (r *SecretVaultSecretReconciler) recordBackendRequest(isError bool) {
	if r.Metrics != nil {
		r.Metrics.RecordBackendRequest(isError)
	}
}

func (r *SecretVaultSecretReconciler) recordSecretSync(success bool) {
	if r.Metrics != nil {
		r.Metrics.RecordSecretSync(success)
	}
}

func (r *SecretVaultSecretReconciler) recordVersionChange() {
	if r.Metrics != nil {
		r.Metrics.RecordVersionChange()
	}
}

func (r *SecretVaultSecretReconciler) recordLeaseCreated() {
	if r.Metrics != nil {
		r.Metrics.RecordLeaseCreated()
	}
}

func (r *SecretVaultSecretReconciler) recordLeaseRenewed() {
	if r.Metrics != nil {
		r.Metrics.RecordLeaseRenewed()
	}
}

func (r *SecretVaultSecretReconciler) recordLeaseRevoked() {
	if r.Metrics != nil {
		r.Metrics.RecordLeaseRevoked()
	}
}

func (r *SecretVaultSecretReconciler) recordKubernetesSecretWrite() {
	if r.Metrics != nil {
		r.Metrics.RecordKubernetesSecretWrite()
	}
}

// SetupWithManager registers the reconciler with the controller manager.
func (r *SecretVaultSecretReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&secretvaultv1alpha1.SecretVaultSecret{}).
		Complete(r)
}
