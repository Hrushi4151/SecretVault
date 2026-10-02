package com.secretvault.security.engine;

import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityIntelligenceEngineTest {

    @Mock
    private SecurityFindingService findingService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private SecurityEventService eventService;

    @Mock
    private SecurityDetectionRule rule1;

    @Mock
    private SecurityDetectionRule rule2;

    private SecurityIntelligenceEngine engine;

    private final UUID orgId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace(orgId, "Workspace", "workspace-slug", false);
        workspace.setId(workspaceId);
        engine = new SecurityIntelligenceEngine(
                List.of(rule1, rule2),
                findingService,
                workspaceRepository,
                eventService
        );
    }

    @Test
    @DisplayName("analyzeWorkspace orchestrates rules, upserts findings, and emits audit event")
    void analyzeWorkspace_success() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        SecurityFindingDraft draft1 = new SecurityFindingDraft(
                workspaceId, null, null,
                FindingCategory.EXCESSIVE_PRIVILEGE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Title 1", "Desc 1", "Remediation 1", "sub1", Map.of()
        );
        SecurityFindingDraft draft2 = new SecurityFindingDraft(
                workspaceId, null, null,
                FindingCategory.ACCESS_REVIEW_OVERDUE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Title 2", "Desc 2", "Remediation 2", "sub2", Map.of()
        );

        when(rule1.evaluate(eq(workspace), any())).thenReturn(List.of(draft1));
        when(rule2.evaluate(eq(workspace), any())).thenReturn(List.of(draft2));

        SecurityIntelligenceEngine.SecurityAnalysisResult result = engine.analyzeWorkspace(workspaceId, userId);

        assertThat(result).isNotNull();
        assertThat(result.workspaceId()).isEqualTo(workspaceId);
        assertThat(result.rulesExecuted()).isEqualTo(2);
        assertThat(result.findingsEvaluated()).isEqualTo(2);

        verify(findingService).upsertFinding(draft1);
        verify(findingService).upsertFinding(draft2);
        verify(eventService).recordEvent(
                eq(workspaceId), isNull(), isNull(), eq(userId),
                eq(SecurityEventType.SECURITY_ANALYSIS_EXECUTED), any(), any(), eq("SECURITY_ENGINE"),
                isNull(), isNull(), isNull(), any()
        );
    }

    @Test
    @DisplayName("analyzeWorkspace is resilient to individual rule failures")
    void analyzeWorkspace_ruleFailureResilience() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(rule1.getRuleName()).thenReturn("FailingRule");
        when(rule1.evaluate(eq(workspace), any())).thenThrow(new RuntimeException("Database timeout in rule"));

        SecurityFindingDraft draft2 = new SecurityFindingDraft(
                workspaceId, null, null,
                FindingCategory.ACCESS_REVIEW_OVERDUE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Title 2", "Desc 2", "Remediation 2", "sub2", Map.of()
        );
        when(rule2.evaluate(eq(workspace), any())).thenReturn(List.of(draft2));

        SecurityIntelligenceEngine.SecurityAnalysisResult result = engine.analyzeWorkspace(workspaceId, userId);

        assertThat(result.findingsEvaluated()).isEqualTo(1);
        verify(findingService, times(1)).upsertFinding(draft2);
    }

    @Test
    @DisplayName("analyzeWorkspace throws exception if workspace does not exist")
    void analyzeWorkspace_workspaceNotFound() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.analyzeWorkspace(workspaceId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Workspace not found");
    }
}
