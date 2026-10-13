package com.secretvault.common.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise transactional email dispatch service for SecretVault.
 * Delivers secure OTP codes and security notices via Pingram API or SMTP when configured,
 * with resilient logging fallback in local/development environments.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String mailPassword;
    private final String pingramApiKey;
    private final String pingramBaseUrl;
    private final String pingramFromName;
    private final String pingramFromAddress;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public record OtpPreview(String otp, boolean smtpDelivered, boolean smtpConfigured, String message) {}

    // Cache latest OTP and delivery status for developer visibility
    private final Map<String, OtpPreview> devOtpCache = new ConcurrentHashMap<>();

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Value("${spring.mail.username:}") String fromAddress,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${vault.pingram.api-key:${PINGRAM_API_KEY:}}") String pingramApiKey,
            @Value("${vault.pingram.base-url:${PINGRAM_BASE_URL:https://api.pingram.io}}") String pingramBaseUrl,
            @Value("${vault.pingram.from-name:${PINGRAM_FROM_NAME:SecretVault Security}}") String pingramFromName,
            @Value("${vault.pingram.from-address:${PINGRAM_FROM_ADDRESS:noreply@pingram.io}}") String pingramFromAddress) {
        this.mailSender = mailSender;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.fromAddress = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress.trim() : "security@secretvault.io";
        this.mailPassword = (mailPassword != null) ? mailPassword.trim() : "";
        this.pingramApiKey = (pingramApiKey != null) ? pingramApiKey.trim() : "";
        this.pingramBaseUrl = (pingramBaseUrl != null && !pingramBaseUrl.isBlank()) ? pingramBaseUrl.trim() : "https://api.pingram.io";
        this.pingramFromName = (pingramFromName != null && !pingramFromName.isBlank()) ? pingramFromName.trim() : "SecretVault Security";
        this.pingramFromAddress = (pingramFromAddress != null && !pingramFromAddress.isBlank()) ? pingramFromAddress.trim() : "noreply@pingram.io";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isPingramConfigured() {
        return pingramApiKey != null && !pingramApiKey.isBlank();
    }

    public boolean isSmtpConfigured() {
        return mailSender != null
                && fromAddress != null
                && !fromAddress.isBlank()
                && !fromAddress.equals("security@secretvault.io")
                && !mailPassword.isBlank();
    }

    public boolean isLiveDeliveryConfigured() {
        return isPingramConfigured() || isSmtpConfigured();
    }

    public void sendRegistrationOtp(String toEmail, String otp, String fullName, int expiryMinutes) {
        String subject = "SecretVault — Verify Your Account Code: " + otp;
        String htmlContent = buildEmailTemplate(
                "Verify Your SecretVault Account",
                "Hello " + (fullName != null && !fullName.isBlank() ? fullName : "Engineer") + ",",
                "Welcome to SecretVault. Please use the verification code below to activate your zero-knowledge security enclave and complete your registration.",
                otp,
                expiryMinutes,
                "This code will expire in " + expiryMinutes + " minutes. If you did not request this account, please ignore this email."
        );

        boolean sent = dispatchEmail(toEmail, subject, htmlContent, otp, "REGISTRATION_VERIFICATION");
        devOtpCache.put("reg:" + toEmail.toLowerCase(), new OtpPreview(
                otp,
                sent,
                isLiveDeliveryConfigured(),
                sent ? "Dispatched live email to " + toEmail : "Dev Sandbox: Live email delivery not configured"
        ));
    }

    public void sendPasswordResetOtp(String toEmail, String otp, String fullName, int expiryMinutes) {
        String subject = "SecretVault — Password Reset Security Code: " + otp;
        String htmlContent = buildEmailTemplate(
                "Password Reset Request",
                "Hello " + (fullName != null && !fullName.isBlank() ? fullName : "Security Admin") + ",",
                "A password reset request was initiated for your SecretVault identity. Use the verification code below to authorize the password change.",
                otp,
                expiryMinutes,
                "SECURITY WARNING: Never share this code with anyone. If you did NOT initiate this password reset, please secure your account immediately."
        );

        boolean sent = dispatchEmail(toEmail, subject, htmlContent, otp, "PASSWORD_RESET");
        devOtpCache.put("pwd:" + toEmail.toLowerCase(), new OtpPreview(
                otp,
                sent,
                isLiveDeliveryConfigured(),
                sent ? "Dispatched live email to " + toEmail : "Dev Sandbox: Live email delivery not configured"
        ));
    }

    public OtpPreview getOtpPreview(String key) {
        OtpPreview preview = devOtpCache.get(key);
        if (preview == null) {
            return new OtpPreview(null, false, isLiveDeliveryConfigured(), "No active verification code for this email.");
        }
        return preview;
    }

    public String getDevOtp(String key) {
        OtpPreview p = devOtpCache.get(key);
        return p != null ? p.otp() : null;
    }

    private boolean dispatchViaPingram(String toEmail, String subject, String htmlContent) {
        if (!isPingramConfigured()) {
            return false;
        }
        try {
            Map<String, Object> body = Map.of(
                    "type", "email_compose_preview",
                    "to", toEmail,
                    "subject", subject,
                    "html", htmlContent,
                    "fromName", pingramFromName,
                    "fromAddress", pingramFromAddress
            );
            String jsonPayload = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pingramBaseUrl.replaceAll("/+$", "") + "/email"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + pingramApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email successfully dispatched via Pingram API to [{}] (Status: {})", toEmail, response.statusCode());
                return true;
            } else {
                log.error("Pingram API delivery to [{}] failed with status [{}] body: {}", toEmail, response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Pingram API delivery error for [{}]: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    private boolean dispatchEmail(String toEmail, String subject, String htmlContent, String otp, String actionType) {
        boolean sentLive = false;
        String deliveryStatus = "NOT_SENT";

        // 1. Primary delivery channel: Pingram API
        if (isPingramConfigured()) {
            boolean pingramOk = dispatchViaPingram(toEmail, subject, htmlContent);
            if (pingramOk) {
                sentLive = true;
                deliveryStatus = "SENT_VIA_PINGRAM_API (" + pingramFromAddress + ")";
            } else {
                log.warn("Pingram delivery failed for [{}], attempting SMTP fallback if configured.", toEmail);
            }
        }

        // 2. Secondary delivery channel: Direct SMTP
        if (!sentLive && isSmtpConfigured()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlContent, true);

                mailSender.send(message);
                sentLive = true;
                deliveryStatus = "SENT_VIA_SMTP (" + fromAddress + ")";
                log.info("Email [{}] successfully dispatched via SMTP to [{}]", actionType, toEmail);
            } catch (Exception e) {
                deliveryStatus = "FAILED_SMTP (" + e.getMessage() + ")";
                log.error("SMTP delivery to [{}] failed ({}). Please verify your Gmail App Password in SPRING_MAIL_PASSWORD.", toEmail, e.getMessage());
            }
        }

        if (!sentLive && !isPingramConfigured() && !isSmtpConfigured()) {
            deliveryStatus = "DEV_CONSOLE (Live email provider not configured in .env)";
            log.info("No live email provider configured (Pingram / SMTP). Falling back to developer sandbox OTP.");
        }

        // Always log high-visibility security block in application logs
        log.info("\n" +
                "========================================================================================\n" +
                " [SECRETVAULT SECURE DISPATCHER] " + actionType + "\n" +
                " To:          " + toEmail + "\n" +
                " Subject:     " + subject + "\n" +
                " Security OTP:" + otp + "\n" +
                " Delivery:    " + deliveryStatus + "\n" +
                "========================================================================================");

        return sentLive;
    }

    private String buildEmailTemplate(String title, String greeting, String lead, String otp, int expiryMinutes, String warning) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0D0106; color: #FFFFFF; margin: 0; padding: 24px; }
                .container { max-width: 540px; margin: 0 auto; background-color: #30000F; border: 1px solid rgba(255, 180, 200, 0.25); border-radius: 16px; padding: 32px; box-shadow: 0 20px 40px rgba(0,0,0,0.8); }
                .badge { display: inline-block; padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: bold; background-color: #3F0016; color: #FF2D6D; border: 1px solid rgba(255, 45, 109, 0.4); text-transform: uppercase; margin-bottom: 12px; }
                h1 { font-size: 22px; font-weight: bold; margin: 0 0 16px 0; color: #FFFFFF; }
                p { font-size: 14px; line-height: 1.6; color: #F4B5C8; margin: 0 0 16px 0; }
                .otp-box { background: #3F0016; border: 2px dashed #FF2D6D; border-radius: 12px; text-align: center; padding: 20px; margin: 24px 0; }
                .otp-code { font-family: 'Courier New', Courier, monospace; font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #FFFFFF; }
                .warning { font-size: 12px; line-height: 1.5; color: #FFB4C8; background: rgba(255, 45, 109, 0.1); border-left: 3px solid #FF2D6D; padding: 12px; border-radius: 6px; margin-top: 24px; }
                .footer { margin-top: 32px; text-align: center; font-size: 11px; color: #A26377; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="badge">SecretVault Security Control Plane</div>
                <h1>%s</h1>
                <p><strong>%s</strong></p>
                <p>%s</p>
                <div class="otp-box">
                  <div style="font-size: 11px; text-transform: uppercase; color: #FF85A2; margin-bottom: 8px; font-weight: bold;">Your One-Time Security Code</div>
                  <div class="otp-code">%s</div>
                  <div style="font-size: 12px; color: #F4B5C8; margin-top: 8px;">Valid for %d minutes</div>
                </div>
                <div class="warning">%s</div>
                <div class="footer">
                  SecretVault Zero-Knowledge Cryptographic Enclave &bull; Automated Security Dispatcher
                </div>
              </div>
            </body>
            </html>
            """.formatted(title, greeting, lead, otp, expiryMinutes, warning);
    }
}
