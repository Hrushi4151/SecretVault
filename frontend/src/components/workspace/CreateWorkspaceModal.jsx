import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { Modal } from '../common/Modal';
import { Input } from '../common/Input';
import { Button } from '../common/Button';
import { Alert } from '../common/Alert';
import { Layers, Hash, Plus } from 'lucide-react';

export const CreateWorkspaceModal = ({ isOpen, onClose, onSuccess }) => {
  const { createWorkspace } = useAuth();
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [isSlugCustomized, setIsSlugCustomized] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleNameChange = (e) => {
    const val = e.target.value;
    setName(val);
    if (!isSlugCustomized) {
      const generatedSlug = val
        .toLowerCase()
        .replace(/[^a-z0-9\s-]/g, '')
        .trim()
        .replace(/\s+/g, '-');
      setSlug(generatedSlug);
    }
  };

  const handleSlugChange = (e) => {
    setIsSlugCustomized(true);
    setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError('Workspace name is required.');
      return;
    }

    if (!slug.trim() || !/^[a-z0-9-]+$/.test(slug)) {
      setError('Slug must contain only lowercase letters, numbers, and hyphens.');
      return;
    }

    setIsLoading(true);
    try {
      await createWorkspace({
        name: name.trim(),
        slug: slug.trim(),
      });
      handleClose();
      if (onSuccess) onSuccess();
    } catch (err) {
      const msg = err.payload?.message || err.message || 'Failed to create workspace.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleClose = () => {
    setName('');
    setSlug('');
    setIsSlugCustomized(false);
    setError(null);
    onClose();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title="Create New Workspace"
      description="Workspaces provide isolated cryptographic enclaves for environments, secrets, and member access."
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && <Alert variant="danger" message={error} onDismiss={() => setError(null)} />}

        <Input
          label="Workspace Display Name"
          type="text"
          id="ws-name"
          placeholder="Core Infrastructure"
          value={name}
          onChange={handleNameChange}
          leftIcon={<Layers className="w-4 h-4" />}
          required
          autoFocus
        />

        <Input
          label="Workspace Identifier (Slug)"
          type="text"
          id="ws-slug"
          placeholder="core-infra"
          value={slug}
          onChange={handleSlugChange}
          leftIcon={<Hash className="w-4 h-4" />}
          helperText="Used for API routing and CLI scoping: /workspaces/{slug}"
          required
        />

        <div className="flex items-center justify-end gap-2 pt-3 border-t border-white/[0.08]">
          <Button type="button" variant="ghost" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            isLoading={isLoading}
            leftIcon={<Plus className="w-4 h-4" />}
          >
            Create Workspace
          </Button>
        </div>
      </form>
    </Modal>
  );
};
