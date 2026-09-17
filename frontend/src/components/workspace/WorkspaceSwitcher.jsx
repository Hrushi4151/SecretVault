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
    <div className="relative font-body" ref={dropdownRef}>
      {/* Trigger Button */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-[#30000F] hover:bg-[#3F0016] text-xs text-white font-medium transition-all select-none border border-[#FFB4C8]/18 shadow-sm"
      >
        <div className="w-5 h-5 rounded-lg bg-[#4A001C] flex items-center justify-center font-bold text-[10px] text-[#FF2D6D] border border-[#FF2D6D]/30">
          {getInitials(activeWorkspace?.name || 'WS')}
        </div>
        <div className="flex items-center gap-1.5 max-w-[140px] sm:max-w-[200px] truncate">
          <span className="font-semibold text-white truncate">
            {activeWorkspace?.name || 'Select Workspace'}
          </span>
          {activeWorkspace && <RoleBadge role={activeWorkspace.role} />}
        </div>
        <ChevronDown className="w-3.5 h-3.5 text-[#A26377] shrink-0 ml-0.5" />
      </button>

      {/* Multi-Org Tenant Switcher Dropdown */}
      {isOpen && (
        <div className="absolute top-full left-0 mt-2 z-50 w-[380px] sm:w-[440px] max-w-[calc(100vw-2rem)] rounded-2xl bg-[#30000F]/98 border border-[#FFB4C8]/25 p-4 flex flex-col gap-3 text-white backdrop-blur-2xl shadow-2xl shadow-black/90 animate-scale-in">
          {/* Header */}
          <div className="flex items-center justify-between px-1 pb-1 border-b border-[#FFB4C8]/15">
            <div className="flex items-center gap-2">
              <span className="font-headline font-semibold text-sm tracking-tight text-white">
                Workspaces
              </span>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-[#FF2D6D]/15 text-[#FF2D6D] border border-[#FF2D6D]/30">
                {workspaces.length} Available
              </span>
            </div>
            <button
              type="button"
              onClick={() => {
                setIsOpen(false);
                setIsCreateModalOpen(true);
              }}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-[#3F0016] hover:bg-[#4A001C] text-xs font-medium text-white transition-colors border border-[#FFB4C8]/20"
            >
              <Plus className="w-3.5 h-3.5 text-[#FF2D6D]" />
              <span>New Workspace</span>
            </button>
          </div>

          {/* Search Filter Input */}
          <div className="relative flex items-center">
            <Search className="w-4 h-4 absolute left-3 text-[#A26377] pointer-events-none" />
            <input
              type="text"
              placeholder="Search workspaces..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-12 py-2 text-xs rounded-xl bg-[#3F0016] text-white placeholder:text-[#A26377] border border-[#FFB4C8]/20 focus:outline-none focus:bg-[#4A001C] focus:border-[#FF2D6D] transition-all"
              autoFocus
            />
            <div className="absolute right-2.5 flex items-center pointer-events-none">
              <kbd className="px-1.5 py-0.5 text-[10px] font-mono rounded bg-[#4A001C] text-white font-medium border border-[#FFB4C8]/25">
                ⌘K
              </kbd>
            </div>
          </div>

          {/* Workspaces List */}
          <div className="flex flex-col gap-1.5 max-h-72 overflow-y-auto pr-1">
            {filteredWorkspaces.length === 0 ? (
              <div className="text-center py-6 text-xs text-[#A26377]">
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
                        ? 'bg-[#4A001C] border-[#FF2D6D]/50 shadow-md shadow-[#FF2D6D]/10'
                        : 'bg-[#3F0016] hover:bg-[#4A001C] border-transparent hover:border-[#FFB4C8]/20'
                    }`}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div
                        className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold font-headline text-xs shrink-0 ${
                          isCurrent
                            ? 'bg-[#580023] text-[#FF2D6D] border border-[#FF2D6D]/40'
                            : 'bg-[#4A001C] text-[#F4B5C8] border border-[#FFB4C8]/15'
                        }`}
                      >
                        {getInitials(ws.name)}
                      </div>

                      <div className="flex flex-col min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-semibold text-white truncate">
                            {ws.name}
                          </span>
                          {isCurrent && (
                            <CheckCircle2 className="w-4 h-4 text-[#FF2D6D] shrink-0" />
                          )}
                        </div>

                        <div className="flex items-center gap-2 text-[11px] text-[#F4B5C8] truncate">
                          <span className="font-mono text-[#A26377]">slug: {ws.slug}</span>
                          <span>•</span>
                          <RoleBadge role={ws.role} />
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {isCurrent ? (
                        <span className="hidden sm:inline-flex px-2 py-0.5 rounded-md text-[10px] font-medium bg-[#30000F] text-[#FF2D6D] font-mono border border-[#FF2D6D]/30">
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
                          className="p-1.5 rounded-lg text-[#A26377] hover:text-white hover:bg-[#580023] transition-colors"
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
