package com.secretvault.access.privileged.service;

import com.secretvault.access.privileged.dto.*;
import com.secretvault.access.privileged.entity.PrivilegedAccessPolicy;
import com.secretvault.access.privileged.model.PrivilegedRequestStatus;

import java.util.List;
import java.util.UUID;

public interface PrivilegedAccessService {

    PrivilegedAccessRequestResponse createRequest(
            UUID workspaceId,
            UUID callerId,
            String sessionIdentifier,
            CreatePrivilegedAccessRequest request
    );

    PrivilegedAccessRequestResponse approveRequest(
            UUID workspaceId,
            UUID requestId,
            UUID approverId,
            String sessionIdentifier,
            ApprovePrivilegedRequest request
    );

    PrivilegedAccessRequestResponse rejectRequest(
            UUID workspaceId,
            UUID requestId,
            UUID approverId,
            RejectPrivilegedRequest request
    );

    PrivilegedAccessRequestResponse cancelRequest(
            UUID workspaceId,
            UUID requestId,
            UUID callerId,
            CancelPrivilegedRequest request
    );

    PrivilegedAccessRequestResponse executeRequest(
            UUID workspaceId,
            UUID requestId,
            UUID callerId,
            String sessionIdentifier,
            String stepUpProof
    );

    PrivilegedAccessRequestResponse revokeRequest(
            UUID workspaceId,
            UUID requestId,
            UUID callerId,
            RevokePrivilegedRequest request
    );

    PrivilegedAccessRequestResponse breakGlass(
            UUID workspaceId,
            UUID callerId,
            String sessionIdentifier,
            BreakGlassRequest request
    );

    PrivilegedAccessRequestResponse getRequest(UUID workspaceId, UUID requestId, UUID callerId);

    List<PrivilegedAccessRequestResponse> listRequests(
            UUID workspaceId,
            PrivilegedRequestStatus status,
            Boolean myRequestsOnly,
            Boolean awaitingMyApprovalOnly,
            UUID callerId
    );

    List<PrivilegedAccessElevationResponse> listElevations(UUID workspaceId, Boolean activeOnly, UUID callerId);

    void revokeElevation(UUID workspaceId, UUID elevationId, UUID callerId, String reason);

    List<PrivilegedAccessPolicyResponse> listPolicies(UUID workspaceId, UUID callerId);

    PrivilegedAccessPolicyResponse updatePolicy(
            UUID workspaceId,
            UUID policyId,
            UUID callerId,
            String sessionIdentifier,
            UpdatePrivilegedPolicyRequest request
    );

    PrivilegedAccessPolicy getEffectivePolicy(
            UUID workspaceId,
            com.secretvault.access.privileged.model.PrivilegedPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            com.secretvault.access.privileged.model.PrivilegedAction action
    );
}
