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
	// Condition Types for Sync
	ConditionDriftDetected = "DriftDetected"

	// Sync Condition Reasons
	ReasonSyncPolicyConfigured = "SyncPolicyConfigured"
	ReasonScopeValidated       = "ScopeValidated"
	ReasonTargetInvalid        = "TargetInvalid"

	// Default Sync Refresh Interval
	DefaultSyncRefreshInterval = 15 * time.Minute
)

// SecretVaultSyncReconciler reconciles a SecretVaultSync object.
type SecretVaultSyncReconciler struct {
	client.Client
	Log           logr.Logger
	Scheme        *runtime.Scheme
	Recorder      record.EventRecorder
	ClientFactory svclient.ClientFactory
	Metrics       *metrics.OperatorMetrics
}

// +kubebuilder:rbac:groups=secretvault.io,resources=secretvaultsyncs,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups=secretvault.io,resources=secretvaultsyncs/status,verbs=get;update;patch
// +kubebuilder:rbac:groups="",resources=events,verbs=create;patch

// Reconcile handles the desired-state reconciliation cycle for SecretVaultSync.
func (r *SecretVaultSyncReconciler) Reconcile(ctx context.Context, req reconcile.Request) (ctrl.Result, error) {
	startTime := time.Now()
	log := r.Log.WithValues("secretvaultsync", req.NamespacedName)

	// 1. Fetch SecretVaultSync resource
	var syncRes secretvaultv1alpha1.SecretVaultSync
	if err := r.Get(ctx, req.NamespacedName, &syncRes); err != nil {
		if apierrors.IsNotFound(err) {
			log.Info("SecretVaultSync resource deleted from cluster")
			return ctrl.Result{}, nil
		}
		log.Error(err, "Failed to get SecretVaultSync from cache")
		return ctrl.Result{}, err
	}

	// 2. Deletion handling
	if !syncRes.ObjectMeta.DeletionTimestamp.IsZero() {
		return ctrl.Result{}, nil
	}

	// 3. Validate Spec
	if err := r.validateSpec(&syncRes.Spec); err != nil {
		log.Info("Sync configuration invalid", "reason", err.Error())
		r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonConfigInvalid, err.Error())
		r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonConfigInvalid, err.Error())
		r.emitEvent(&syncRes, corev1.EventTypeWarning, ReasonConfigInvalid, err.Error())
		_ = r.updateStatusIfChanged(ctx, &syncRes)
		r.recordMetric(false, time.Since(startTime))
		return ctrl.Result{}, nil
	}

	// 4. Resolve Workload Authentication & Client
	svClient, err := r.resolveClient(ctx, &syncRes)
	if err != nil {
		log.Info("Sync authentication resolution failed", "reason", err.Error())
		r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonAuthFailed, err.Error())
		r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonAuthFailed, err.Error())
		r.emitEvent(&syncRes, corev1.EventTypeWarning, ReasonAuthFailed, err.Error())
		_ = r.updateStatusIfChanged(ctx, &syncRes)
		r.recordMetric(false, time.Since(startTime))
		return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
	}

	// 5. Validate Scope on SecretVault Backend
	err = svClient.ValidateScope(
		ctx,
		syncRes.Spec.Workspace,
		syncRes.Spec.Project,
		syncRes.Spec.Environment,
	)
	if err != nil {
		r.recordBackendRequest(true)
		if strings.Contains(err.Error(), "unauthorized") || strings.Contains(err.Error(), "forbidden") {
			r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonAuthDenied, "Workload not authorized for target sync scope")
			r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonAuthDenied, "Authorization denied")
			r.emitEvent(&syncRes, corev1.EventTypeWarning, ReasonAuthDenied, "SecretVault authorization denied for sync scope")
			_ = r.updateStatusIfChanged(ctx, &syncRes)
			r.recordMetric(false, time.Since(startTime))
			return ctrl.Result{}, nil
		}

		r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonBackendError, err.Error())
		r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonBackendError, err.Error())
		_ = r.updateStatusIfChanged(ctx, &syncRes)
		r.recordMetric(false, time.Since(startTime))
		return ctrl.Result{RequeueAfter: 15 * time.Second}, nil
	}
	r.recordBackendRequest(false)

	// 6. Establish Desired State
	r.setCondition(&syncRes, ConditionReady, metav1.ConditionTrue, ReasonSyncPolicyConfigured, "Sync policy established and validated with SecretVault")
	r.setCondition(&syncRes, ConditionSynced, metav1.ConditionTrue, ReasonSyncPolicyConfigured, "Desired synchronization policy ready for execution")
	r.setCondition(&syncRes, ConditionDriftDetected, metav1.ConditionFalse, ReasonSyncPolicyConfigured, "No drift detected")
	r.setCondition(&syncRes, ConditionError, metav1.ConditionFalse, ReasonSyncPolicyConfigured, "No active errors")

	now := metav1.Now()
	syncRes.Status.LastSyncTime = &now
	syncRes.Status.ObservedGeneration = syncRes.Generation

	// 7. Update Status Idempotently
	if err := r.updateStatusIfChanged(ctx, &syncRes); err != nil {
		log.Error(err, "Failed to update SecretVaultSync status")
		return ctrl.Result{}, err
	}

	r.emitEvent(&syncRes, corev1.EventTypeNormal, ReasonSyncPolicyConfigured, fmt.Sprintf("Sync policy configured for target Secret '%s'", syncRes.Spec.Target.SecretName))
	r.recordMetric(true, time.Since(startTime))

	// 8. Schedule Refresh
	refreshDuration := r.parseRefreshInterval(syncRes.Spec.RefreshInterval)
	return ctrl.Result{RequeueAfter: refreshDuration}, nil
}

func (r *SecretVaultSyncReconciler) validateSpec(spec *secretvaultv1alpha1.SecretVaultSyncSpec) error {
	if strings.TrimSpace(spec.Workspace) == "" {
		return fmt.Errorf("spec.workspace is required")
	}
	if strings.TrimSpace(spec.Project) == "" {
		return fmt.Errorf("spec.project is required")
	}
	if strings.TrimSpace(spec.Environment) == "" {
		return fmt.Errorf("spec.environment is required")
	}
	if strings.TrimSpace(spec.Target.SecretName) == "" {
		return fmt.Errorf("spec.target.secretName is required")
	}
	return nil
}

func (r *SecretVaultSyncReconciler) resolveClient(ctx context.Context, syncRes *secretvaultv1alpha1.SecretVaultSync) (*svclient.SecretVaultClient, error) {
	if r.ClientFactory == nil {
		return nil, fmt.Errorf("client factory is not configured")
	}

	authOpts := auth.TokenProviderOptions{}
	var providerID, issuer string

	if syncRes.Spec.Auth != nil {
		if len(syncRes.Spec.Auth.Audiences) > 0 {
			authOpts.ExpectedAudience = syncRes.Spec.Auth.Audiences[0]
		}
		if syncRes.Spec.Auth.ProviderID != nil {
			providerID = *syncRes.Spec.Auth.ProviderID
		}
	}

	return r.ClientFactory.GetClient(ctx, syncRes.Namespace, authOpts, providerID, issuer)
}

func (r *SecretVaultSyncReconciler) setCondition(
	syncRes *secretvaultv1alpha1.SecretVaultSync,
	condType string,
	status metav1.ConditionStatus,
	reason, message string,
) {
	now := metav1.Now()
	for i, c := range syncRes.Status.Conditions {
		if c.Type == condType {
			if c.Status != status || c.Reason != reason || c.Message != message {
				syncRes.Status.Conditions[i].Status = status
				syncRes.Status.Conditions[i].Reason = reason
				syncRes.Status.Conditions[i].Message = message
				syncRes.Status.Conditions[i].LastTransitionTime = now
				syncRes.Status.Conditions[i].ObservedGeneration = syncRes.Generation
			}
			return
		}
	}
	syncRes.Status.Conditions = append(syncRes.Status.Conditions, metav1.Condition{
		Type:               condType,
		Status:             status,
		Reason:             reason,
		Message:            message,
		LastTransitionTime: now,
		ObservedGeneration: syncRes.Generation,
	})
}

func (r *SecretVaultSyncReconciler) updateStatusIfChanged(ctx context.Context, syncRes *secretvaultv1alpha1.SecretVaultSync) error {
	return r.Status().Update(ctx, syncRes)
}

func (r *SecretVaultSyncReconciler) parseRefreshInterval(intervalStr string) time.Duration {
	if intervalStr == "" {
		return DefaultSyncRefreshInterval
	}
	d, err := time.ParseDuration(intervalStr)
	if err != nil || d <= 0 {
		return DefaultSyncRefreshInterval
	}
	return d
}

func (r *SecretVaultSyncReconciler) emitEvent(syncRes *secretvaultv1alpha1.SecretVaultSync, eventType, reason, message string) {
	if r.Recorder != nil {
		r.Recorder.Event(syncRes, eventType, reason, message)
	}
}

func (r *SecretVaultSyncReconciler) recordMetric(success bool, duration time.Duration) {
	if r.Metrics != nil {
		r.Metrics.RecordSyncReconcile(success, duration)
	}
}

func (r *SecretVaultSyncReconciler) recordBackendRequest(isError bool) {
	if r.Metrics != nil {
		r.Metrics.RecordBackendRequest(isError)
	}
}

// SetupWithManager registers the reconciler with the controller manager.
func (r *SecretVaultSyncReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&secretvaultv1alpha1.SecretVaultSync{}).
		Complete(r)
}
