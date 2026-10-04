package main

import (
	"context"
	"flag"
	"log"

	"github.com/hashicorp/terraform-plugin-framework/providerserver"
	"github.com/secretvault/terraform-provider-secretvault/internal/provider"
)

// ProviderVersion is set during compilation via ldflags.
var ProviderVersion string = "1.0.0"

func main() {
	var debug bool

	flag.BoolVar(&debug, "debug", false, "set to true to run the provider with support for debuggers like delve")
	flag.Parse()

	opts := providerserver.ServeOpts{
		Address: "registry.terraform.io/secretvault/secretvault",
		Debug:   debug,
	}

	err := providerserver.Serve(context.Background(), provider.New(ProviderVersion), opts)
	if err != nil {
		log.Fatal(err.Error())
	}
}
