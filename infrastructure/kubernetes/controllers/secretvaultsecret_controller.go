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
	"k8s.io/client-go/tools/record"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
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
	ReasonBackendError       = "BackendUnavailable"

	// Default Reconcile Intervals
	DefaultSecretRefreshInterval = 1 * time.Hour
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

	// 2. Handle DeletionTimestamp if being deleted (no external cleanup required in 13.3)
	if !svs.ObjectMeta.DeletionTimestamp.IsZero() {
		return ctrl.Result{}, nil
	}

	// 3. Spec Validation
	if err := r.validateSpec(&svs.Spec); err != nil {
		log.Info("Configuration invalid", "reason", err.Error())
		r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonConfigInvalid, err.Error())
		r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonConfigInvalid, err.Error())
		r.emitEvent(&svs, corev1.EventTypeWarning, ReasonConfigInvalid, err.Error())
		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(false, time.Since(startTime))
		return ctrl.Result{}, nil // Non-retryable spec error
	}

	// 4. Resolve Workload Authentication & SecretVault Client
	svClient, err := r.resolveClient(ctx, &svs)
	if err != nil {
		log.Info("Authentication resolution failed", "reason", err.Error())
		r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonAuthFailed, err.Error())
		r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonAuthFailed, err.Error())
		r.emitEvent(&svs, corev1.EventTypeWarning, ReasonAuthFailed, err.Error())
		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(false, time.Since(startTime))
		return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
	}

	// 5. Query Secret Metadata from SecretVault Backend (zero plaintext values queried)
	meta, err := svClient.GetSecretMetadata(
		ctx,
		svs.Spec.Workspace,
		svs.Spec.Project,
		svs.Spec.Environment,
		svs.Spec.SecretName,
		svs.Spec.Version,
	)
	if err != nil {
		r.recordBackendRequest(true)
		if strings.Contains(err.Error(), "not found") {
			r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonSecretNotFound, "Secret reference does not exist in SecretVault")
			r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonSecretNotFound, "Secret not found")
			r.emitEvent(&svs, corev1.EventTypeWarning, ReasonSecretNotFound, "Referenced secret not found in SecretVault")
			_ = r.updateStatusIfChanged(ctx, &svs)
			r.recordMetric(false, time.Since(startTime))
			return ctrl.Result{RequeueAfter: 1 * time.Minute}, nil
		}
		if strings.Contains(err.Error(), "unauthorized") || strings.Contains(err.Error(), "forbidden") {
			r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonAuthDenied, "Workload not authorized to access secret scope")
			r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonAuthDenied, "Authorization denied")
			r.emitEvent(&svs, corev1.EventTypeWarning, ReasonAuthDenied, "SecretVault authorization denied for workload")
			_ = r.updateStatusIfChanged(ctx, &svs)
			r.recordMetric(false, time.Since(startTime))
			return ctrl.Result{}, nil // Avoid aggressive retries on permission denial
		}

		r.setCondition(&svs, ConditionReady, metav1.ConditionFalse, ReasonBackendError, "SecretVault backend communication error")
		r.setCondition(&svs, ConditionError, metav1.ConditionTrue, ReasonBackendError, err.Error())
		_ = r.updateStatusIfChanged(ctx, &svs)
		r.recordMetric(false, time.Since(startTime))
		return ctrl.Result{RequeueAfter: 15 * time.Second}, nil
	}
	r.recordBackendRequest(false)

	// 6. Establish Successful Desired State
	r.setCondition(&svs, ConditionReady, metav1.ConditionTrue, ReasonReferenceValidated, "SecretVault reference validated successfully")
	r.setCondition(&svs, ConditionSynced, metav1.ConditionTrue, ReasonReferenceValidated, "Desired state in sync with SecretVault metadata")
	r.setCondition(&svs, ConditionError, metav1.ConditionFalse, ReasonReferenceValidated, "No active errors")

	// Update status fields safely without secrets
	svs.Status.CurrentVersion = &meta.Version
	if meta.Fingerprint != "" {
		svs.Status.SecretFingerprint = meta.Fingerprint
	}
	now := metav1.Now()
	svs.Status.LastSyncTime = &now
	svs.Status.ObservedGeneration = svs.Generation

	// 7. Update Status Subresource Idempotently
	if err := r.updateStatusIfChanged(ctx, &svs); err != nil {
		log.Error(err, "Failed to update SecretVaultSecret status")
		return ctrl.Result{}, err
	}

	r.emitEvent(&svs, corev1.EventTypeNormal, ReasonReferenceValidated, fmt.Sprintf("Secret reference '%s' validated (v%d)", svs.Spec.SecretName, meta.Version))
	r.recordMetric(true, time.Since(startTime))

	// 8. Schedule Next Sync Interval
	refreshDuration := r.parseRefreshInterval(svs.Spec.RefreshInterval)
	return ctrl.Result{RequeueAfter: refreshDuration}, nil
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

// SetupWithManager registers the reconciler with the controller manager.
func (r *SecretVaultSecretReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&secretvaultv1alpha1.SecretVaultSecret{}).
		Complete(r)
}
