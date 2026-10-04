import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AiCopilotView } from '../components/ai/AiCopilotView';
import { AiRemediationWorkbenchView } from '../components/ai/AiRemediationWorkbenchView';

// Mock AuthContext
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'user-sec-01', fullName: 'Security Architect', email: 'architect@secretvault.io' },
    activeWorkspace: { id: 'ws-phase15-ai', name: 'Security Automation Workspace' }
  })
}));

// Mock AI API
vi.mock('../api/ai', () => ({
  aiApi: {
    getTokenBudget: vi.fn().mockResolvedValue({
      workspaceId: 'ws-phase15-ai',
      dailyTokenQuota: 100000,
      dailyTokensUsed: 1250,
      requestsTodayCount: 5
    }),
    listInquiries: vi.fn().mockResolvedValue([
      {
        id: 'inq-01',
        prompt: 'Diagnose sync drift on staging-db',
        responseContent: 'Sync drift identified in 2 parameters.',
        intent: 'DEPLOYMENT_RCA',
        modelUsed: 'deterministic-offline-v1',
        confidenceScore: 0.95,
        latencyMs: 35,
        createdAt: new Date().toISOString()
      }
    ]),
    getPostureForecast: vi.fn().mockResolvedValue({
      currentPostureScore: 92,
      projectedScore7Days: 86,
      projectedScore14Days: 78,
      driftVelocity: 'MODERATE',
      topRiskVectors: ['Overdue rotation on db credentials'],
      proactiveRecommendations: ['Execute automated rotation']
    }),
    listPlans: vi.fn().mockResolvedValue([
      {
        id: 'plan-01',
        title: 'Zero-Downtime DB Credential Rotation',
        description: 'Rotate overdue database credentials with zero downtime',
        status: 'PROPOSED',
        riskLevel: 'LOW',
        confidenceScore: 0.96,
        steps: [
          { stepNumber: 1, actionType: 'SECRETS_ROTATE', targetEntity: 'POSTGRES_DB', description: 'Trigger staged dual-credential rotation' }
        ],
        blastRadius: {
          affectedSecretsCount: 1,
          affectedServices: ['api-service'],
          downtimeEstimatedSeconds: 0,
          requiresStepUpMfa: false,
          rollbackComplexity: 'AUTOMATED'
        }
      }
    ])
  }
}));

describe('Phase 15: Frontend AI Intelligence Views', () => {
  it('renders AiCopilotView with Air-Gapped offline badge and quick prompts', async () => {
    render(<AiCopilotView />);
    expect(screen.getByText(/AI Security Intelligence Copilot/i)).toBeInTheDocument();
    expect(screen.getByText(/Air-Gapped Offline Engine Active/i)).toBeInTheDocument();
    expect(screen.getByText(/Why did deployment fail\?/i)).toBeInTheDocument();
    expect(screen.getByText(/Forecast posture decay/i)).toBeInTheDocument();
  });

  it('renders AiRemediationWorkbenchView with blast radius summary and plan controls', async () => {
    render(<AiRemediationWorkbenchView />);
    expect(screen.getByText(/AI Remediation Workbench/i)).toBeInTheDocument();
    expect(screen.getByText(/Advisory Guardrails Enforced/i)).toBeInTheDocument();
    expect(screen.getByText(/Generate Plan/i)).toBeInTheDocument();
  });
});
