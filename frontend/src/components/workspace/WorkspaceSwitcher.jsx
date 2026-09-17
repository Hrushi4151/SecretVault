import React, { useState, useRef, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { RoleBadge } from '../common/Badge';
import { CreateWorkspaceModal } from './CreateWorkspaceModal';
import {
  ChevronDown,
  CheckCircle2,
  Plus,
  Search,
  Users,
} from 'lucide-react';

export const WorkspaceSwitcher = ({ onOpenMembers }) => {
  const { workspaces, activeWorkspace, switchWorkspace } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const dropdownRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const filteredWorkspaces = (workspaces || []).filter((ws) =>
    ws.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    ws.slug.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const getInitials = (name) => {
    if (!name) return 'WS';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
    return name.slice(0, 2).toUpperCase();
  };

  const handleSelectWorkspace = (ws) => {
    switchWorkspace(ws.id);
    setIsOpen(false);
  };

  return (
    <div className="relative" ref={dropdownRef}>
      {/* Trigger Button */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2.5 px-3 py-1.5 rounded-xl bg-white/[0.04] hover:bg-white/[0.08] border border-white/[0.08] hover:border-white/[0.15] text-xs font-medium text-vault-text transition-all select-none"
      >
        <div className="w-5 h-5 rounded-lg bg-vault-primary/20 border border-vault-primary/30 flex items-center justify-center font-bold text-[10px] text-vault-primary-light">
          {getInitials(activeWorkspace?.name || 'WS')}
        </div>
        <div className="flex items-center gap-1.5 max-w-[140px] sm:max-w-[200px] truncate">
          <span className="font-semibold text-vault-text truncate">
            {activeWorkspace?.name || 'Select Workspace'}
          </span>
          {activeWorkspace && <RoleBadge role={activeWorkspace.role} />}
        </div>
        <ChevronDown className="w-3.5 h-3.5 text-vault-text-muted shrink-0 ml-0.5" />
      </button>

      {/* Stitch Multi-Org Tenant Switcher Dropdown */}
      {isOpen && (
        <div className="absolute top-full left-0 mt-2 z-50 w-[380px] sm:w-[440px] max-w-[calc(100vw-2rem)] rounded-2xl bg-[#0D1117]/95 border border-white/[0.12] p-4 flex flex-col gap-3 text-vault-text backdrop-blur-2xl shadow-2xl shadow-black/80 animate-scale-in">
          {/* Header */}
          <div className="flex items-center justify-between px-1 pb-1">
            <div className="flex items-center gap-2">
              <span className="font-semibold text-sm tracking-tight text-vault-text font-sans">
                Workspaces
              </span>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-medium bg-white/[0.06] text-vault-primary-light border border-white/[0.08]">
                {workspaces.length} Available
              </span>
            </div>
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                setIsCreateModalOpen(true);
              }}
              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-white/[0.06] hover:bg-white/[0.10] text-xs font-medium text-vault-text transition-colors border border-white/[0.08]"
            >
              <Plus className="w-3.5 h-3.5 text-vault-primary" />
              <span>New Workspace</span>
            </button>
          </div>

          {/* Search Filter Input */}
          <div className="relative flex items-center">
            <Search className="w-4 h-4 absolute left-3 text-vault-text-muted pointer-events-none" />
            <input
              type="text"
              placeholder="Search workspaces..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-12 py-2 text-xs rounded-xl bg-white/[0.04] text-vault-text placeholder:text-vault-text-muted/60 border border-white/[0.10] focus:outline-none focus:bg-white/[0.07] focus:border-vault-primary/60 transition-all"
              autoFocus
            />
            <div className="absolute right-2.5 flex items-center pointer-events-none">
              <kbd className="px-1.5 py-0.5 text-[10px] font-mono rounded bg-white/[0.08] text-vault-text-muted font-medium">
                ⌘K
              </kbd>
            </div>
          </div>

          {/* Workspaces List */}
          <div className="flex flex-col gap-1.5 max-h-72 overflow-y-auto pr-1">
            {filteredWorkspaces.length === 0 ? (
              <div className="text-center py-6 text-xs text-vault-text-muted">
                No matching workspaces found.
              </div>
            ) : (
              filteredWorkspaces.map((ws) => {
                const isCurrent = activeWorkspace?.id === ws.id;
                return (
                  <div
                    key={ws.id}
                    onClick={() => handleSelectWorkspace(ws)}
                    className={`group relative flex items-center justify-between p-3 rounded-xl cursor-pointer transition-all border ${
                      isCurrent
                        ? 'bg-white/[0.08] border-vault-primary/40 shadow-sm'
                        : 'bg-white/[0.02] hover:bg-white/[0.06] border-white/[0.06]'
                    }`}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold text-xs shrink-0 ${
                          isCurrent
                            ? 'bg-vault-primary/20 text-vault-primary-light border border-vault-primary/30'
                            : 'bg-white/[0.06] text-vault-text-secondary border border-white/[0.08]'
                        }`}
                      >
                        {getInitials(ws.name)}
                      </div>

                      <div className="flex flex-col min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="text-xs font-semibold text-vault-text truncate">
                            {ws.name}
                          </span>
                          {isCurrent && (
                            <CheckCircle2 className="w-3.5 h-3.5 text-vault-primary shrink-0" />
                          )}
                        </div>

                        <div className="flex items-center gap-2 text-[11px] text-vault-text-muted truncate">
                          <span className="font-mono">slug: {ws.slug}</span>
                          <span>•</span>
                          <RoleBadge role={ws.role} />
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {isCurrent ? (
                        <span className="px-2 py-0.5 rounded-md text-[10px] font-mono font-medium bg-vault-primary-subtle text-vault-primary-light border border-vault-primary/30">
                          Current
                        </span>
                      ) : null}

                      {onOpenMembers && isCurrent && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setIsOpen(false);
                            onOpenMembers();
                          }}
                          className="p-1.5 rounded-lg text-vault-text-muted hover:text-vault-text hover:bg-white/[0.10] transition-colors"
                          title="Manage Members"
                        >
                          <Users className="w-3.5 h-3.5" />
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

      <CreateWorkspaceModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
      />
    </div>
  );
};
