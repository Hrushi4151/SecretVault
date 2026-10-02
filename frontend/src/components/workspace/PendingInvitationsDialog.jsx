import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { workspaceApi } from '../../api/workspaces';
import { Modal } from '../common/Modal';
import { Button } from '../common/Button';
import { RoleBadge } from '../common/Badge';
import { Alert } from '../common/Alert';
import {
  Mail,
  Check,
  X,
  Clock,
  Shield,
  Building2,
  Loader2,
  Inbox,
  UserCheck
} from 'lucide-react';

export const PendingInvitationsDialog = ({ isOpen, onClose, onInvitationAccepted }) => {
  const { user } = useAuth();
  const [invitations, setInvitations] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [processingId, setProcessingId] = useState(null);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const loadInvitations = useCallback(async () => {
    if (!isOpen) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await workspaceApi.getMyInvitations();
      setInvitations(data?.items || []);
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to retrieve pending invitations.');
    } finally {
      setIsLoading(false);
    }
  }, [isOpen]);

  useEffect(() => {
    if (isOpen) {
      loadInvitations();
      setSuccessMsg(null);
      setError(null);
    }
  }, [isOpen, loadInvitations]);

  const handleAccept = async (invitation) => {
    setProcessingId(invitation.id);
    setError(null);
    setSuccessMsg(null);

    try {
      await workspaceApi.acceptInvitationById(invitation.id);
      setSuccessMsg(`Joined workspace "${invitation.workspaceName}" successfully as ${invitation.role}!`);
      setInvitations((prev) => prev.filter((i) => i.id !== invitation.id));
      if (onInvitationAccepted) {
        onInvitationAccepted(invitation);
      }
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to accept invitation.');
    } finally {
      setProcessingId(null);
    }
  };

  const handleDecline = async (invitation) => {
    setProcessingId(invitation.id);
    setError(null);
    setSuccessMsg(null);

    try {
      await workspaceApi.declineInvitationById(invitation.id);
      setSuccessMsg(`Declined invitation to "${invitation.workspaceName}".`);
      setInvitations((prev) => prev.filter((i) => i.id !== invitation.id));
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to decline invitation.');
    } finally {
      setProcessingId(null);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Pending Workspace Invitations"
      description="Review and accept team invitations issued to your account."
      maxWidth="lg"
    >
      <div className="flex flex-col gap-4 font-body text-white">
        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}
        {successMsg && (
          <Alert variant="success" message={successMsg} onDismiss={() => setSuccessMsg(null)} />
        )}

        <div className="flex flex-col gap-3 min-h-[220px]">
          {isLoading ? (
            <div className="flex flex-col items-center justify-center py-12 gap-3 text-[#F4B5C8]">
              <Loader2 className="w-6 h-6 animate-spin text-[#FF2D6D]" />
              <span className="text-xs">Checking for pending invitations...</span>
            </div>
          ) : invitations.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 px-4 rounded-2xl bg-[#30000F]/60 border border-[#FFB4C8]/15 text-center gap-3">
              <div className="w-12 h-12 rounded-2xl bg-[#3F0016] border border-[#FFB4C8]/20 flex items-center justify-center text-[#FF2D6D]">
                <Inbox className="w-6 h-6" />
              </div>
              <div className="flex flex-col">
                <span className="text-sm font-semibold text-white">No Pending Invitations</span>
                <span className="text-xs text-[#A26377] mt-1">
                  You are all caught up. When someone invites your email ({user?.email}) to a workspace, it will appear here.
                </span>
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {invitations.map((inv) => {
                const isProcessing = processingId === inv.id;
                const formattedExpiry = new Date(inv.expiresAt).toLocaleDateString(undefined, {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric',
                });

                return (
                  <div
                    key={inv.id}
                    className="flex flex-col sm:flex-row items-start sm:items-center justify-between p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/20 hover:border-[#FF2D6D]/40 transition-all gap-4"
                  >
                    <div className="flex items-start gap-3.5 min-w-0">
                      <div className="w-10 h-10 rounded-xl bg-[#4A001C] border border-[#FF2D6D]/30 flex items-center justify-center text-[#FF2D6D] shrink-0 mt-0.5">
                        <Building2 className="w-5 h-5" />
                      </div>
                      <div className="flex flex-col min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-semibold text-white truncate">
                            {inv.workspaceName}
                          </span>
                          <RoleBadge role={inv.role} />
                        </div>
                        <span className="text-xs text-[#F4B5C8]/80 mt-0.5">
                          Invited by <strong className="text-white">{inv.invitedBy?.name || 'Administrator'}</strong>
                        </span>
                        <div className="flex items-center gap-3 text-[11px] font-mono text-[#A26377] mt-1">
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            Expires {formattedExpiry}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 w-full sm:w-auto justify-end shrink-0 border-t sm:border-t-0 pt-2 sm:pt-0 border-[#FFB4C8]/10">
                      <button
                        type="button"
                        onClick={() => handleDecline(inv)}
                        disabled={isProcessing}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-xl text-[#F4B5C8] hover:text-white hover:bg-[#3F0016] border border-[#FFB4C8]/20 transition-all disabled:opacity-50"
                      >
                        <X className="w-3.5 h-3.5" />
                        <span>Decline</span>
                      </button>

                      <Button
                        type="button"
                        variant="primary"
                        size="sm"
                        isLoading={isProcessing}
                        onClick={() => handleAccept(inv)}
                        className="flex items-center gap-1.5"
                      >
                        <UserCheck className="w-3.5 h-3.5" />
                        <span>Accept &amp; Join</span>
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div className="flex justify-end pt-3 border-t border-[#FFB4C8]/15">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
};
