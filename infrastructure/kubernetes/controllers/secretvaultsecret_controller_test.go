package controllers

import (
	"context"
	"testing"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/client-go/tools/record"
	"sigs.k8s.io/controller-runtime/pkg/client/fake"
	"sigs.k8s.io/controller-runtime/pkg/log/zap"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"

	secretvaultv1alpha1 "github.com/secretvault/operator/api/v1alpha1"
	"github.com/secretvault/operator/pkg/auth"
	svclient "github.com/secretvault/operator/pkg/client"
	"github.com/secretvault/operator/pkg/metrics"
)

// MockClientFactory implements svclient.ClientFactory for testing.
type MockClientFactory struct {
	client *svclient.SecretVaultClient
	err    error
}

func (m *MockClientFactory) GetClient(
	ctx context.Context,
	namespace string,
	authOpts auth.TokenProviderOptions,
	providerID, issuer string,
) (*svclient.SecretVaultClient, error) {
	if m.err != nil {
		return nil, m.err
	}
	return m.client, nil
}

func TestSecretVaultSecretReconciler_SpecValidation(t *testing.T) {
	scheme := runtime.NewScheme()
	_ = secretvaultv1alpha1.AddToScheme(scheme)

	tests := []struct {
		name      string
		spec      secretvaultv1alpha1.SecretVaultSecretSpec
		wantError bool
	}{
		{
			name: "Valid Spec",
			spec: secretvaultv1alpha1.SecretVaultSecretSpec{
				Workspace:   "default",
				Project:     "payment-service",
				Environment: "production",
				SecretName:  "DB_PASSWORD",
			},
			wantError: false,
		},
		{
			name: "Missing Workspace",
			spec: secretvaultv1alpha1.SecretVaultSecretSpec{
				Workspace:   "",
				Project:     "payment-service",
				Environment: "production",
				SecretName:  "DB_PASSWORD",
			},
			wantError: true,
		},
		{
			name: "Pinned Version without Version Number",
			spec: secretvaultv1alpha1.SecretVaultSecretSpec{
				Workspace:     "default",
				Project:       "payment-service",
				Environment:   "production",
				SecretName:    "DB_PASSWORD",
				VersionPolicy: secretvaultv1alpha1.VersionPolicyPinned,
				Version:       nil,
			},
			wantError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			reconciler := &SecretVaultSecretReconciler{}
			err := reconciler.validateSpec(&tt.spec)
			if (err != nil) != tt.wantError {
				t.Errorf("validateSpec() error = %v, wantError %v", err, tt.wantError)
			}
		})
	}
}

func TestSecretVaultSecretReconciler_ReconcileInvalidSpec(t *testing.T) {
	scheme := runtime.NewScheme()
	_ = secretvaultv1alpha1.AddToScheme(scheme)

	svs := &secretvaultv1alpha1.SecretVaultSecret{
		ObjectMeta: metav1.ObjectMeta{
			Name:      "test-secret",
			Namespace: "default",
		},
		Spec: secretvaultv1alpha1.SecretVaultSecretSpec{
			Workspace: "", // Invalid: empty workspace
		},
	}

	fakeClient := fake.NewClientBuilder().WithScheme(scheme).WithObjects(svs).WithStatusSubresource(svs).Build()
	recorder := record.NewFakeRecorder(10)

	reconciler := &SecretVaultSecretReconciler{
		Client:        fakeClient,
		Log:           zap.New(zap.UseDevMode(true)),
		Scheme:        scheme,
		Recorder:      recorder,
		ClientFactory: &MockClientFactory{},
		Metrics:       metrics.DefaultOperatorMetrics,
	}

	res, err := reconciler.Reconcile(context.Background(), reconcile.Request{
		NamespacedName: types.NamespacedName{
			Name:      "test-secret",
			Namespace: "default",
		},
	})

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.Requeue || res.RequeueAfter > 0 {
		t.Errorf("expected no requeue on invalid spec, got %v", res)
	}

	// Verify status conditions
	var updated secretvaultv1alpha1.SecretVaultSecret
	if err := fakeClient.Get(context.Background(), types.NamespacedName{Name: "test-secret", Namespace: "default"}, &updated); err != nil {
		t.Fatalf("failed to get updated secret: %v", err)
	}

	foundErrorCondition := false
	for _, c := range updated.Status.Conditions {
		if c.Type == ConditionError && c.Status == metav1.ConditionTrue {
			foundErrorCondition = true
			break
		}
	}
	if !foundErrorCondition {
		t.Errorf("expected ConditionError to be True for invalid spec")
	}
}
