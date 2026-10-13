package com.secretvault.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.repository.engine.GitCloneUrlValidator;
import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.provider.GitHubRepositoryProviderAdapter;
import com.secretvault.repository.provider.RepositoryProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Repo Security: GitHub Provider Adapter & Authenticated Clone Suite")
class GitHubRepositoryProviderAdapterTest {

    private GitHubRepositoryProviderAdapter githubAdapter;
    private GitCloneUrlValidator urlValidator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        urlValidator = new GitCloneUrlValidator();
        githubAdapter = new GitHubRepositoryProviderAdapter(objectMapper, urlValidator);
    }

    @Test
    @DisplayName("GitHub provider adapter identifies as GITHUB and is available")
    void testProviderIdentification() {
        assertEquals(RepositoryProvider.GITHUB, githubAdapter.getProvider());
        assertTrue(githubAdapter.isAvailable());
    }

    @Test
    @DisplayName("Empty or null token validation returns false safely without exceptions")
    void testTokenValidationSafety() {
        assertFalse(githubAdapter.validateConnection(null));
        assertFalse(githubAdapter.validateConnection(""));
        assertFalse(githubAdapter.validateConnection("   "));
        assertTrue(githubAdapter.listRepositories(null).isEmpty());
    }

    @Test
    @DisplayName("Authenticated clone URL construction wraps GitHub URL with access token")
    void testBuildAuthenticatedCloneUrl() {
        String publicUrl = "https://github.com/secretvault/securevault.git";
        String token = "ghp_fakeToken1234567890abcdefghijklmnopqrst";

        String authUrl = githubAdapter.buildAuthenticatedCloneUrl(publicUrl, token);
        assertNotNull(authUrl);
        assertTrue(authUrl.startsWith("https://x-access-token:ghp_fakeToken1234567890abcdefghijklmnopqrst@github.com/"));
        assertTrue(authUrl.endsWith("secretvault/securevault.git"));
    }

    @Test
    @DisplayName("Authenticated clone URL construction on unauthenticated URL preserves clone URL")
    void testBuildAuthenticatedCloneUrlNoToken() {
        String publicUrl = "https://github.com/secretvault/securevault.git";
        String authUrl = githubAdapter.buildAuthenticatedCloneUrl(publicUrl, null);
        assertEquals(publicUrl, authUrl);
    }
}
