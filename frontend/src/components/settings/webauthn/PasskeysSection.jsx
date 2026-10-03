import React, { useState, useEffect, useCallback } from 'react';
import { authApi } from '../../../api/auth';
import { Button } from '../../common/Button';
import { Input } from '../../common/Input';
import { Modal } from '../../common/Modal';
import { Alert } from '../../common/Alert';
import {
  isWebAuthnSupported,
  prepareCreationOptions,
  credentialCreationToJSON,
  formatWebAuthnError,
} from '../../../utils/webauthn';
import {
  Key,
  ShieldCheck,
  ShieldAlert,
  Smartphone,
  Laptop,
  Plus,
  Trash2,
  Edit2,
  RefreshCw,
  Loader2,
  AlertTriangle,
  Fingerprint,
  Sparkles,
  CheckCircle2,
} from 'lucide-react';

export const PasskeysSection = () => {
  const [credentials, setCredentials] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Modals state
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [addFriendlyName, setAddFriendlyName] = useState('');
  const [isRegistering, setIsRegistering] = useState(false);
  const [addError, setAddError] = useState(null);

  const [renameTarget, setRenameTarget] = useState(null);
  const [renameFriendlyName, setRenameFriendlyName] = useState('');
  const [isRenaming, setIsRenaming] = useState(false);
  const [renameError, setRenameError] = useState(null);

  const [revokeTarget, setRevokeTarget] = useState(null);
  const [isRevoking, setIsRevoking] = useState(false);
  const [revokeError, setRevokeError] = useState(null);

  const isSupported = isWebAuthnSupported();

  const fetchCredentials = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await authApi.getWebAuthnCredentials();
      setCredentials(Array.isArray(data) ? data : []);
    } catch (err) {
      const errMsg =
        err.payload?.message ||
        err.message ||
        'Unable to load registered passkeys. Please try again.';
      setError(errMsg);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCredentials();
  }, [fetchCredentials]);

  // Add / Register Passkey Ceremony
  const handleStartAddPasskey = () => {
    setAddFriendlyName('');
    setAddError(null);
    setIsAddModalOpen(true);
  };

  const handleRegisterPasskey = async (e) => {
    if (e) e.preventDefault();
    if (!isSupported) {
      setAddError('WebAuthn is not supported in this browser environment.');
      return;
    }

    setIsRegistering(true);
    setAddError(null);

    try {
      // 1. Fetch registration options from server
      const optionsResponse = await authApi.getWebAuthnRegistrationOptions(
        addFriendlyName.trim() || 'My Passkey'
      );

      // 2. Prepare options for navigator.credentials.create()
      const browserOptions = prepareCreationOptions(optionsResponse.optionsJson);

      // 3. Trigger browser WebAuthn ceremony
      const credential = await navigator.credentials.create(browserOptions);
      if (!credential) {
        throw new Error('Credential creation was cancelled or returned empty.');
      }

      // 4. Format public credential for backend verification
      const credentialJson = credentialCreationToJSON(credential);

      // 5. Send to backend verification
      await authApi.verifyWebAuthnRegistration({
        challengeId: optionsResponse.challengeId,
        friendlyName: addFriendlyName.trim() || optionsResponse.friendlyName || 'My Passkey',
        credentialJson,
      });

      // 6. Success
      setIsAddModalOpen(false);
      setSuccessMessage('Passkey successfully registered and bound to your account.');
      fetchCredentials();
      setTimeout(() => setSuccessMessage(null), 6000);
    } catch (err) {
      const formatted = formatWebAuthnError(err);
      setAddError(formatted);
    } finally {
      setIsRegistering(false);
    }
  };

  // Rename Passkey
  const handleOpenRename = (cred) => {
    setRenameTarget(cred);
    setRenameFriendlyName(cred.friendlyName || '');
    setRenameError(null);
  };

  const handleRenamePasskey = async (e) => {
    if (e) e.preventDefault();
    if (!renameFriendlyName.trim()) {
      setRenameError('Name cannot be empty.');
      return;
    }

    setIsRenaming(true);
    setRenameError(null);

    try {
      await authApi.renameWebAuthnCredential({
        credentialId: renameTarget.id,
        friendlyName: renameFriendlyName.trim(),
      });
      setRenameTarget(null);
      setSuccessMessage('Passkey renamed successfully.');
      fetchCredentials();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err) {
      setRenameError(err.payload?.message || err.message || 'Failed to rename passkey.');
    } finally {
      setIsRenaming(false);
    }
  };

  // Revoke Passkey
  const handleOpenRevoke = (cred) => {
    setRevokeTarget(cred);
    setRevokeError(null);
  };

  const handleRevokePasskey = async () => {
    if (!revokeTarget) return;

    setIsRevoking(true);
    setRevokeError(null);

    try {
      await authApi.revokeWebAuthnCredential(revokeTarget.id);
      setRevokeTarget(null);
      setSuccessMessage('Passkey removed successfully.');
      fetchCredentials();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err) {
      setRevokeError(
        err.payload?.message ||
        err.message ||
        'Failed to remove passkey. Ensure you retain at least one authentication method.'
      );
    } finally {
      setIsRevoking(false);
    }
  };

  const formatDate = (isoString) => {
    if (!isoString) return 'Never';
    try {
      const date = new Date(isoString);
      return date.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="p-6 rounded-3xl bg-[#1E000A] border border-[#FFB4C8]/15 flex flex-col gap-6 shadow-xl shadow-black/40">
      {/* Header */}
      <div className="flex items-start justify-between flex-wrap gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <h2 className="text-base font-headline font-bold text-white flex items-center gap-2">
              <Fingerprint className="w-4 h-4 text-[#FF2D6D]" />
              Passkeys & Security Keys (FIDO2 / WebAuthn)
            </h2>
            {isLoading ? (
              <span className="flex items-center gap-1 text-[10px] font-mono text-[#A26377]">
                <Loader2 className="w-3 h-3 animate-spin text-[#FF2D6D]" />
                Loading...
              </span>
            ) : credentials.length > 0 ? (
              <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#34D399] border border-[#34D399]/30 font-semibold">
                <span className="w-1.5 h-1.5 rounded-full bg-[#34D399] animate-pulse" />
                {credentials.length} ACTIVE {credentials.length === 1 ? 'PASSKEY' : 'PASSKEYS'}
              </span>
            ) : (
              <span className="px-2.5 py-0.5 rounded-full bg-[#3F0016] text-[10px] font-mono text-[#A26377] border border-[#FFB4C8]/15 font-semibold">
                NO PASSKEYS
              </span>
            )}
          </div>
          <p className="text-xs text-[#A26377]">
            Phishing-resistant cryptographic credentials stored securely on your device biometrics (Touch ID, Windows Hello) or hardware keys (YubiKey).
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={fetchCredentials}
            disabled={isLoading}
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            title="Refresh Passkeys"
          >
            Refresh
          </Button>

          <Button
            variant="primary"
            size="sm"
            onClick={handleStartAddPasskey}
            disabled={isLoading || !isSupported}
            leftIcon={<Plus className="w-3.5 h-3.5" />}
          >
            Add Passkey
          </Button>
        </div>
      </div>

      {/* Unsupported Browser Alert */}
      {!isSupported && (
        <div className="p-4 rounded-2xl bg-[#3F0016] border border-[#EF4444]/30 text-xs flex items-center gap-3">
          <ShieldAlert className="w-5 h-5 text-[#EF4444] shrink-0" />
          <span className="text-[#F4B5C8]">
            WebAuthn / Passkeys are not supported on this browser or platform. Please use a modern browser such as Chrome, Safari, Firefox, or Edge.
          </span>
        </div>
      )}

      {/* Success Notification */}
      {successMessage && (
        <Alert
          variant="success"
          message={successMessage}
          onDismiss={() => setSuccessMessage(null)}
        />
      )}

      {/* Error Banner */}
      {error && (
        <div className="flex items-center justify-between p-4 rounded-2xl bg-[#3F0016] border border-[#EF4444]/30 text-xs">
          <div className="flex items-center gap-3">
            <ShieldAlert className="w-5 h-5 text-[#EF4444] shrink-0" />
            <span className="text-[#F4B5C8]">{error}</span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={fetchCredentials}
            leftIcon={<RefreshCw className="w-3.5 h-3.5" />}
          >
            Retry
          </Button>
        </div>
      )}

      {/* Passkeys List */}
      {!isLoading && credentials.length > 0 && (
        <div className="flex flex-col gap-3">
          {credentials.map((cred) => (
            <div
              key={cred.id}
              className="p-4 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 transition-all hover:border-[#FFB4C8]/30"
            >
              <div className="flex items-start gap-3.5">
                <div className="w-10 h-10 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/25 flex items-center justify-center text-[#FF2D6D] shrink-0">
                  {cred.transports?.includes('internal') || cred.discoverable ? (
                    <Laptop className="w-5 h-5" />
                  ) : (
                    <Key className="w-5 h-5" />
                  )}
                </div>
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-headline font-bold text-white">
                      {cred.friendlyName || 'Passkey'}
                    </span>
                    {cred.backupEligible && (
                      <span className="px-2 py-0.5 rounded-md bg-[#1E000A] text-[9px] font-mono text-[#34D399] border border-[#34D399]/30 font-medium">
                        Synced Passkey
                      </span>
                    )}
                    {cred.userVerifiedCapable && (
                      <span className="px-2 py-0.5 rounded-md bg-[#1E000A] text-[9px] font-mono text-[#F4B5C8] border border-[#FFB4C8]/15 font-medium">
                        Biometric Capable
                      </span>
                    )}
                  </div>
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] font-mono text-[#A26377]">
                    <span>Added: {formatDate(cred.createdAt)}</span>
                    <span>•</span>
                    <span>Last used: {formatDate(cred.lastUsedAt)}</span>
                    {cred.lastUsedIp && (
                      <>
                        <span>•</span>
                        <span>IP: {cred.lastUsedIp}</span>
                      </>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-center">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleOpenRename(cred)}
                  leftIcon={<Edit2 className="w-3.5 h-3.5" />}
                >
                  Rename
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-[#EF4444] hover:bg-[#EF4444]/10 hover:text-[#EF4444]"
                  onClick={() => handleOpenRevoke(cred)}
                  leftIcon={<Trash2 className="w-3.5 h-3.5" />}
                >
                  Remove
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && credentials.length === 0 && (
        <div className="p-8 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex flex-col items-center justify-center text-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-[#3F0016] border border-[#FFB4C8]/20 flex items-center justify-center text-[#FFB4C8]">
            <Key className="w-6 h-6" />
          </div>
          <div className="flex flex-col gap-1 max-w-md">
            <h4 className="text-sm font-headline font-bold text-white">
              No Passkeys or Security Keys Registered
            </h4>
            <p className="text-xs text-[#A26377]">
              Register your computer’s Touch ID, Windows Hello, phone biometrics, or hardware security keys (e.g. YubiKey) for seamless phishing-resistant authentication and instant step-up approval.
            </p>
          </div>
          {isSupported && (
            <Button
              variant="primary"
              size="sm"
              onClick={handleStartAddPasskey}
              leftIcon={<Plus className="w-3.5 h-3.5" />}
              className="mt-2"
            >
              Add Your First Passkey
            </Button>
          )}
        </div>
      )}

      {/* Add Passkey Modal */}
      <Modal
        isOpen={isAddModalOpen}
        onClose={() => !isRegistering && setIsAddModalOpen(false)}
        title="Register Passkey or Security Key"
        description="Bind a phishing-resistant FIDO2 passkey or hardware security key to your account."
        maxWidth="md"
      >
        <form onSubmit={handleRegisterPasskey} className="flex flex-col gap-5 pt-1">
          {addError && (
            <Alert variant="danger">
              {addError}
            </Alert>
          )}

          <div className="flex items-center gap-3 p-3.5 rounded-xl bg-[#3F0016] border border-[#FFB4C8]/20 text-xs text-[#F4B5C8]">
            <ShieldCheck className="w-5 h-5 text-[#34D399] shrink-0" />
            <span>
              Your device will prompt you to use your fingerprint, facial recognition, device PIN, or tap your hardware key. Private keys never leave your device.
            </span>
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-[#F4B5C8]">
              Passkey Friendly Name
            </label>
            <Input
              type="text"
              value={addFriendlyName}
              onChange={(e) => setAddFriendlyName(e.target.value)}
              placeholder="e.g. MacBook Touch ID, Work YubiKey 5C, Pixel 8"
              disabled={isRegistering}
              autoFocus
            />
            <span className="text-[11px] text-[#A26377]">
              Give this key an identifiable label so you can recognize it later.
            </span>
          </div>

          <div className="flex items-center justify-end gap-3 pt-2 border-t border-[#FFB4C8]/10">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setIsAddModalOpen(false)}
              disabled={isRegistering}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              disabled={isRegistering}
            >
              {isRegistering ? (
                <>
                  <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                  Follow Device Prompt...
                </>
              ) : (
                <>
                  <Fingerprint className="w-4 h-4 mr-1.5" />
                  Continue & Trigger Prompt
                </>
              )}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Rename Passkey Modal */}
      <Modal
        isOpen={Boolean(renameTarget)}
        onClose={() => !isRenaming && setRenameTarget(null)}
        title="Rename Passkey"
        description="Update the friendly label for this credential."
        maxWidth="sm"
      >
        <form onSubmit={handleRenamePasskey} className="flex flex-col gap-4 pt-1">
          {renameError && (
            <Alert variant="danger">
              {renameError}
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-[#F4B5C8]">
              Friendly Name
            </label>
            <Input
              type="text"
              value={renameFriendlyName}
              onChange={(e) => setRenameFriendlyName(e.target.value)}
              placeholder="e.g. Personal MacBook"
              disabled={isRenaming}
              autoFocus
              required
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setRenameTarget(null)}
              disabled={isRenaming}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              disabled={isRenaming || !renameFriendlyName.trim()}
            >
              {isRenaming ? (
                <>
                  <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                  Saving...
                </>
              ) : (
                'Save Changes'
              )}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Revoke Passkey Confirmation Modal */}
      <Modal
        isOpen={Boolean(revokeTarget)}
        onClose={() => !isRevoking && setRevokeTarget(null)}
        title="Remove Passkey"
        description={`Are you sure you want to remove "${revokeTarget?.friendlyName || 'this passkey'}"?`}
        maxWidth="sm"
      >
        <div className="flex flex-col gap-4 pt-1">
          {revokeError && (
            <Alert variant="danger">
              {revokeError}
            </Alert>
          )}

          <div className="p-3.5 rounded-xl bg-[#3F0016] border border-[#EF4444]/30 text-xs text-[#F4B5C8] flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-[#EF4444] shrink-0 mt-0.5" />
            <div className="flex flex-col gap-1">
              <span className="font-bold text-white">Lockout Prevention Notice</span>
              <span>
                Removing this passkey will prevent this hardware key from authenticating. Ensure you have alternative login methods (password, authenticator app, or recovery codes) active.
              </span>
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setRevokeTarget(null)}
              disabled={isRevoking}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="danger"
              size="sm"
              onClick={handleRevokePasskey}
              disabled={isRevoking}
            >
              {isRevoking ? (
                <>
                  <Loader2 className="w-4 h-4 mr-1.5 animate-spin" />
                  Removing...
                </>
              ) : (
                'Remove Passkey'
              )}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
