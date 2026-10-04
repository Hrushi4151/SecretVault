package main

import (
	"flag"
	"fmt"
	"os"
	"time"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/runtime"
	utilruntime "k8s.io/apimachinery/pkg/util/runtime"
	clientgoscheme "k8s.io/client-go/kubernetes/scheme"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/healthz"
	"sigs.k8s.io/controller-runtime/pkg/log/zap"
	metricsserver "sigs.k8s.io/controller-runtime/pkg/metrics/server"

	secretvaultv1alpha1 "github.com/secretvault/operator/api/v1alpha1"
	"github.com/secretvault/operator/controllers"
	svclient "github.com/secretvault/operator/pkg/client"
	"github.com/secretvault/operator/pkg/metrics"
)

var (
	scheme   = runtime.NewScheme()
	setupLog = ctrl.Log.WithName("setup")
)

func init() {
	utilruntime.Must(clientgoscheme.AddToScheme(scheme))
	utilruntime.Must(corev1.AddToScheme(scheme))
	utilruntime.Must(secretvaultv1alpha1.AddToScheme(scheme))
}

func main() {
	var metricsAddr string
	var probeAddr string
	var enableLeaderElection bool
	var leaderElectionID string
	var secretvaultAPIURL string
	var allowInsecureHTTP bool
	var concurrency int

	flag.StringVar(&metricsAddr, "metrics-bind-address", ":8080", "The address the metric endpoint binds to.")
	flag.StringVar(&probeAddr, "health-probe-bind-address", ":8081", "The address the probe endpoint binds to.")
	flag.BoolVar(&enableLeaderElection, "leader-elect", true, "Enable leader election for controller manager.")
	flag.StringVar(&leaderElectionID, "leader-election-id", "secretvault-operator-lock.secretvault.io", "The leader election ID.")
	flag.StringVar(&secretvaultAPIURL, "secretvault-api-url", "https://secretvault.internal:8443", "SecretVault control plane API URL.")
	flag.BoolVar(&allowInsecureHTTP, "allow-insecure-http", false, "Allow insecure HTTP for local testing only (localhost).")
	flag.IntVar(&concurrency, "concurrency", 1, "Concurrent worker threads per controller.")

	opts := zap.Options{
		Development: false,
	}
	opts.BindFlags(flag.CommandLine)
	flag.Parse()

	ctrl.SetLogger(zap.New(zap.UseFlagOptions(&opts)))

	setupLog.Info("Starting SecretVault Kubernetes Operator",
		"version", "v1alpha1",
		"apiURL", secretvaultAPIURL,
		"leaderElect", enableLeaderElection,
	)

	// 1. Initialize Client Configuration
	clientConfig := svclient.DefaultClientConfig()
	clientConfig.BaseURL = secretvaultAPIURL
	clientConfig.AllowInsecureHTTP = allowInsecureHTTP
	if err := clientConfig.Validate(); err != nil {
		setupLog.Error(err, "Invalid SecretVault client configuration")
		os.Exit(1)
	}

	clientFactory := svclient.NewClientFactory(clientConfig)

	// 2. Initialize Controller Manager
	mgr, err := ctrl.NewManager(ctrl.GetConfigOrDie(), ctrl.Options{
		Scheme: scheme,
		Metrics: metricsserver.Options{
			BindAddress: metricsAddr,
		},
		HealthProbeBindAddress: probeAddr,
		LeaderElection:         enableLeaderElection,
		LeaderElectionID:       leaderElectionID,
		LeaseDuration:          func() *time.Duration { d := 15 * time.Second; return &d }(),
		RenewDeadline:          func() *time.Duration { d := 10 * time.Second; return &d }(),
		RetryPeriod:            func() *time.Duration { d := 2 * time.Second; return &d }(),
	})
	if err != nil {
		setupLog.Error(err, "Unable to start controller manager")
		os.Exit(1)
	}

	// 3. Register Reconcilers
	if err = (&controllers.SecretVaultSecretReconciler{
		Client:        mgr.GetClient(),
		Log:           ctrl.Log.WithName("controllers").WithName("SecretVaultSecret"),
		Scheme:        mgr.GetScheme(),
		Recorder:      mgr.GetEventRecorderFor("secretvaultsecret-controller"),
		ClientFactory: clientFactory,
		Metrics:       metrics.DefaultOperatorMetrics,
	}).SetupWithManager(mgr); err != nil {
		setupLog.Error(err, "Unable to create controller", "controller", "SecretVaultSecret")
		os.Exit(1)
	}

	if err = (&controllers.SecretVaultSyncReconciler{
		Client:        mgr.GetClient(),
		Log:           ctrl.Log.WithName("controllers").WithName("SecretVaultSync"),
		Scheme:        mgr.GetScheme(),
		Recorder:      mgr.GetEventRecorderFor("secretvaultsync-controller"),
		ClientFactory: clientFactory,
		Metrics:       metrics.DefaultOperatorMetrics,
	}).SetupWithManager(mgr); err != nil {
		setupLog.Error(err, "Unable to create controller", "controller", "SecretVaultSync")
		os.Exit(1)
	}

	// 4. Add Health & Readiness Probes
	if err := mgr.AddHealthzCheck("healthz", healthz.Ping); err != nil {
		setupLog.Error(err, "Unable to set up health check")
		os.Exit(1)
	}
	if err := mgr.AddReadyzCheck("readyz", healthz.Ping); err != nil {
		setupLog.Error(err, "Unable to set up ready check")
		os.Exit(1)
	}

	// 5. Start Manager with Graceful Signal Handling
	setupLog.Info("Starting manager")
	if err := mgr.Start(ctrl.SetupSignalHandler()); err != nil {
		setupLog.Error(err, "Problem running manager")
		os.Exit(1)
	}
}
