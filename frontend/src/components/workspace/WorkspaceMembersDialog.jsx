import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { workspaceApi } from '../../api/workspaces';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { RoleBadge } from '../common/Badge';
import { Alert } from '../common/Alert';
import { Users, UserPlus, Mail, Shield, Clock, Loader2 } from 'lucide-react';

export const WorkspaceMembersDialog = ({ isOpen, onClose }) => {
  const { activeWorkspace } = useAuth();
  const [members, setMembers] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isAdding, setIsAdding] = useState(false);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('DEVELOPER');
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const canManage = activeWorkspace?.role === 'OWNER' || activeWorkspace?.role === 'ADMIN';

  const fetchMembers = useCallback(async () => {
    if (!activeWorkspace?.id) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await workspaceApi.listMembers(activeWorkspace.id);
      setMembers(data);
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to load members.');
    } finally {
      setIsLoading(false);
    }
  }, [activeWorkspace?.id]);

  useEffect(() => {
    if (isOpen) {
      fetchMembers();
      setEmail('');
      setSuccessMsg(null);
      setError(null);
    }
  }, [isOpen, fetchMembers]);

  const handleAddMember = async (e) => {
    e.preventDefault();
    if (!activeWorkspace?.id) return;
    if (!email.trim() || !email.includes('@')) {
      setError('Please provide a valid member email address.');
      return;
    }

    setIsAdding(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const addedMember = await workspaceApi.addMember(activeWorkspace.id, {
        email: email.trim(),
        role,
      });
      setMembers((prev) => [...prev, addedMember]);
      setEmail('');
      setSuccessMsg(`Successfully added ${addedMember.fullName || addedMember.email} as ${role}.`);
    } catch (err) {
      setError(err.payload?.message || err.message || 'Failed to add member to workspace.');
    } finally {
      setIsAdding(false);
    }
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
      title="Workspace Members &amp; Access Control"
      description={`Manage identities and role authorizations for ${activeWorkspace?.name || 'this workspace'}.`}
      maxWidth="lg"
    >
      <div className="flex flex-col gap-5">
        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}
        {successMsg && (
          <Alert variant="success" message={successMsg} onDismiss={() => setSuccessMsg(null)} />
        )}

        {/* Add Member Form (Admin/Owner only) */}
        {canManage ? (
          <form
            onSubmit={handleAddMember}
            className="p-4 rounded-xl bg-white/[0.03] border border-white/[0.08] flex flex-col gap-3"
          >
            <div className="flex items-center gap-2 text-xs font-semibold text-vault-text">
              <UserPlus className="w-4 h-4 text-vault-primary" />
              <span>Invite or Add Existing User</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="sm:col-span-2">
                <Input
                  placeholder="colleague@company.com"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  leftIcon={<Mail className="w-4 h-4" />}
                  required
                />
              </div>
              <div>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  className="w-full h-full min-h-[38px] text-xs font-medium rounded-lg bg-white/[0.04] text-vault-text border border-white/[0.10] px-3 py-2 focus:outline-none focus:border-vault-primary/70 focus:ring-2 focus:ring-vault-primary/20"
                >
                  <option value="VIEWER" className="bg-[#0D1117] text-vault-text">
                    VIEWER (Read-only)
                  </option>
                  <option value="DEVELOPER" className="bg-[#0D1117] text-vault-text">
                    DEVELOPER (Read &amp; Write)
                  </option>
                  <option value="ADMIN" className="bg-[#0D1117] text-vault-text">
                    ADMIN (Full Config)
                  </option>
                  <option value="OWNER" className="bg-[#0D1117] text-vault-text">
                    OWNER (Primary Tenant)
                  </option>
                </select>
              </div>
            </div>

            <div className="flex justify-end">
              <Button type="submit" variant="primary" size="sm" isLoading={isAdding}>
                Add Member
              </Button>
            </div>
          </form>
        ) : (
          <div className="flex items-center gap-2 p-3 rounded-lg bg-white/[0.02] border border-white/[0.06] text-xs text-vault-text-muted font-mono">
            <Shield className="w-4 h-4 text-vault-warning" />
            <span>You have {activeWorkspace?.role} permissions. Member management requires ADMIN or OWNER role.</span>
          </div>
        )}

        {/* Member List */}
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs font-semibold text-vault-text-secondary">
            <span className="flex items-center gap-1.5">
              <Users className="w-3.5 h-3.5" />
              Active Members ({members.length})
            </span>
            <span className="font-mono text-[11px] text-vault-text-muted">RBAC Policy: Strict</span>
          </div>

          <div className="max-h-64 overflow-y-auto flex flex-col gap-1.5 pr-1">
            {isLoading ? (
              <div className="flex items-center justify-center py-8 text-vault-text-muted gap-2 text-xs">
                <Loader2 className="w-4 h-4 animate-spin text-vault-primary" />
                <span>Loading workspace members...</span>
              </div>
            ) : members.length === 0 ? (
              <div className="text-center py-8 text-xs text-vault-text-muted">
                No members found in this workspace.
              </div>
            ) : (
              members.map((member) => (
                <div
                  key={member.id}
                  className="flex items-center justify-between p-3 rounded-xl bg-white/[0.03] hover:bg-white/[0.05] border border-white/[0.06] transition-all"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-9 h-9 rounded-xl bg-white/[0.08] flex items-center justify-center font-bold text-xs text-vault-primary-light shrink-0">
                      {getInitials(member.fullName, member.email)}
                    </div>
                    <div className="flex flex-col min-w-0">
                      <span className="text-xs font-semibold text-vault-text truncate">
                        {member.fullName || 'Registered User'}
                      </span>
                      <span className="text-[11px] font-mono text-vault-text-muted truncate">
                        {member.email}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 shrink-0">
                    <RoleBadge role={member.role} />
                    <span className="hidden sm:flex items-center gap-1 text-[10px] font-mono text-vault-text-muted">
                      <Clock className="w-3 h-3" />
                      {new Date(member.joinedAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="flex justify-end pt-2 border-t border-white/[0.08]">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
};
