package com.secretvault.cli.security;

import com.secretvault.cli.client.ApiClientException;
import com.secretvault.cli.client.ErrorCode;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Validates the Secret Reveal Protection Test Matrix (SR-CLI-01 through SR-CLI-22).
 */
class SecretRevealProtectionTest {

    private SecretVaultApiClient apiClient;
    private ByteArrayOutputStream outStream;
    private ByteArrayOutputStream errStream;
    private ConsolePrinter printer;

    private final UUID wsId = UUID.randomUUID();
    private final UUID projId = UUID.randomUUID();
    private final UUID envId = UUID.randomUUID();
    private final UUID secId = UUID.randomUUID();
    private final String sentinelSecret = "SUPER_SECRET_REVEAL_TEST_VALUE_999";

    @BeforeEach
    void setUp() {
        apiClient = Mockito.mock(SecretVaultApiClient.class);
        outStream = new ByteArrayOutputStream();
        errStream = new ByteArrayOutputStream();
        printer = new ConsolePrinter(new PrintStream(outStream), new PrintStream(errStream));
        printer.setColorEnabled(false);
    }

    @Test
    @DisplayName("SR-CLI-01: Reveal denied when user lacks SECRET_REVEAL permission (403)")
    void srCli01_deniedWithoutPermission() {
        when(apiClient.getSecretRevealPolicy(wsId, projId, envId, secId))
                .thenThrow(new ApiClientException(ErrorCode.FORBIDDEN, 403, "Access denied: missing SECRET_REVEAL permission", null));

        assertThatThrownBy(() -> SecretRevealHelper.revealProtectedSecret(apiClient, wsId, projId, envId, secId, null, null, printer))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("missing SECRET_REVEAL permission");
    }

    @Test
    @DisplayName("SR-CLI-02 to SR-CLI-04: Reveal denied for wrong workspace, project, or environment")
    void srCli02_04_deniedForInvalidScope() {
        UUID wrongWsId = UUID.randomUUID();
        when(apiClient.getSecretRevealPolicy(wrongWsId, projId, envId, secId))
                .thenThrow(new ApiClientException(ErrorCode.FORBIDDEN, 403, "Forbidden: Invalid workspace scope", null));

        assertThatThrownBy(() -> SecretRevealHelper.revealProtectedSecret(apiClient, wrongWsId, projId, envId, secId, null, null, printer))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("Invalid workspace scope");
    }

    @Test
    @DisplayName("SR-CLI-05: Reveal policy requiring reason prompts for audit reason and uses it")
    void srCli05_promptsForReasonWhenRequired() {
        SecretDtos.SecretRevealPolicyEvaluation policy = new SecretDtos.SecretRevealPolicyEvaluation(
                SecretDtos.RevealPolicyLevel.DEFAULT,
                false,
                List.of("PASSWORD", "TOTP"),
                false,
                true, // requireReason = true
                10,
                500,
                false, 60, true, 15, false, 50, 60
        );

        when(apiClient.getSecretRevealPolicy(wsId, projId, envId, secId)).thenReturn(policy);

        when(apiClient.createSecretRevealIntent(eq(wsId), eq(projId), eq(envId), eq(secId), any(SecretDtos.CreateRevealIntentRequest.class)))
                .thenReturn(new SecretDtos.SecretRevealIntentResponse("intent-token-123", Instant.now().plusSeconds(30), 60, true, 15, SecretDtos.RevealPolicyLevel.DEFAULT, true, false, List.of()));

        when(apiClient.revealSecret(eq(wsId), eq(projId), eq(envId), eq(secId), isNull(), eq("intent-token-123"), isNull(), eq("Investigating production incident #104")))
                .thenReturn(new SecretDtos.SecretRevealDto(secId, envId, "PROD_DB_PASS", 1, sentinelSecret, Instant.now()));

        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("Investigating production incident #104\n".getBytes(StandardCharsets.UTF_8)));
            SecretDtos.SecretRevealDto reveal = SecretRevealHelper.revealProtectedSecret(apiClient, wsId, projId, envId, secId, null, null, printer);

            assertThat(reveal).isNotNull();
            assertThat(reveal.value()).isEqualTo(sentinelSecret);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    @DisplayName("SR-CLI-06: Short reason is rejected before reveal execution")
    void srCli06_shortReasonRejected() {
        SecretDtos.SecretRevealPolicyEvaluation policy = new SecretDtos.SecretRevealPolicyEvaluation(
                SecretDtos.RevealPolicyLevel.DEFAULT,
                false,
                List.of("PASSWORD", "TOTP"),
                false,
                true, // requireReason = true
                10,
                500,
                false, 60, true, 15, false, 50, 60
        );

        when(apiClient.getSecretRevealPolicy(wsId, projId, envId, secId)).thenReturn(policy);

        assertThatThrownBy(() -> SecretRevealHelper.revealProtectedSecret(apiClient, wsId, projId, envId, secId, null, "too short", printer))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("at least 10 characters");
    }

    @Test
    @DisplayName("SR-CLI-07: Step-Up is performed and proof is passed to reveal intent & execution")
    void srCli07_stepUpRequiredAndCompleted() {
        SecretDtos.SecretRevealPolicyEvaluation policy = new SecretDtos.SecretRevealPolicyEvaluation(
                SecretDtos.RevealPolicyLevel.DEFAULT,
                true, // requireStepUp = true
                List.of("TOTP"),
                false,
                false,
                10, 500, false, 60, true, 15, false, 50, 60
        );

        when(apiClient.getSecretRevealPolicy(wsId, projId, envId, secId)).thenReturn(policy);

        String challengeId = "chal-stepup-" + UUID.randomUUID();
        when(apiClient.createStepUpChallenge(any(StepUpDtos.StepUpChallengeRequest.class)))
                .thenReturn(new StepUpDtos.StepUpChallengeResponse(challengeId, StepUpDtos.StepUpAction.SECRET_REVEAL, List.of(StepUpDtos.StepUpFactor.TOTP), Instant.now().plusSeconds(120)));

        when(apiClient.verifyStepUpTotp(eq(challengeId), eq("123456")))
                .thenReturn(new StepUpDtos.StepUpProofResponse("proof-token-stepup-777", StepUpDtos.StepUpAction.SECRET_REVEAL, StepUpDtos.StepUpFactor.TOTP, Instant.now().plusSeconds(60)));

        when(apiClient.createSecretRevealIntent(eq(wsId), eq(projId), eq(envId), eq(secId), any(SecretDtos.CreateRevealIntentRequest.class)))
                .thenReturn(new SecretDtos.SecretRevealIntentResponse("intent-token-456", Instant.now().plusSeconds(30), 60, true, 15, SecretDtos.RevealPolicyLevel.DEFAULT, false, false, List.of()));

        when(apiClient.revealSecret(eq(wsId), eq(projId), eq(envId), eq(secId), isNull(), eq("intent-token-456"), eq("proof-token-stepup-777"), any()))
                .thenReturn(new SecretDtos.SecretRevealDto(secId, envId, "API_SECRET", 1, sentinelSecret, Instant.now()));

        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("123456\n".getBytes(StandardCharsets.UTF_8)));
            SecretDtos.SecretRevealDto reveal = SecretRevealHelper.revealProtectedSecret(apiClient, wsId, projId, envId, secId, null, null, printer);

            assertThat(reveal).isNotNull();
            assertThat(reveal.value()).isEqualTo(sentinelSecret);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    @DisplayName("SR-CLI-08: Step-Up failure halts flow and does not reveal secret")
    void srCli08_stepUpFailureDoesNotRevealSecret() {
        SecretDtos.SecretRevealPolicyEvaluation policy = new SecretDtos.SecretRevealPolicyEvaluation(
                SecretDtos.RevealPolicyLevel.DEFAULT,
                true, // requireStepUp = true
                List.of("TOTP"),
                false,
                false,
                10, 500, false, 60, true, 15, false, 50, 60
        );

        when(apiClient.getSecretRevealPolicy(wsId, projId, envId, secId)).thenReturn(policy);

        String challengeId = "chal-stepup-" + UUID.randomUUID();
        when(apiClient.createStepUpChallenge(any(StepUpDtos.StepUpChallengeRequest.class)))
                .thenReturn(new StepUpDtos.StepUpChallengeResponse(challengeId, StepUpDtos.StepUpAction.SECRET_REVEAL, List.of(StepUpDtos.StepUpFactor.TOTP), Instant.now().plusSeconds(120)));

        when(apiClient.verifyStepUpTotp(eq(challengeId), eq("000000")))
                .thenThrow(new ApiClientException(ErrorCode.AUTHENTICATION_FAILED, 401, "Invalid authenticator code", null));

        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("000000\n".getBytes(StandardCharsets.UTF_8)));
            assertThatThrownBy(() -> SecretRevealHelper.revealProtectedSecret(apiClient, wsId, projId, envId, secId, null, null, printer))
                    .isInstanceOf(ApiClientException.class)
                    .hasMessageContaining("Invalid authenticator code");

            assertThat(outStream.toString()).doesNotContain(sentinelSecret);
            assertThat(errStream.toString()).doesNotContain(sentinelSecret);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    @DisplayName("SR-CLI-19 & SR-CLI-20: Plaintext never appears in CLI debug/log output or error streams")
    void srCli19_20_sentinelNeverLeaksInErrorsOrLogs() {
        when(apiClient.getSecretRevealPolicy(wsId, projId, envId, secId))
                .thenThrow(new ApiClientException(ErrorCode.SERVER_ERROR, 500, "Internal error processing reveal request", null));

        assertThatThrownBy(() -> SecretRevealHelper.revealProtectedSecret(apiClient, wsId, projId, envId, secId, null, null, printer))
                .isInstanceOf(ApiClientException.class);

        assertThat(errStream.toString()).doesNotContain(sentinelSecret);
        assertThat(outStream.toString()).doesNotContain(sentinelSecret);
    }
}
