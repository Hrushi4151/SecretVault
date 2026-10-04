package controllers

import (
	"context"
	"testing"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	"sigs.k8s.io/controller-runtime/pkg/client/fake"
	"sigs.k8s.io/controller-runtime/pkg/log/zap"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"

	secretvaultv1alpha1 "github.com/secretvault/operator/api/v1alpha1"
	"github.com/secretvault/operator/pkg/metrics"
)

func TestSecretVaultSyncReconciler_SpecValidation(t *testing.T) {
	tests := []struct {
		name      string
		spec      secretvaultv1alpha1.SecretVaultSyncSpec
		wantError bool
	}{
		{
			name: "Valid Sync Spec",
			spec: secretvaultv1alpha1.SecretVaultSyncSpec{
				Workspace:   "default",
				Project:     "payment-gateway",
				Environment: "production",
				Target: secretvaultv1alpha1.SyncTargetConfig{
					SecretName: "payment-env",
				},
			},
			wantError: false,
		},
		{
			name: "Missing Target SecretName",
			spec: secretvaultv1alpha1.SecretVaultSyncSpec{
				Workspace:   "default",
				Project:     "payment-gateway",
				Environment: "production",
				Target: secretvaultv1alpha1.SyncTargetConfig{
					SecretName: "",
				},
			},
			wantError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			reconciler := &SecretVaultSyncReconciler{}
			err := reconciler.validateSpec(&tt.spec)
			if (err != nil) != tt.wantError {
				t.Errorf("validateSpec() error = %v, wantError %v", err, tt.wantError)
			}
		})
	}
}

func TestSecretVaultSyncReconciler_ReconcileInvalidSpec(t *testing.T) {
	scheme := runtime.NewScheme()
	_ = secretvaultv1alpha1.AddToScheme(scheme)

	syncRes := &secretvaultv1alpha1.SecretVaultSync{
		ObjectMeta: metav1.ObjectMeta{
			Name:      "test-sync",
			Namespace: "default",
		},
		Spec: secretvaultv1alpha1.SecretVaultSyncSpec{
			Workspace: "",
		},
	}

	fakeClient := fake.NewClientBuilder().WithScheme(scheme).WithObjects(syncRes).WithStatusSubresource(syncRes).Build()
	recorder := record.NewFakeRecorder(10)

	reconciler := &SecretVaultSyncReconciler{
		Client:        fakeClient,
		Log:           zap.New(zap.UseDevMode(true)),
		Scheme:        scheme,
		Recorder:      recorder,
		ClientFactory: &MockClientFactory{},
		Metrics:       metrics.DefaultOperatorMetrics,
	}

	res, err := reconciler.Reconcile(context.Background(), reconcile.Request{
		NamespacedName: types.NamespacedName{
			Name:      "test-sync",
			Namespace: "default",
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.Requeue || res.RequeueAfter > 0 {
		t.Errorf("expected no requeue on invalid spec, got %v", res)
	}

	var updated secretvaultv1alpha1.SecretVaultSync
	if err := fakeClient.Get(context.Background(), types.NamespacedName{Name: "test-sync", Namespace: "default"}, &updated); err != nil {
		t.Fatalf("failed to get updated sync: %v", err)
	}

	foundErrorCondition := false
	for _, c := range updated.Status.Conditions {
		if c.Type == ConditionError && c.Status == metav1.ConditionTrue {
			foundErrorCondition = true
			break
		}
	}
	if !foundErrorCondition {
		t.Errorf("expected ConditionError to be True for invalid sync spec")
	}
}
