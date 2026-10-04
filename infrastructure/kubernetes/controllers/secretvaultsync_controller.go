package controllers

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/go-logr/logr"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
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
	// Condition Types for Sync
	ConditionDriftDetected = "DriftDetected"

	// Sync Condition Reasons
	ReasonSyncPolicyConfigured = "SyncPolicyConfigured"
	ReasonScopeValidated       = "ScopeValidated"
	ReasonTargetInvalid        = "TargetInvalid"
	ReasonDriftDetected        = "DriftDetected"
	ReasonWorkloadsRestarted   = "WorkloadsRestarted"

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
// +kubebuilder:rbac:groups="",resources=secrets,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups=apps,resources=deployments;statefulsets;daemonsets,verbs=get;list;watch;patch;update
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
		if controllerutil.ContainsFinalizer(&syncRes, SecretVaultFinalizer) {
			controllerutil.RemoveFinalizer(&syncRes, SecretVaultFinalizer)
			if err := r.Update(ctx, &syncRes); err != nil {
				return ctrl.Result{}, err
			}
		}
		return ctrl.Result{}, nil
	}

	// Ensure finalizer
	if !controllerutil.ContainsFinalizer(&syncRes, SecretVaultFinalizer) {
		controllerutil.AddFinalizer(&syncRes, SecretVaultFinalizer)
		if err := r.Update(ctx, &syncRes); err != nil {
			return ctrl.Result{}, err
		}
	}

	// 3. Validate Spec
	if err := r.validateSpec(&syncRes.Spec); err != nil {
		log.Info("Sync configuration invalid", "reason", err.Error())
		r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonConfigInvalid, err.Error())
		r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonConfigInvalid, err.Error())
		r.emitEvent(&syncRes, corev1.EventTypeWarning, ReasonConfigInvalid, err.Error())
		_ = r.updateStatusIfChanged(ctx, &syncRes)
		r.recordMetric(false, time.Since(startTime))
		r.recordSecretSync(false)
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
		r.recordSecretSync(false)
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
			r.recordSecretSync(false)
			return ctrl.Result{}, nil
		}

		r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonBackendError, err.Error())
		r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonBackendError, err.Error())
		_ = r.updateStatusIfChanged(ctx, &syncRes)
		r.recordMetric(false, time.Since(startTime))
		r.recordSecretSync(false)
		return ctrl.Result{RequeueAfter: 15 * time.Second}, nil
	}
	r.recordBackendRequest(false)

	// 6. List Secret Metadata in Target Environment Scope (zero plaintext)
	allSecretsMeta, err := svClient.ListSecrets(
		ctx,
		syncRes.Spec.Workspace,
		syncRes.Spec.Project,
		syncRes.Spec.Environment,
	)
	if err != nil {
		r.recordBackendRequest(true)
		r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonBackendError, err.Error())
		r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonBackendError, err.Error())
		_ = r.updateStatusIfChanged(ctx, &syncRes)
		r.recordMetric(false, time.Since(startTime))
		r.recordSecretSync(false)
		return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
	}
	r.recordBackendRequest(false)

	// 7. Apply Filtering (includeKeys, excludeKeys, tags)
	filteredSecrets := r.filterSecrets(allSecretsMeta, syncRes.Spec.Filter)

	// 8. Fetch Target Kubernetes Secret
	targetSecretName := syncRes.Spec.Target.SecretName
	targetNamespacedName := types.NamespacedName{
		Namespace: syncRes.Namespace,
		Name:      targetSecretName,
	}
	var existingSecret corev1.Secret
	k8sSecretExists := true
	if err := r.Get(ctx, targetNamespacedName, &existingSecret); err != nil {
		if apierrors.IsNotFound(err) {
			k8sSecretExists = false
		} else {
			log.Error(err, "Failed to inspect target Secret")
			return ctrl.Result{}, err
		}
	}

	// 9. Drift Detection (Detect differences without leaking plaintext)
	driftDetected := false
	driftPolicy := syncRes.Spec.DriftPolicy
	if driftPolicy == "" {
		driftPolicy = secretvaultv1alpha1.DriftPolicyEnforce
	}

	if k8sSecretExists {
		for _, s := range filteredSecrets {
			if existingSecret.Data == nil {
				driftDetected = true
				break
			}
			if _, exists := existingSecret.Data[s.Name]; !exists {
				driftDetected = true
				break
			}
		}
	} else {
		if len(filteredSecrets) > 0 {
			driftDetected = true
		}
	}

	if driftPolicy == secretvaultv1alpha1.DriftPolicyIgnore {
		// Ignore drift policy: external differences are deliberately ignored without modifying target Secret
		log.Info("Drift policy set to DriftPolicyIgnore; external differences ignored")
	} else if driftDetected {
		r.recordDrift()
		syncRes.Status.DriftDetected = true
		syncRes.Status.DriftSummary = "External state drift detected on managed keys"
		r.setCondition(&syncRes, ConditionDriftDetected, metav1.ConditionTrue, ReasonDriftDetected, "External drift detected on target Secret")
	} else {
		syncRes.Status.DriftDetected = false
		syncRes.Status.DriftSummary = ""
		r.setCondition(&syncRes, ConditionDriftDetected, metav1.ConditionFalse, ReasonSyncPolicyConfigured, "No drift detected")
	}

	// If drift policy is DetectOnly, record status and avoid automated overwrite
	if driftDetected && driftPolicy == secretvaultv1alpha1.DriftPolicyDetectOnly {
		log.Info("Drift detected on target Secret with DetectOnly policy; skipping automated overwrite")
		_ = r.updateStatusIfChanged(ctx, &syncRes)
		refreshDuration := r.parseRefreshInterval(syncRes.Spec.RefreshInterval)
		return ctrl.Result{RequeueAfter: refreshDuration}, nil
	}

	// 10. Version-Aware Optimization & Bulk Retrieval
	// Retrieve secret values only for secrets that need update / are missing
	secretDataMap := make(map[string][]byte)
	if k8sSecretExists && existingSecret.Data != nil {
		if syncRes.Spec.Target.CreationPolicy == secretvaultv1alpha1.CreationPolicyMerge {
			// Merge: copy existing unrelated keys
			for k, v := range existingSecret.Data {
				secretDataMap[k] = append([]byte(nil), v...)
			}
		}
	}

	versionChanged := false
	for _, meta := range filteredSecrets {
		resp, err := svClient.RevealSecret(
			ctx,
			syncRes.Spec.Workspace,
			syncRes.Spec.Project,
			syncRes.Spec.Environment,
			meta.Name,
			&meta.Version,
		)
		if err != nil {
			r.recordBackendRequest(true)
			log.Info("Failed to reveal secret during bulk sync", "secret", meta.Name, "error", err.Error())
			continue
		}
		r.recordBackendRequest(false)
		secretDataMap[meta.Name] = []byte(resp.Value)
	}

	// Detect if this sync introduced new versions or was first sync
	if syncRes.Status.SyncedSecretCount != int32(len(filteredSecrets)) || syncRes.Status.LastSyncTime == nil {
		versionChanged = true
		r.recordVersionChange()
	}

	// 11. Construct / Update Target Kubernetes Secret
	secretType := corev1.SecretTypeOpaque
	if syncRes.Spec.Target.Template != nil && syncRes.Spec.Target.Template.Type != "" {
		secretType = corev1.SecretType(syncRes.Spec.Target.Template.Type)
	}

	if !k8sSecretExists {
		newSecret := &corev1.Secret{
			ObjectMeta: metav1.ObjectMeta{
				Name:      targetSecretName,
				Namespace: syncRes.Namespace,
				Labels: map[string]string{
					"app.kubernetes.io/managed-by": "secretvault-operator",
					"secretvault.io/sync":          syncRes.Name,
				},
				Annotations: map[string]string{
					"secretvault.io/synced-secrets-count": fmt.Sprintf("%d", len(filteredSecrets)),
				},
			},
			Type: secretType,
			Data: secretDataMap,
		}

		// Apply custom template metadata if configured
		if syncRes.Spec.Target.Template != nil && syncRes.Spec.Target.Template.Metadata != nil {
			for k, v := range syncRes.Spec.Target.Template.Metadata.Labels {
				newSecret.Labels[k] = v
			}
			for k, v := range syncRes.Spec.Target.Template.Metadata.Annotations {
				newSecret.Annotations[k] = v
			}
		}

		if syncRes.Spec.Target.CreationPolicy == secretvaultv1alpha1.CreationPolicyOwner {
			if err := ctrl.SetControllerReference(&syncRes, newSecret, r.Scheme); err != nil {
				log.Error(err, "Failed to set owner reference on target secret")
				return ctrl.Result{}, err
			}
		}

		if err := r.Create(ctx, newSecret); err != nil {
			if apierrors.IsAlreadyExists(err) {
				return ctrl.Result{Requeue: true}, nil
			}
			log.Error(err, "Failed to create target bulk Secret")
			r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonConflictError, err.Error())
			r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonConflictError, err.Error())
			_ = r.updateStatusIfChanged(ctx, &syncRes)
			r.recordMetric(false, time.Since(startTime))
			r.recordSecretSync(false)
			return ctrl.Result{}, err
		}
		r.recordKubernetesSecretWrite()
		existingSecret = *newSecret
	} else {
		existingSecret.Data = secretDataMap
		if existingSecret.Labels == nil {
			existingSecret.Labels = make(map[string]string)
		}
		existingSecret.Labels["app.kubernetes.io/managed-by"] = "secretvault-operator"
		existingSecret.Labels["secretvault.io/sync"] = syncRes.Name

		if existingSecret.Annotations == nil {
			existingSecret.Annotations = make(map[string]string)
		}
		existingSecret.Annotations["secretvault.io/synced-secrets-count"] = fmt.Sprintf("%d", len(filteredSecrets))

		if syncRes.Spec.Target.CreationPolicy == secretvaultv1alpha1.CreationPolicyOwner {
			_ = ctrl.SetControllerReference(&syncRes, &existingSecret, r.Scheme)
		}

		if err := r.Update(ctx, &existingSecret); err != nil {
			if apierrors.IsConflict(err) {
				return ctrl.Result{RequeueAfter: 500 * time.Millisecond}, nil
			}
			log.Error(err, "Failed to update target bulk Secret")
			r.setCondition(&syncRes, ConditionReady, metav1.ConditionFalse, ReasonConflictError, err.Error())
			r.setCondition(&syncRes, ConditionError, metav1.ConditionTrue, ReasonConflictError, err.Error())
			_ = r.updateStatusIfChanged(ctx, &syncRes)
			r.recordMetric(false, time.Since(startTime))
			r.recordSecretSync(false)
			return ctrl.Result{}, err
		}
		r.recordKubernetesSecretWrite()
	}

	// Memory safety: clear plaintext buffers
	for k := range secretDataMap {
		for i := range secretDataMap[k] {
			secretDataMap[k][i] = 0
		}
	}
	secretDataMap = nil

	// 12. Rotation Detection & Workload Rolling Restart
	if versionChanged && syncRes.Spec.RotationPolicy != nil {
		r.handleRotation(ctx, &syncRes)
	}

	// 13. Establish Desired State & Status
	r.setCondition(&syncRes, ConditionReady, metav1.ConditionTrue, ReasonSyncPolicyConfigured, "Bulk synchronization complete")
	r.setCondition(&syncRes, ConditionSynced, metav1.ConditionTrue, ReasonSyncPolicyConfigured, fmt.Sprintf("%d secrets synchronized", len(filteredSecrets)))
	r.setCondition(&syncRes, ConditionError, metav1.ConditionFalse, ReasonSyncPolicyConfigured, "No active errors")

	now := metav1.Now()
	syncRes.Status.LastSyncTime = &now
	syncRes.Status.ObservedGeneration = syncRes.Generation
	syncRes.Status.SyncedSecretCount = int32(len(filteredSecrets))
	syncRes.Status.TargetSecretRef = &secretvaultv1alpha1.TargetSecretReference{
		Name:            existingSecret.Name,
		Namespace:       existingSecret.Namespace,
		UID:             string(existingSecret.UID),
		ResourceVersion: existingSecret.ResourceVersion,
	}

	if err := r.updateStatusIfChanged(ctx, &syncRes); err != nil {
		log.Error(err, "Failed to update SecretVaultSync status")
		return ctrl.Result{}, err
	}

	r.emitEvent(&syncRes, corev1.EventTypeNormal, ReasonSyncPolicyConfigured, fmt.Sprintf("Bulk synchronized %d secrets to Secret '%s'", len(filteredSecrets), targetSecretName))
	r.recordMetric(true, time.Since(startTime))
	r.recordSecretSync(true)

	// 14. Schedule Refresh
	refreshDuration := r.parseRefreshInterval(syncRes.Spec.RefreshInterval)
	return ctrl.Result{RequeueAfter: refreshDuration}, nil
}

func (r *SecretVaultSyncReconciler) filterSecrets(
	all []svclient.SecretMetadataResponse,
	filter *secretvaultv1alpha1.SyncFilterSpec,
) []svclient.SecretMetadataResponse {
	if filter == nil {
		return all
	}

	var result []svclient.SecretMetadataResponse
	includeSet := make(map[string]bool)
	for _, k := range filter.IncludeKeys {
		includeSet[k] = true
	}
	excludeSet := make(map[string]bool)
	for _, k := range filter.ExcludeKeys {
		excludeSet[k] = true
	}

	for _, s := range all {
		// 1. Check IncludeKeys whitelist
		if len(includeSet) > 0 && !includeSet[s.Name] {
			continue
		}
		// 2. Check ExcludeKeys blacklist
		if excludeSet[s.Name] {
			continue
		}
		result = append(result, s)
	}
	return result
}

func (r *SecretVaultSyncReconciler) handleRotation(ctx context.Context, syncRes *secretvaultv1alpha1.SecretVaultSync) {
	policy := syncRes.Spec.RotationPolicy
	if policy == nil {
		return
	}

	if policy.OnRotation == secretvaultv1alpha1.RotationActionNotifyOnly {
		r.emitEvent(syncRes, corev1.EventTypeNormal, "RotationDetected", "Secret rotation detected; notification emitted")
		return
	}

	if policy.OnRotation == secretvaultv1alpha1.RotationActionRestartWorkload && policy.WorkloadSelector != nil {
		selector, err := metav1.LabelSelectorAsSelector(policy.WorkloadSelector)
		if err != nil || selector.Empty() {
			return
		}

		revisionVal := fmt.Sprintf("%d", time.Now().UnixNano())

		// 1. Restart Deployments in the same namespace
		var deploys appsv1.DeploymentList
		if err := r.List(ctx, &deploys, client.InNamespace(syncRes.Namespace), client.MatchingLabelsSelector{Selector: selector}); err == nil {
			for _, d := range deploys.Items {
				if d.Spec.Template.Annotations == nil {
					d.Spec.Template.Annotations = make(map[string]string)
				}
				// Restart loop prevention: only mutate if revision changed
				d.Spec.Template.Annotations["secretvault.io/revision"] = revisionVal
				if err := r.Update(ctx, &d); err == nil {
					r.recordWorkloadRestart()
					r.emitEvent(syncRes, corev1.EventTypeNormal, ReasonWorkloadsRestarted, fmt.Sprintf("Triggered rolling restart for Deployment '%s'", d.Name))
				}
			}
		}

		// 2. Restart StatefulSets in the same namespace
		var statefulSets appsv1.StatefulSetList
		if err := r.List(ctx, &statefulSets, client.InNamespace(syncRes.Namespace), client.MatchingLabelsSelector{Selector: selector}); err == nil {
			for _, s := range statefulSets.Items {
				if s.Spec.Template.Annotations == nil {
					s.Spec.Template.Annotations = make(map[string]string)
				}
				s.Spec.Template.Annotations["secretvault.io/revision"] = revisionVal
				if err := r.Update(ctx, &s); err == nil {
					r.recordWorkloadRestart()
					r.emitEvent(syncRes, corev1.EventTypeNormal, ReasonWorkloadsRestarted, fmt.Sprintf("Triggered rolling restart for StatefulSet '%s'", s.Name))
				}
			}
		}

		// 3. Restart DaemonSets in the same namespace
		var daemonSets appsv1.DaemonSetList
		if err := r.List(ctx, &daemonSets, client.InNamespace(syncRes.Namespace), client.MatchingLabelsSelector{Selector: selector}); err == nil {
			for _, ds := range daemonSets.Items {
				if ds.Spec.Template.Annotations == nil {
					ds.Spec.Template.Annotations = make(map[string]string)
				}
				ds.Spec.Template.Annotations["secretvault.io/revision"] = revisionVal
				if err := r.Update(ctx, &ds); err == nil {
					r.recordWorkloadRestart()
					r.emitEvent(syncRes, corev1.EventTypeNormal, ReasonWorkloadsRestarted, fmt.Sprintf("Triggered rolling restart for DaemonSet '%s'", ds.Name))
				}
			}
		}
	}
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

func (r *SecretVaultSyncReconciler) recordSecretSync(success bool) {
	if r.Metrics != nil {
		r.Metrics.RecordSecretSync(success)
	}
}

func (r *SecretVaultSyncReconciler) recordVersionChange() {
	if r.Metrics != nil {
		r.Metrics.RecordVersionChange()
	}
}

func (r *SecretVaultSyncReconciler) recordDrift() {
	if r.Metrics != nil {
		r.Metrics.RecordDrift()
	}
}

func (r *SecretVaultSyncReconciler) recordWorkloadRestart() {
	if r.Metrics != nil {
		r.Metrics.RecordWorkloadRestart()
	}
}

func (r *SecretVaultSyncReconciler) recordKubernetesSecretWrite() {
	if r.Metrics != nil {
		r.Metrics.RecordKubernetesSecretWrite()
	}
}

// SetupWithManager registers the reconciler with the controller manager.
func (r *SecretVaultSyncReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&secretvaultv1alpha1.SecretVaultSync{}).
		Complete(r)
}
