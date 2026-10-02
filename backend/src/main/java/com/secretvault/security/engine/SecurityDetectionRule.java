package com.secretvault.security.engine;

import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.workspace.entity.Workspace;

import java.time.Instant;
import java.util.List;

/**
 * Deterministic detection rule contract for Security Intelligence analysis.
 * All rules must be side-effect free, deterministic, bounded, and strictly tenant-aware.
 */
public interface SecurityDetectionRule {

    FindingCategory getCategory();

    String getRuleName();

    List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime);
}
