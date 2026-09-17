import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { workspaceApi } from '../../api/workspaces';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { RoleBadge } from '../common/Badge';
import { Alert } from '../common/Alert';
import {
  Users,
  UserPlus,
  Mail,
  Shield,
  Clock,
  Loader2,
  Trash2,
  Copy,
  Check,
  Send,
  XCircle,
  KeyRound
} from 'lucide-react';

export const WorkspaceMembersDialog = ({ isOpen, onClose }) => {
  const { activeWorkspace, user } = useAuth();
  const [activeTab, setActiveTab] = useState('members'); // 'members' | 'invitations' | 'invite'
  const [members, setMembers] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Invite Form State
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('DEVELOPER');
  const [expiresInDays, setExpiresInDays] = useState(7);
  const [createdToken, setCreatedToken] = useState(null);
  const [copied, setCopied] = useState(false);

  // Feedback State
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const canManage = activeWorkspace?.role === 'OWNER' || activeWorkspace?.role === 'ADMIN';

  const loadData = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsLoading(true);
    setError(null);
    try {
      const [membersData, invitationsData] = await Promise.all([
        workspaceApi.listMembers(activeWorkspace.id),
        canManage ? workspaceApi.listInvitations(activeWorkspace.id).catch(() => []) : Promise.resolve([]),
      ]);
      setMembers(membersData || []);
      setInvitations(invitationsData || []);
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to load workspace access data.');
    } finally {
      setIsLoading(false);
    }
  }, [activeWorkspace?.id, canManage]);

  useEffect(() => {
    if (isOpen) {
      loadData();
      setActiveTab('members');
      setInviteEmail('');
      setInviteRole('DEVELOPER');
      setCreatedToken(null);
      setCopied(false);
      setSuccessMsg(null);
      setError(null);
    }
  }, [isOpen, loadData]);

  const handleCreateInvitation = async (e) => {
    e.preventDefault();
    if (!activeWorkspace?.id) return;
    if (!inviteEmail.trim() || !inviteEmail.includes('@')) {
      setError('Please provide a valid recipient email address.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const response = await workspaceApi.createInvitation(activeWorkspace.id, {
        email: inviteEmail.trim(),
        role: inviteRole,
        expiresInDays: Number(expiresInDays),
      });

      if (response.rawToken) {
        setCreatedToken(response.rawToken);
      }
      setSuccessMsg(`Invitation generated successfully for ${inviteEmail}.`);
      setInviteEmail('');
      loadData();
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to generate workspace invitation.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRoleChange = async (targetUserId, newRole) => {
    if (!activeWorkspace?.id) return;
    setError(null);
    setSuccessMsg(null);
    try {
      await workspaceApi.updateMemberRole(activeWorkspace.id, targetUserId, { role: newRole });
      setMembers((prev) =>
        prev.map((m) => (m.userId === targetUserId ? { ...m, role: newRole } : m))
      );
      setSuccessMsg('Member role updated successfully.');
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to update member role.');
    }
  };

  const handleRemoveMember = async (targetUserId, memberName) => {
    if (!activeWorkspace?.id) return;
    const isSelf = user?.id === targetUserId;
    if (!window.confirm(isSelf ? 'Are you sure you want to leave this workspace?' : `Remove ${memberName} from this workspace?`)) {
      return;
    }

    setError(null);
    setSuccessMsg(null);
    try {
      await workspaceApi.removeMember(activeWorkspace.id, targetUserId);
      setMembers((prev) => prev.filter((m) => m.userId !== targetUserId));
      setSuccessMsg(isSelf ? 'You have left the workspace.' : `${memberName} has been removed.`);
      if (isSelf) {
        setTimeout(() => window.location.reload(), 1000);
      }
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to remove member.');
    }
  };

  const handleRevokeInvitation = async (invitationId, inviteeEmail) => {
    if (!activeWorkspace?.id) return;
    if (!window.confirm(`Revoke pending invitation for ${inviteeEmail}?`)) {
      return;
    }

    setError(null);
    setSuccessMsg(null);
    try {
      await workspaceApi.revokeInvitation(activeWorkspace.id, invitationId);
      setInvitations((prev) => prev.filter((inv) => inv.id !== invitationId));
      setSuccessMsg(`Invitation for ${inviteeEmail} has been revoked.`);
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to revoke invitation.');
    }
  };

  const copyTokenToClipboard = () => {
    if (!createdToken) return;
    navigator.clipboard.writeText(createdToken);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  const getInitials = (name, mail) => {
    if (name && name.trim().length > 0) {
      const parts = name.trim().split(' ');
      if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
      return name.slice(0, 2).toUpperCase();
    }
    if (mail) return mail.slice(0, 2).toUpperCase();
    return 'U';
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Workspace Access Control & Governance"
      description={`Manage identities, invitations, and hierarchical permissions for ${activeWorkspace?.name || 'this workspace'}.`}
      maxWidth="xl"
    >
      <div className="flex flex-col gap-4 font-body text-white">
        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}
        {successMsg && (
          <Alert variant="success" message={successMsg} onDismiss={() => setSuccessMsg(null)} />
        )}

        {/* Tab Navigation */}
        <div className="flex items-center gap-2 p-1 rounded-xl bg-[#28000C] border border-[#FFB4C8]/15 text-xs font-semibold">
          <button
            onClick={() => { setActiveTab('members'); setError(null); setSuccessMsg(null); }}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
              activeTab === 'members'
                ? 'bg-[#FF2D6D] text-white shadow-md'
                : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#3F0016]'
            }`}
          >
            <Users className="w-3.5 h-3.5" />
            <span>Active Members ({members.length})</span>
          </button>

          {canManage && (
            <button
              onClick={() => { setActiveTab('invitations'); setError(null); setSuccessMsg(null); }}
              className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
                activeTab === 'invitations'
                  ? 'bg-[#FF2D6D] text-white shadow-md'
                  : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#3F0016]'
              }`}
            >
              <Clock className="w-3.5 h-3.5" />
              <span>Pending Invitations ({invitations.length})</span>
            </button>
          )}

          {canManage && (
            <button
              onClick={() => { setActiveTab('invite'); setError(null); setSuccessMsg(null); }}
              className={`flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
                activeTab === 'invite'
                  ? 'bg-[#FF2D6D] text-white shadow-md'
                  : 'text-[#F4B5C8]/80 hover:text-white hover:bg-[#3F0016]'
              }`}
            >
              <UserPlus className="w-3.5 h-3.5" />
              <span>Create Invitation</span>
            </button>
          )}
        </div>

        {/* Tab: Active Members */}
        {activeTab === 'members' && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs font-semibold text-[#F4B5C8]">
              <span className="flex items-center gap-1.5">
                <Users className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Enrolled Workspace Principals
              </span>
              <span className="font-mono text-[11px] text-[#A26377]">
                Your Role: <strong className="text-white">{activeWorkspace?.role}</strong>
              </span>
            </div>

            <div className="max-h-72 overflow-y-auto flex flex-col gap-2 pr-1">
              {isLoading ? (
                <div className="flex items-center justify-center py-8 text-[#A26377] gap-2 text-xs">
                  <Loader2 className="w-4 h-4 animate-spin text-[#FF2D6D]" />
                  <span>Loading workspace members...</span>
                </div>
              ) : members.length === 0 ? (
                <div className="text-center py-8 text-xs text-[#A26377]">
                  No members found in this workspace.
                </div>
              ) : (
                members.map((member) => {
                  const isCurrentUser = member.userId === user?.id;
                  return (
                    <div
                      key={member.id}
                      className="flex items-center justify-between p-3.5 rounded-xl bg-[#3F0016] hover:bg-[#4A001C] border border-[#FFB4C8]/15 transition-all"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="w-9 h-9 rounded-xl bg-[#4A001C] border border-[#FF2D6D]/30 flex items-center justify-center font-bold text-xs text-[#FF2D6D] shrink-0">
                          {getInitials(member.fullName, member.email)}
                        </div>
                        <div className="flex flex-col min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs font-semibold text-white truncate">
                              {member.fullName || 'Registered User'}
                            </span>
                            {isCurrentUser && (
                              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[#FF2D6D]/20 text-[#FF2D6D] border border-[#FF2D6D]/30">
                                You
                              </span>
                            )}
                          </div>
                          <span className="text-[11px] font-mono text-[#A26377] truncate">
                            {member.email}
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-3 shrink-0">
                        {canManage && !isCurrentUser ? (
                          <select
                            value={member.role}
                            onChange={(e) => handleRoleChange(member.userId, e.target.value)}
                            className="text-xs font-medium rounded-lg bg-[#30000F] text-white border border-[#FFB4C8]/25 px-2.5 py-1 focus:outline-none focus:border-[#FF2D6D]"
                          >
                            <option value="VIEWER">VIEWER</option>
                            <option value="DEVELOPER">DEVELOPER</option>
                            <option value="ADMIN">ADMIN</option>
                            <option value="OWNER">OWNER</option>
                          </select>
                        ) : (
                          <RoleBadge role={member.role} />
                        )}

                        <span className="hidden sm:flex items-center gap-1 text-[10px] font-mono text-[#A26377]">
                          <Clock className="w-3 h-3" />
                          {new Date(member.joinedAt).toLocaleDateString()}
                        </span>

                        {(canManage || isCurrentUser) && (
                          <button
                            onClick={() => handleRemoveMember(member.userId, member.fullName || member.email)}
                            title={isCurrentUser ? 'Leave Workspace' : 'Remove Member'}
                            className="p-1.5 rounded-lg text-[#A26377] hover:text-[#FF2D6D] hover:bg-[#FF2D6D]/10 transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}

        {/* Tab: Pending Invitations */}
        {activeTab === 'invitations' && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs font-semibold text-[#F4B5C8]">
              <span className="flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-[#FF2D6D]" />
                Outstanding Single-Use Invitations
              </span>
              <span className="font-mono text-[11px] text-[#A26377]">SHA-256 Hashed Tokens</span>
            </div>

            <div className="max-h-72 overflow-y-auto flex flex-col gap-2 pr-1">
              {isLoading ? (
                <div className="flex items-center justify-center py-8 text-[#A26377] gap-2 text-xs">
                  <Loader2 className="w-4 h-4 animate-spin text-[#FF2D6D]" />
                  <span>Loading invitations...</span>
                </div>
              ) : invitations.length === 0 ? (
                <div className="text-center py-8 text-xs text-[#A26377]">
                  No pending invitations found.
                </div>
              ) : (
                invitations.map((inv) => (
                  <div
                    key={inv.id}
                    className="flex items-center justify-between p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/15"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-8 h-8 rounded-lg bg-[#4A001C] border border-[#FFB4C8]/20 flex items-center justify-center text-[#FF2D6D]">
                        <Mail className="w-4 h-4" />
                      </div>
                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-semibold text-white truncate">
                          {inv.email}
                        </span>
                        <span className="text-[11px] font-mono text-[#A26377]">
                          Expires: {new Date(inv.expiresAt).toLocaleDateString()}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-3 shrink-0">
                      <RoleBadge role={inv.role} />
                      <button
                        onClick={() => handleRevokeInvitation(inv.id, inv.email)}
                        className="flex items-center gap-1 text-xs text-[#FF2D6D] hover:bg-[#FF2D6D]/15 px-2.5 py-1 rounded-lg border border-[#FF2D6D]/30 transition-colors"
                      >
                        <XCircle className="w-3.5 h-3.5" />
                        <span>Revoke</span>
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* Tab: Create Invitation */}
        {activeTab === 'invite' && (
          <form onSubmit={handleCreateInvitation} className="flex flex-col gap-4">
            <div className="p-4 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/20 flex flex-col gap-3">
              <div className="flex items-center gap-2 text-xs font-semibold text-white">
                <Send className="w-4 h-4 text-[#FF2D6D]" />
                <span>Issue Cryptographically Secure Invitation</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="sm:col-span-2">
                  <Input
                    label="Invitee Email"
                    placeholder="teammate@company.com"
                    type="email"
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                    leftIcon={<Mail className="w-4 h-4" />}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#F4B5C8] mb-1.5">
                    Assigned Role
                  </label>
                  <select
                    value={inviteRole}
                    onChange={(e) => setInviteRole(e.target.value)}
                    className="w-full text-xs font-medium rounded-xl bg-[#4A001C] text-white border border-[#FFB4C8]/25 px-3 py-2.5 focus:outline-none focus:border-[#FF2D6D]"
                  >
                    <option value="VIEWER">VIEWER (Read-only)</option>
                    <option value="DEVELOPER">DEVELOPER (Read &amp; Write)</option>
                    <option value="ADMIN">ADMIN (Full Config)</option>
                    <option value="OWNER">OWNER (Primary Tenant)</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#F4B5C8] mb-1.5">
                  Token Expiration
                </label>
                <select
                  value={expiresInDays}
                  onChange={(e) => setExpiresInDays(e.target.value)}
                  className="w-full sm:w-1/2 text-xs font-medium rounded-xl bg-[#4A001C] text-white border border-[#FFB4C8]/25 px-3 py-2 focus:outline-none focus:border-[#FF2D6D]"
                >
                  <option value={1}>1 Day (Ephemeral)</option>
                  <option value={7}>7 Days (Standard)</option>
                  <option value={14}>14 Days</option>
                  <option value={30}>30 Days</option>
                </select>
              </div>

              <div className="flex justify-end pt-2">
                <Button type="submit" variant="primary" size="sm" isLoading={isSubmitting}>
                  Generate Invitation Token
                </Button>
              </div>
            </div>

            {/* Generated Token Display */}
            {createdToken && (
              <div className="p-4 rounded-xl bg-[#28000C] border border-[#FF2D6D]/40 flex flex-col gap-2">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-[#FF2D6D]">
                  <KeyRound className="w-4 h-4" />
                  <span>Single-Use Invitation Token Generated</span>
                </div>
                <p className="text-[11px] text-[#F4B5C8]/80 font-mono">
                  This raw token is displayed only once. Copy and share it securely with the invitee.
                </p>
                <div className="flex items-center gap-2 p-2.5 rounded-lg bg-[#3F0016] border border-[#FFB4C8]/20">
                  <span className="text-xs font-mono text-white truncate select-all flex-1">
                    {createdToken}
                  </span>
                  <button
                    type="button"
                    onClick={copyTokenToClipboard}
                    className="flex items-center gap-1 px-2.5 py-1 text-xs font-semibold rounded bg-[#FF2D6D] text-white hover:bg-[#FF4D82] transition-colors shrink-0"
                  >
                    {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copied ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>
              </div>
            )}
          </form>
        )}

        <div className="flex justify-end pt-3 border-t border-[#FFB4C8]/15">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
};
