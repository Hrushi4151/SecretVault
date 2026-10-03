import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { notificationsApi } from '../../api/notifications';
import {
  Bell,
  CheckCircle2,
  Clock,
  Settings,
  Sliders,
  Check,
  RotateCw,
  ExternalLink
} from 'lucide-react';

export const NotificationCenterModal = ({ isOpen, onClose }) => {
  const { activeWorkspace } = useAuth();
  const workspaceId = activeWorkspace?.id;

  const [activeTab, setActiveTab] = useState('list'); // 'list' | 'preferences'
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);
  const [preferences, setPreferences] = useState({
    channelInApp: true,
    channelWebhook: true,
    channelEmail: false,
    minSeverity: 'INFO',
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
      // ignore
    } finally {
      setLoading(false);
    }
  }, [workspaceId, isOpen]);

  const fetchPreferences = useCallback(async () => {
    if (!workspaceId || !isOpen) return;
    try {
      const data = await notificationsApi.getPreferences(workspaceId);
      if (data) setPreferences(data);
    } catch (err) {
      // ignore
    }
  }, [workspaceId, isOpen]);

  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
      fetchPreferences();
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
      // ignore
    }
  };

  const handleMarkAllRead = async () => {
    if (!workspaceId) return;
    try {
      await notificationsApi.markAllRead(workspaceId);
      setNotifications((prev) => prev.map((n) => ({ ...n, status: 'READ' })));
    } catch (err) {
      // ignore
    }
  };

  const handleSavePreferences = async (e) => {
    e.preventDefault();
    if (!workspaceId) return;
    try {
      await notificationsApi.updatePreferences(workspaceId, preferences);
      alert('Notification preferences updated!');
    } catch (err) {
      alert('Failed to save preferences');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div className="bg-surface-container-high border border-outline-variant rounded-2xl max-w-lg w-full p-6 space-y-4 shadow-2xl max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-outline-variant/60 pb-3">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-[#818CF8]/20 border border-[#818CF8]/40 flex items-center justify-center text-[#818CF8]">
              <Bell className="w-4 h-4" />
            </div>
            <span className="font-headline font-bold text-white text-base">Notification Center</span>
          </div>
          <button onClick={onClose} className="text-[#94A3B8] hover:text-white transition">
            ✕
          </button>
        </div>

        {/* Tab switcher */}
        <div className="flex items-center justify-between border-b border-outline-variant/40 pb-2">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setActiveTab('list')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                activeTab === 'list'
                  ? 'bg-[#818CF8]/20 text-[#818CF8] border border-[#818CF8]/40'
                  : 'text-[#94A3B8] hover:text-white'
              }`}
            >
              Notifications
            </button>
            <button
              onClick={() => setActiveTab('preferences')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                activeTab === 'preferences'
                  ? 'bg-[#818CF8]/20 text-[#818CF8] border border-[#818CF8]/40'
                  : 'text-[#94A3B8] hover:text-white'
              }`}
            >
              Preferences
            </button>
          </div>

          {activeTab === 'list' && notifications.length > 0 && (
            <button
              onClick={handleMarkAllRead}
              className="text-[11px] text-[#818CF8] hover:underline font-mono"
            >
              Mark all as read
            </button>
          )}
        </div>

        {/* Tab content */}
        <div className="flex-1 overflow-y-auto space-y-3 pr-1">
          {activeTab === 'list' ? (
            loading && notifications.length === 0 ? (
              <div className="p-8 text-center text-xs text-[#94A3B8]">
                <RotateCw className="w-4 h-4 animate-spin mx-auto mb-2 text-[#818CF8]" />
                Loading notifications...
              </div>
            ) : notifications.length === 0 ? (
              <div className="p-8 text-center text-xs text-[#94A3B8]">
                No notifications right now. You are all caught up!
              </div>
            ) : (
              notifications.map((n) => (
                <div
                  key={n.id}
                  className={`p-3.5 rounded-xl border text-xs space-y-1.5 transition ${
                    n.status === 'UNREAD'
                      ? 'bg-surface-container-low border-[#818CF8]/40'
                      : 'bg-surface-container-low/50 border-outline-variant/30 opacity-75'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-headline font-bold text-white text-xs">{n.title}</span>
                    <span className="text-[10px] font-mono text-[#64748B]">
                      {new Date(n.createdAt).toLocaleTimeString()}
                    </span>
                  </div>
                  <p className="text-xs text-[#94A3B8]">{n.message}</p>
                  <div className="flex items-center justify-between pt-1">
                    <span className="text-[10px] font-mono text-[#818CF8]">{n.severity || 'INFO'}</span>
                    {n.status === 'UNREAD' && (
                      <button
                        onClick={() => handleMarkRead(n.id)}
                        className="text-[11px] text-[#818CF8] hover:underline font-mono"
                      >
                        Mark read
                      </button>
                    )}
                  </div>
                </div>
              ))
            )
          ) : (
            <form onSubmit={handleSavePreferences} className="space-y-4 text-xs pt-1">
              <div className="space-y-2">
                <span className="font-mono text-[#94A3B8] block">Notification Channels:</span>
                <label className="flex items-center gap-2 text-white font-mono cursor-pointer">
                  <input
                    type="checkbox"
                    checked={preferences.channelInApp}
                    onChange={(e) => setPreferences({ ...preferences, channelInApp: e.target.checked })}
                    className="rounded border-outline-variant text-[#818CF8]"
                  />
                  <span>In-App Center</span>
                </label>
                <label className="flex items-center gap-2 text-white font-mono cursor-pointer">
                  <input
                    type="checkbox"
                    checked={preferences.channelWebhook}
                    onChange={(e) => setPreferences({ ...preferences, channelWebhook: e.target.checked })}
                    className="rounded border-outline-variant text-[#818CF8]"
                  />
                  <span>Outbound Webhook Delivery</span>
                </label>
              </div>

              <div>
                <label className="block text-[#94A3B8] mb-1 font-mono">Minimum Severity Alerting:</label>
                <select
                  value={preferences.minSeverity}
                  onChange={(e) => setPreferences({ ...preferences, minSeverity: e.target.value })}
                  className="w-full bg-surface-container-lowest text-white text-xs rounded-xl px-3 py-2 border border-outline-variant focus:outline-none focus:border-[#818CF8]"
                >
                  <option value="INFO">INFO (All alerts)</option>
                  <option value="MEDIUM">MEDIUM (Warnings and above)</option>
                  <option value="HIGH">HIGH (High severity and critical)</option>
                  <option value="CRITICAL">CRITICAL (Only emergencies)</option>
                </select>
              </div>

              <div className="flex justify-end pt-2">
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-[#818CF8] hover:bg-[#818CF8]/80 text-white text-xs font-semibold shadow-lg shadow-[#818CF8]/20 transition"
                >
                  Save Preferences
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};

export default NotificationCenterModal;
