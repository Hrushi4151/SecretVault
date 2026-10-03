package com.secretvault.cli.security;

import com.secretvault.cli.client.ApiClientException;
import com.secretvault.cli.client.ErrorCode;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.StepUpDtos;
import com.secretvault.cli.output.ConsolePrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class StepUpSecurityTest {

    private SecretVaultApiClient apiClient;
    private ByteArrayOutputStream outStream;
    private ByteArrayOutputStream errStream;
    private ConsolePrinter printer;

    @BeforeEach
    void setUp() {
        apiClient = Mockito.mock(SecretVaultApiClient.class);
        outStream = new ByteArrayOutputStream();
        errStream = new ByteArrayOutputStream();
        printer = new ConsolePrinter(new PrintStream(outStream), new PrintStream(errStream));
        printer.setColorEnabled(false);
    }

    @Test
    @DisplayName("Step-Up TOTP flow successfully issues proof token")
    void stepUpTotpVerificationSuccess() {
        UUID wsId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID secId = UUID.randomUUID();

        StepUpDtos.StepUpContext ctx = StepUpDtos.StepUpContext.forSecret(wsId, projId, envId, secId);
        String challengeId = "chal-" + UUID.randomUUID();

        StepUpDtos.StepUpChallengeResponse challenge = new StepUpDtos.StepUpChallengeResponse(
                challengeId, StepUpDtos.StepUpAction.SECRET_REVEAL, List.of(StepUpDtos.StepUpFactor.TOTP), Instant.now().plusSeconds(120)
        );

        when(apiClient.createStepUpChallenge(any(StepUpDtos.StepUpChallengeRequest.class)))
                .thenReturn(challenge);

        when(apiClient.verifyStepUpTotp(eq(challengeId), eq("654321")))
                .thenReturn(new StepUpDtos.StepUpProofResponse("proof-token-xyz-123", StepUpDtos.StepUpAction.SECRET_REVEAL, StepUpDtos.StepUpFactor.TOTP, Instant.now().plusSeconds(60)));

        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("654321\n".getBytes(StandardCharsets.UTF_8)));
            String proof = StepUpAuthenticator.authenticateStepUp(apiClient, StepUpDtos.StepUpAction.SECRET_REVEAL, ctx, printer);

            assertThat(proof).isEqualTo("proof-token-xyz-123");
            // Verify proof token and OTP never leak to stdout/stderr
            assertThat(outStream.toString()).doesNotContain("proof-token-xyz-123");
            assertThat(errStream.toString()).doesNotContain("654321");
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    @DisplayName("Step-Up Password flow successfully issues proof token when TOTP not enabled")
    void stepUpPasswordVerificationSuccess() {
        UUID wsId = UUID.randomUUID();
        StepUpDtos.StepUpContext ctx = StepUpDtos.StepUpContext.forWorkspace(wsId);
        String challengeId = "chal-pwd-" + UUID.randomUUID();

        StepUpDtos.StepUpChallengeResponse challenge = new StepUpDtos.StepUpChallengeResponse(
                challengeId, StepUpDtos.StepUpAction.SESSION_REVOKE_ALL, List.of(StepUpDtos.StepUpFactor.PASSWORD), Instant.now().plusSeconds(120)
        );

        when(apiClient.createStepUpChallenge(any(StepUpDtos.StepUpChallengeRequest.class)))
                .thenReturn(challenge);

        when(apiClient.verifyStepUpPassword(eq(challengeId), eq("StrongPassword999!")))
                .thenReturn(new StepUpDtos.StepUpProofResponse("proof-pwd-token-888", StepUpDtos.StepUpAction.SESSION_REVOKE_ALL, StepUpDtos.StepUpFactor.PASSWORD, Instant.now().plusSeconds(60)));

        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("StrongPassword999!\n".getBytes(StandardCharsets.UTF_8)));
            String proof = StepUpAuthenticator.authenticateStepUp(apiClient, StepUpDtos.StepUpAction.SESSION_REVOKE_ALL, ctx, printer);

            assertThat(proof).isEqualTo("proof-pwd-token-888");
            assertThat(outStream.toString()).doesNotContain("StrongPassword999!");
            assertThat(errStream.toString()).doesNotContain("StrongPassword999!");
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    @DisplayName("Step-Up WebAuthn-only factor directs user safely to Web Console without downgrading security")
    void stepUpWebAuthnOnlyDirectsToWebConsole() {
        UUID wsId = UUID.randomUUID();
        StepUpDtos.StepUpContext ctx = StepUpDtos.StepUpContext.forWorkspace(wsId);
        String challengeId = "chal-webauthn-" + UUID.randomUUID();

        StepUpDtos.StepUpChallengeResponse challenge = new StepUpDtos.StepUpChallengeResponse(
                challengeId, StepUpDtos.StepUpAction.MFA_DISABLE, List.of(StepUpDtos.StepUpFactor.WEBAUTHN), Instant.now().plusSeconds(120)
        );

        when(apiClient.createStepUpChallenge(any(StepUpDtos.StepUpChallengeRequest.class)))
                .thenReturn(challenge);

        assertThatThrownBy(() -> StepUpAuthenticator.authenticateStepUp(apiClient, StepUpDtos.StepUpAction.MFA_DISABLE, ctx, printer))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("Web Console");
    }
}
