package io.secretvault.sdk.auth;

import io.secretvault.sdk.exception.AuthenticationException;
import io.secretvault.sdk.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Iterates through an ordered chain of {@link CredentialsProvider} instances to locate valid authentication.
 */
public class CredentialProviderChain implements CredentialsProvider {

    private final List<CredentialsProvider> providers;
    private CredentialsProvider lastSuccessfulProvider;

    public CredentialProviderChain(CredentialsProvider... providers) {
        this(Arrays.asList(providers));
    }

    public CredentialProviderChain(List<CredentialsProvider> providers) {
        this.providers = providers != null ? new ArrayList<>(providers) : Collections.emptyList();
    }

    public static CredentialProviderChain defaultChain() {
        return new CredentialProviderChain(
                new EnvironmentTokenProvider()
        );
    }

    @Override
    public synchronized String getBearerToken() {
        if (lastSuccessfulProvider != null) {
            try {
                return lastSuccessfulProvider.getBearerToken();
            } catch (Exception ignored) {
                lastSuccessfulProvider = null;
            }
        }

        List<String> failures = new ArrayList<>();
        for (CredentialsProvider provider : providers) {
            try {
                String token = provider.getBearerToken();
                if (token != null && !token.trim().isEmpty()) {
                    this.lastSuccessfulProvider = provider;
                    return token;
                }
            } catch (Exception e) {
                failures.add(provider.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        throw new AuthenticationException(
                "Unable to obtain credentials from any provider in chain. Errors: " + String.join("; ", failures),
                ErrorCode.SV_AUTH_REQUIRED
        );
    }

    @Override
    public boolean supportsRefresh() {
        return lastSuccessfulProvider != null && lastSuccessfulProvider.supportsRefresh();
    }

    @Override
    public void refresh() {
        if (lastSuccessfulProvider != null) {
            lastSuccessfulProvider.refresh();
        }
    }

    @Override
    public void close() {
        for (CredentialsProvider p : providers) {
            try {
                p.close();
            } catch (Exception ignored) {}
        }
    }
}
