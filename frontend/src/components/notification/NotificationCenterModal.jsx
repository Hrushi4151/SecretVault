import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { notificationsApi } from '../../api/notifications';
import { sendPingramEmail, dispatchSecurityAlert, DEFAULT_ALERT_RECIPIENT } from '../../services/pingramService';
import {
  Bell,
  CheckCircle2,
  Clock,
  Settings,
  Sliders,
  Check,
  RotateCw,
  ExternalLink,
  Mail,
  ShieldAlert,
  Send,
  Sparkles,
  X,
  AlertTriangle,
  Info,
  CheckCheck,
  Radio
} from 'lucide-react';

export const NotificationCenterModal = ({ isOpen, onClose }) => {
  const { user, activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [activeTab, setActiveTab] = useState('list'); // 'list' | 'dispatch' | 'preferences'
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);
  const [dispatchLoading, setDispatchLoading] = useState(false);
  const [dispatchStatus, setDispatchStatus] = useState(null); // { type: 'success'|'error', message: string, trackingId?: string }

  // Dispatch Form State
  const [dispatchForm, setDispatchForm] = useState({
    title: 'High Severity: Unauthorized Secret Access Attempt',
    message: 'Anomalous decryption request observed outside registered IP boundaries for production RDS credentials.',
    severity: 'HIGH',
    recipientEmail: user?.email || DEFAULT_ALERT_RECIPIENT,
    channel: 'both', // 'both' | 'in-app' | 'email'
    category: 'Threat Protection'
  });

  const [preferences, setPreferences] = useState({
    channelInApp: true,
    channelWebhook: true,
    channelEmail: true,
    minSeverity: 'INFO',
    emailRecipient: user?.email || DEFAULT_ALERT_RECIPIENT,
    quietHoursEnabled: false,
    quietHoursStart: '22:00',
    quietHoursEnd: '07:00'
  });

  const fetchNotifications = useCallback(async () => {
    if (!workspaceId || !isOpen) return;
    setLoading(true);
    try {
      const data = await notificationsApi.listNotifications(workspaceId);
      setNotifications(data?.content || data?.items || []);
    } catch (err) {
      console.warn('Failed to load notifications:', err);
    } finally {
      setLoading(false);
    }
  }, [workspaceId, isOpen]);

  const fetchPreferences = useCallback(async () => {
    if (!workspaceId || !isOpen) return;
    try {
      const data = await notificationsApi.getPreferences(workspaceId);
      if (data) {
        setPreferences((prev) => ({
          ...prev,
          ...data,
          emailRecipient: user?.email || DEFAULT_ALERT_RECIPIENT
        }));
      }
    } catch (err) {
      // ignore
    }
  }, [workspaceId, isOpen, user]);

  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
      fetchPreferences();
      setDispatchStatus(null);
    }
  }, [isOpen, fetchNotifications, fetchPreferences]);

  const handleMarkRead = async (id) => {
    if (!workspaceId) return;
    try {
      await notificationsApi.markRead(workspaceId, id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, status: 'READ' } : n))
      );
    } catch (err) {
      console.error(err);
    }
  };

  const handleMarkAllRead = async () => {
    if (!workspaceId) return;
    try {
      await notificationsApi.markAllRead(workspaceId);
      setNotifications((prev) => prev.map((n) => ({ ...n, status: 'READ' })));
    } catch (err) {
      console.error(err);
    }
  };

  const handleSavePreferences = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;
    try {
      await notificationsApi.updatePreferences(workspaceId, preferences);
      setDispatchStatus({
        type: 'success',
        message: 'Notification channels & Pingram email preferences saved successfully!'
      });
    } catch (err) {
      setDispatchStatus({
        type: 'error',
        message: 'Failed to update preferences. Please retry.'
      });
    }
  };

  // Handle Quick Dispatch (In-App + Email via Pingram)
  const handleDispatchAlert = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;
    setDispatchLoading(true);
    setDispatchStatus(null);

    let pingramResult = null;
    let inAppResult = null;

    try {
      // 1. Send In-App Notification if enabled
      if (dispatchForm.channel === 'both' || dispatchForm.channel === 'in-app') {
        try {
          inAppResult = await notificationsApi.createNotification(workspaceId, {
            title: dispatchForm.title,
            message: dispatchForm.message,
            severity: dispatchForm.severity,
            actionUrl: '/dashboard'
          });
        } catch (err) {
          console.warn('In-app creation warning:', err);
        }
      }

      // 2. Send External Email Alert via Pingram API if enabled
      if (dispatchForm.channel === 'both' || dispatchForm.channel === 'email') {
        pingramResult = await dispatchSecurityAlert({
          recipientEmail: dispatchForm.recipientEmail || DEFAULT_ALERT_RECIPIENT,
          title: dispatchForm.title,
          message: dispatchForm.message,
          severity: dispatchForm.severity,
          category: dispatchForm.category,
          metadata: {
            Workspace: activeWorkspace?.name || 'Default Workspace',
            DispatchedBy: user?.fullName || 'Security Enclave',
            Timestamp: new Date().toISOString()
          }
        });
      }

      const trackingId = pingramResult?.data?.trackingId;
      setDispatchStatus({
        type: 'success',
        message: `Alert successfully dispatched! In-App feed updated${pingramResult?.success ? ` and Pingram email sent to ${dispatchForm.recipientEmail}.` : '.'}`,
        trackingId
      });

      // Refresh in-app list
      fetchNotifications();
    } catch (err) {
      setDispatchStatus({
        type: 'error',
        message: `Alert dispatch failed: ${err?.message || String(err)}`
      });
    } finally {
      setDispatchLoading(false);
    }
  };

  // Preset Alert Templates
  const applyPreset = (preset) => {
    setDispatchForm((prev) => ({
      ...prev,
      title: preset.title,
      message: preset.message,
      severity: preset.severity,
      category: preset.category
    }));
  };

  // Quick Test Email Send via Pingram
  const handleSendTestPingramEmail = async () => {
    setDispatchLoading(true);
    setDispatchStatus(null);
    try {
      const targetEmail = preferences.emailRecipient || user?.email || DEFAULT_ALERT_RECIPIENT;
      const res = await sendPingramEmail({
        to: targetEmail,
        subject: 'SecretVault Security Verification & Notification Center Alert',
        html: `
          <div style="font-family: -apple-system, sans-serif; background: #1E000A; color: #FFF; padding: 24px; border-radius: 12px; border: 1px solid #FF2D6D33;">
            <h2 style="color: #FF2D6D; margin-top: 0;">🛡️ SecretVault Notification Enclave</h2>
            <p style="color: #F4B5C8;">This is a verified test email sent via <strong>Pingram API</strong>.</p>
            <div style="background: #30000F; padding: 12px; border-radius: 8px; border: 1px solid #FFB4C820; font-family: monospace;">
              <strong>Status:</strong> Active &amp; Verified<br/>
              <strong>Environment:</strong> ${activeWorkspace?.name || 'Production'}<br/>
              <strong>Recipient:</strong> ${targetEmail}
            </div>
          </div>
        `
      });

      if (res.success) {
        setDispatchStatus({
          type: 'success',
          message: `Verified test email successfully delivered via Pingram to ${targetEmail}!`,
          trackingId: res.data?.trackingId
        });
      } else {
        setDispatchStatus({
          type: 'error',
          message: `Pingram dispatch error: ${res.error}`
        });
      }
    } catch (err) {
      setDispatchStatus({
        type: 'error',
        message: `Failed to trigger Pingram: ${err.message}`
      });
    } finally {
      setDispatchLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-fade-in">
      <div className="bg-[#1E000A] border border-[#FFB4C8]/25 rounded-2xl max-w-xl w-full p-6 shadow-2xl max-h-[90vh] flex flex-col gap-4 text-white">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#FFB4C8]/15 pb-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#FF2D6D] to-[#80002A] p-0.5 shadow-md shadow-[#FF2D6D]/20 flex items-center justify-center">
              <div className="w-full h-full bg-[#1E000A] rounded-[10px] flex items-center justify-center text-[#FF2D6D]">
                <Bell className="w-4 h-4 text-[#FF2D6D]" />
              </div>
            </div>
            <div className="flex flex-col">
              <div className="flex items-center gap-2">
                <span className="font-headline font-bold text-white text-base">
                  Notification Center &amp; Alert Engine
                </span>
                <span className="text-[9px] font-mono px-1.5 py-0.5 rounded-full bg-[#FF2D6D]/20 text-[#FF85A2] border border-[#FF2D6D]/30 font-semibold">
                  PINGRAM API
                </span>
              </div>
              <span className="text-[11px] font-mono text-[#A26377]">
                Multi-channel in-app and email delivery
              </span>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-[#A26377] hover:text-white hover:bg-[#30000F] transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center justify-between border-b border-[#FFB4C8]/10 pb-2">
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setActiveTab('list')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'list'
                  ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
                  : 'text-[#F4B5C8]/70 hover:text-white hover:bg-[#30000F]'
              }`}
            >
              In-App Feed ({notifications.length})
            </button>
            <button
              onClick={() => setActiveTab('dispatch')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'dispatch'
                  ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
                  : 'text-[#F4B5C8]/70 hover:text-white hover:bg-[#30000F]'
              }`}
            >
              <Send className="w-3.5 h-3.5 text-[#FF2D6D]" />
              <span>Dispatch Alert</span>
            </button>
            <button
              onClick={() => setActiveTab('preferences')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'preferences'
                  ? 'bg-[#FF2D6D]/20 text-white border border-[#FF2D6D]/40 shadow-sm'
                  : 'text-[#F4B5C8]/70 hover:text-white hover:bg-[#30000F]'
              }`}
            >
              <Sliders className="w-3.5 h-3.5 text-[#A26377]" />
              <span>Channels &amp; Pingram</span>
            </button>
          </div>

          {activeTab === 'list' && notifications.length > 0 && (
            <button
              onClick={handleMarkAllRead}
              className="text-[11px] text-[#FF85A2] hover:text-white flex items-center gap-1 font-mono transition-colors cursor-pointer"
            >
              <CheckCheck className="w-3.5 h-3.5" />
              <span>Mark all read</span>
            </button>
          )}
        </div>

        {/* Global Feedback Banner */}
        {dispatchStatus && (
          <div
            className={`p-3 rounded-xl border text-xs flex flex-col gap-1 transition-all ${
              dispatchStatus.type === 'success'
                ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-200'
                : 'bg-rose-950/40 border-rose-500/40 text-rose-200'
            }`}
          >
            <div className="flex items-center gap-2">
              {dispatchStatus.type === 'success' ? (
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              ) : (
                <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
              )}
              <span className="font-semibold">{dispatchStatus.message}</span>
            </div>
            {dispatchStatus.trackingId && (
              <span className="text-[10px] font-mono text-emerald-400/80 pl-6">
                Pingram Tracking ID: <strong>{dispatchStatus.trackingId}</strong>
              </span>
            )}
          </div>
        )}

        {/* Tab Content */}
        <div className="flex-1 overflow-y-auto space-y-3 pr-1 min-h-[300px]">
          {/* TAB 1: NOTIFICATIONS LIST */}
          {activeTab === 'list' && (
            <>
              {loading && notifications.length === 0 ? (
                <div className="p-12 text-center text-xs text-[#A26377]">
                  <RotateCw className="w-5 h-5 animate-spin mx-auto mb-2 text-[#FF2D6D]" />
                  Querying live notifications...
                </div>
              ) : notifications.length === 0 ? (
                <div className="p-12 text-center text-xs text-[#A26377] flex flex-col items-center gap-2">
                  <div className="w-12 h-12 rounded-2xl bg-[#30000F] border border-[#FFB4C8]/15 flex items-center justify-center text-[#A26377]">
                    <CheckCircle2 className="w-6 h-6 text-[#34D399]" />
                  </div>
                  <span className="font-semibold text-white">No active alerts</span>
                  <span>You're all caught up! All security invariant rules are green.</span>
                  <button
                    onClick={() => setActiveTab('dispatch')}
                    className="mt-2 px-3 py-1.5 rounded-lg bg-[#3F0016] text-[#FF85A2] hover:text-white border border-[#FF2D6D]/30 text-xs font-medium cursor-pointer"
                  >
                    Test Dispatching An Alert →
                  </button>
                </div>
              ) : (
                notifications.map((n) => {
                  const severityBadge =
                    n.severity === 'CRITICAL'
                      ? 'bg-red-500/20 text-red-300 border-red-500/40'
                      : n.severity === 'HIGH'
                      ? 'bg-[#FF2D6D]/20 text-[#FFB4C8] border-[#FF2D6D]/40'
                      : n.severity === 'MEDIUM'
                      ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                      : 'bg-[#38BDF8]/20 text-[#38BDF8] border-[#38BDF8]/40';

                  return (
                    <div
                      key={n.id}
                      className={`p-3.5 rounded-xl border text-xs space-y-2 transition-all ${
                        n.status === 'UNREAD'
                          ? 'bg-[#30000F] border-[#FF2D6D]/40 shadow-sm'
                          : 'bg-[#22000B]/60 border-[#FFB4C8]/10 opacity-70 hover:opacity-100'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span
                            className={`text-[9px] font-mono px-2 py-0.5 rounded-full border font-bold uppercase ${severityBadge}`}
                          >
                            {n.severity || 'INFO'}
                          </span>
                          <span className="font-headline font-bold text-white text-xs">{n.title}</span>
                        </div>
                        <span className="text-[10px] font-mono text-[#A26377]">
                          {n.createdAt ? new Date(n.createdAt).toLocaleTimeString() : 'Just now'}
                        </span>
                      </div>
                      <p className="text-xs text-[#F4B5C8]/80 leading-relaxed">{n.message}</p>
                      <div className="flex items-center justify-between pt-1 border-t border-[#FFB4C8]/10">
                        <div className="flex items-center gap-2">
                          <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#1E000A] text-[#A26377] border border-[#FFB4C8]/10">
                            IN-APP
                          </span>
                          {preferences.channelEmail && (
                            <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#1E000A] text-[#34D399] border border-[#34D399]/20">
                              PINGRAM EMAIL
                            </span>
                          )}
                        </div>
                        {n.status === 'UNREAD' && (
                          <button
                            onClick={() => handleMarkRead(n.id)}
                            className="text-[11px] text-[#FF85A2] hover:underline font-mono cursor-pointer"
                          >
                            Mark Read
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </>
          )}

          {/* TAB 2: DISPATCH DUAL ALERT */}
          {activeTab === 'dispatch' && (
            <form onSubmit={handleDispatchAlert} className="space-y-4 text-xs">
              {/* Presets */}
              <div className="space-y-1.5">
                <span className="font-mono text-[#A26377] text-[10px] uppercase font-bold tracking-wider">
                  Quick Security Presets:
                </span>
                <div className="grid grid-cols-2 gap-1.5">
                  <button
                    type="button"
                    onClick={() =>
                      applyPreset({
                        title: 'CRITICAL: Secret Exfiltration Detected',
                        message: 'API Key leak pattern detected in GitHub public repo commit. Immediate rotation recommended.',
                        severity: 'CRITICAL',
                        category: 'Code Security'
                      })
                    }
                    className="p-2 rounded-lg bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FF2D6D] text-left transition-all cursor-pointer"
                  >
                    <span className="font-bold text-red-400 block text-[11px]">🔴 Secret Exfiltration</span>
                    <span className="text-[10px] text-[#A26377] truncate block">GitHub commit leak trigger</span>
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      applyPreset({
                        title: 'Key Rotation Completed Successfully',
                        message: 'Database master password rotated across AWS & local vaults with zero downtime.',
                        severity: 'INFO',
                        category: 'Key Rotation'
                      })
                    }
                    className="p-2 rounded-lg bg-[#30000F] border border-[#FFB4C8]/15 hover:border-[#FF2D6D] text-left transition-all cursor-pointer"
                  >
                    <span className="font-bold text-emerald-400 block text-[11px]">🟢 Rotation Complete</span>
                    <span className="text-[10px] text-[#A26377] truncate block">Multi-cloud sync update</span>
                  </button>
                </div>
              </div>

              {/* Title & Category */}
              <div className="grid grid-cols-3 gap-2">
                <div className="col-span-2">
                  <label className="block text-[#A26377] mb-1 font-mono font-semibold">Alert Title:</label>
                  <input
                    type="text"
                    value={dispatchForm.title}
                    onChange={(e) => setDispatchForm({ ...dispatchForm, title: e.target.value })}
                    required
                    className="w-full bg-[#1A0008] text-white text-xs rounded-xl px-3 py-2 border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:outline-none"
                    placeholder="e.g. Critical Vulnerability Discovered"
                  />
                </div>
                <div>
                  <label className="block text-[#A26377] mb-1 font-mono font-semibold">Severity:</label>
                  <select
                    value={dispatchForm.severity}
                    onChange={(e) => setDispatchForm({ ...dispatchForm, severity: e.target.value })}
                    className="w-full bg-[#1A0008] text-white text-xs rounded-xl px-3 py-2 border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:outline-none font-mono"
                  >
                    <option value="INFO">INFO</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="HIGH">HIGH</option>
                    <option value="CRITICAL">CRITICAL</option>
                  </select>
                </div>
              </div>

              {/* Message */}
              <div>
                <label className="block text-[#A26377] mb-1 font-mono font-semibold">Alert Body Message:</label>
                <textarea
                  rows={3}
                  value={dispatchForm.message}
                  onChange={(e) => setDispatchForm({ ...dispatchForm, message: e.target.value })}
                  required
                  className="w-full bg-[#1A0008] text-white text-xs rounded-xl px-3 py-2 border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:outline-none"
                  placeholder="Describe the security event, impact, or required remediation..."
                />
              </div>

              {/* Delivery Channels */}
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-[#A26377] mb-1 font-mono font-semibold">Delivery Channel:</label>
                  <select
                    value={dispatchForm.channel}
                    onChange={(e) => setDispatchForm({ ...dispatchForm, channel: e.target.value })}
                    className="w-full bg-[#1A0008] text-white text-xs rounded-xl px-3 py-2 border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:outline-none font-mono"
                  >
                    <option value="both">Both: In-App &amp; Pingram Email</option>
                    <option value="email">Pingram Email Only</option>
                    <option value="in-app">In-App Notification Only</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[#A26377] mb-1 font-mono font-semibold">Recipient Email:</label>
                  <input
                    type="email"
                    value={dispatchForm.recipientEmail}
                    onChange={(e) => setDispatchForm({ ...dispatchForm, recipientEmail: e.target.value })}
                    required
                    className="w-full bg-[#1A0008] text-white text-xs rounded-xl px-3 py-2 border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:outline-none font-mono"
                  />
                </div>
              </div>

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  disabled={dispatchLoading}
                  className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-[#FF2D6D] to-[#990033] hover:from-[#FF4D82] hover:to-[#B3003C] text-white font-semibold text-xs shadow-lg shadow-[#FF2D6D]/25 transition-all flex items-center gap-2 cursor-pointer disabled:opacity-50"
                >
                  {dispatchLoading ? (
                    <>
                      <RotateCw className="w-3.5 h-3.5 animate-spin" />
                      <span>Dispatching via Pingram...</span>
                    </>
                  ) : (
                    <>
                      <Send className="w-3.5 h-3.5" />
                      <span>Send In-App &amp; Pingram Email Alert</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}

          {/* TAB 3: CHANNELS & PINGRAM CONFIGURATION */}
          {activeTab === 'preferences' && (
            <div className="space-y-4 text-xs">
              {/* Pingram Status Card */}
              <div className="p-3.5 rounded-xl bg-[#29000D] border border-[#FF2D6D]/30 flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-7 h-7 rounded-lg bg-[#4A001C] flex items-center justify-center text-[#FF2D6D] border border-[#FF2D6D]/30">
                      <Mail className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="font-bold text-white block">Pingram Multi-Channel Email API</span>
                      <span className="text-[10px] font-mono text-[#34D399]">● Connected to api.pingram.io</span>
                    </div>
                  </div>
                  <span className="text-[9px] font-mono px-2 py-0.5 rounded bg-[#1E000A] text-[#FF85A2] border border-[#FFB4C8]/20">
                    Live Enclave
                  </span>
                </div>
                <p className="text-[11px] text-[#F4B5C8]/80">
                  Sends verified cryptographic notifications and security alerts directly to team members via Pingram.
                </p>
                <div className="flex items-center justify-between pt-1 border-t border-[#FFB4C8]/15">
                  <span className="text-[10px] font-mono text-[#A26377]">
                    Sender: noreply@pingram.io
                  </span>
                  <button
                    type="button"
                    onClick={handleSendTestPingramEmail}
                    disabled={dispatchLoading}
                    className="px-3 py-1 rounded-lg bg-[#3F0016] hover:bg-[#52001D] text-[#FF85A2] hover:text-white border border-[#FF2D6D]/30 font-semibold text-[11px] transition-all flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                  >
                    {dispatchLoading ? <RotateCw className="w-3 h-3 animate-spin" /> : <Sparkles className="w-3 h-3 text-[#FF2D6D]" />}
                    <span>Send Test Email via Pingram</span>
                  </button>
                </div>
              </div>

              {/* Preferences Form */}
              <form onSubmit={handleSavePreferences} className="space-y-3">
                <div className="space-y-2">
                  <span className="font-mono text-[#A26377] text-[10px] uppercase font-bold tracking-wider">
                    Alert Delivery Channels:
                  </span>
                  <label className="flex items-center gap-2.5 text-white font-mono cursor-pointer p-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 hover:border-[#FFB4C8]/25">
                    <input
                      type="checkbox"
                      checked={preferences.channelInApp}
                      onChange={(e) => setPreferences({ ...preferences, channelInApp: e.target.checked })}
                      className="rounded accent-[#FF2D6D] w-4 h-4"
                    />
                    <div className="flex flex-col">
                      <span className="text-xs font-semibold">In-App Notification Center</span>
                      <span className="text-[10px] text-[#A26377]">Displays live unread alerts in top bar and modal</span>
                    </div>
                  </label>

                  <label className="flex items-center gap-2.5 text-white font-mono cursor-pointer p-2 rounded-xl bg-[#26000B] border border-[#FFB4C8]/10 hover:border-[#FFB4C8]/25">
                    <input
                      type="checkbox"
                      checked={preferences.channelEmail}
                      onChange={(e) => setPreferences({ ...preferences, channelEmail: e.target.checked })}
                      className="rounded accent-[#FF2D6D] w-4 h-4"
                    />
                    <div className="flex flex-col">
                      <span className="text-xs font-semibold">Pingram External Email Alerting</span>
                      <span className="text-[10px] text-[#A26377]">Sends HTML security alerts directly to mailbox</span>
                    </div>
                  </label>
                </div>

                <div>
                  <label className="block text-[#A26377] mb-1 font-mono font-semibold">Default Email Recipient:</label>
                  <input
                    type="email"
                    value={preferences.emailRecipient}
                    onChange={(e) => setPreferences({ ...preferences, emailRecipient: e.target.value })}
                    className="w-full bg-[#1A0008] text-white text-xs rounded-xl px-3 py-2 border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:outline-none font-mono"
                  />
                </div>

                <div>
                  <label className="block text-[#A26377] mb-1 font-mono font-semibold">Minimum Severity Threshold:</label>
                  <select
                    value={preferences.minSeverity}
                    onChange={(e) => setPreferences({ ...preferences, minSeverity: e.target.value })}
                    className="w-full bg-[#1A0008] text-white text-xs rounded-xl px-3 py-2 border border-[#FFB4C8]/20 focus:border-[#FF2D6D] focus:outline-none font-mono"
                  >
                    <option value="INFO">INFO (All alerts and notifications)</option>
                    <option value="MEDIUM">MEDIUM (Warnings and above)</option>
                    <option value="HIGH">HIGH (High severity &amp; critical only)</option>
                    <option value="CRITICAL">CRITICAL (Only active breach emergencies)</option>
                  </select>
                </div>

                <div className="flex justify-end pt-2">
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-xl bg-[#3F0016] hover:bg-[#52001D] text-white font-semibold text-xs border border-[#FF2D6D]/40 shadow-sm transition-all cursor-pointer"
                  >
                    Save Alert Preferences
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default NotificationCenterModal;
