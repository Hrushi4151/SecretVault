import { Pingram } from 'pingram';

/**
 * Pingram Client Configuration
 * Initialized with official SecretVault Pingram API Key and base URL.
 */
export const pingram = new Pingram({
  apiKey: 'pingram_sk_eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiJrZXlfM2M3YTdiNjFkODZjZTEwYzNiZWYwZmE4NGJlYjU0ZWIiLCJ2ZXJzaW9uIjoxLCJhY2NvdW50SWQiOiJsZ3VlcmEwb2I5dzYzYmNodzVleTBkN3l3dSIsImtleVR5cGUiOiJzZWNyZXQiLCJlbnZpcm9ubWVudElkIjoibGd1ZXJhMG9iOXc2M2JjaHc1ZXkwZDd5d3UifQ.82RRX5wbb0-UvImvx7Yyv1YCB3FAl5IzZpVrDKYLyQQ',
  baseUrl: 'https://api.pingram.io'
});

export const DEFAULT_ALERT_RECIPIENT = 'hrushitech51@gmail.com';

/**
 * Send an email alert via Pingram API
 * @param {Object} options
 * @param {string} options.to - Recipient email address
 * @param {string} options.subject - Email subject
 * @param {string} options.html - HTML body
 * @param {string} [options.fromName] - Sender name (default: 'SecretVault Security')
 * @param {string} [options.fromAddress] - Sender address (default: 'noreply@pingram.io')
 */
export const sendPingramEmail = async ({
  to = DEFAULT_ALERT_RECIPIENT,
  subject,
  html,
  fromName = 'SecretVault Security',
  fromAddress = 'noreply@pingram.io'
}) => {
  try {
    const response = await pingram.email.send({
      type: 'email_compose_preview',
      to: to || DEFAULT_ALERT_RECIPIENT,
      subject: subject || 'SecretVault Alert',
      html: html || '<p>SecretVault automated notification alert.</p>',
      fromName,
      fromAddress
    });
    return { success: true, data: response };
  } catch (err) {
    console.error('Failed to send email alert via Pingram:', err);
    return { success: false, error: err?.message || String(err) };
  }
};

/**
 * Dispatches a dual alert: in-app notification record and an external email alert via Pingram.
 */
export const dispatchSecurityAlert = async ({
  recipientEmail = DEFAULT_ALERT_RECIPIENT,
  title,
  message,
  severity = 'HIGH',
  category = 'Security Posture',
  metadata = {}
}) => {
  const badgeColor =
    severity === 'CRITICAL' ? '#EF4444' :
    severity === 'HIGH' ? '#F43F5E' :
    severity === 'MEDIUM' ? '#F59E0B' : '#38BDF8';

  const html = `
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; background-color: #1E000A; border: 1px solid #FFB4C833; border-radius: 16px; overflow: hidden; color: #FFFFFF;">
      <div style="background: linear-gradient(135deg, #FF2D6D 0%, #4A001C 100%); padding: 24px 28px; text-align: left;">
        <h1 style="margin: 0; font-size: 20px; font-weight: 700; color: #FFFFFF; letter-spacing: -0.5px;">
          🛡️ SecretVault Security Alert
        </h1>
        <p style="margin: 6px 0 0; font-size: 13px; color: #FFD2DF; font-weight: 500;">
          Category: <strong>${category}</strong> | Severity: <span style="background-color: ${badgeColor}; padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: bold;">${severity}</span>
        </p>
      </div>

      <div style="padding: 28px; background-color: #26000B;">
        <h2 style="margin: 0 0 12px; font-size: 16px; font-weight: 600; color: #FFFFFF;">${title}</h2>
        <p style="margin: 0 0 20px; font-size: 14px; line-height: 1.6; color: #F4B5C8;">${message}</p>

        ${metadata && Object.keys(metadata).length > 0 ? `
          <div style="background-color: #30000F; border: 1px solid #FFB4C820; border-radius: 12px; padding: 14px; margin-bottom: 20px;">
            <p style="margin: 0 0 8px; font-size: 11px; font-family: monospace; text-transform: uppercase; color: #A26377; font-weight: bold;">Event Telemetry</p>
            ${Object.entries(metadata).map(([k, v]) => `
              <div style="font-size: 12px; font-family: monospace; color: #FFB4C8; margin-bottom: 4px;">
                <strong style="color: #FFFFFF;">${k}:</strong> ${String(v)}
              </div>
            `).join('')}
          </div>
        ` : ''}

        <div style="text-align: center; margin-top: 24px;">
          <a href="http://localhost:3000" style="display: inline-block; background-color: #FF2D6D; color: #FFFFFF; text-decoration: none; padding: 10px 24px; border-radius: 10px; font-size: 13px; font-weight: 600; box-shadow: 0 4px 12px rgba(255, 45, 109, 0.3);">
            Open SecretVault Enclave →
          </a>
        </div>
      </div>

      <div style="padding: 16px 28px; background-color: #1A0008; border-top: 1px solid #FFB4C815; font-size: 11px; color: #A26377; text-align: center;">
        Dispatched via Pingram Multi-Channel Dispatcher • SecretVault DevSecOps Platform
      </div>
    </div>
  `;

  return await sendPingramEmail({
    to: recipientEmail,
    subject: `[${severity}] ${title} — SecretVault Alert`,
    html
  });
};
