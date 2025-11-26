package dev.somdip.containerplatform.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email:noreply@snapdeploy.dev}")
    private String fromEmail;

    @Value("${sendgrid.from.name:SnapDeploy}")
    private String fromName;

    public void sendPasswordResetOTP(String toEmail, String otp, String userName) {
        String subject = "Password Reset OTP - SnapDeploy";
        String htmlContent = buildPasswordResetEmail(userName, otp);
        
        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendWelcomeEmail(String toEmail, String userName) {
        String subject = "Welcome to SnapDeploy!";
        String htmlContent = buildWelcomeEmail(userName);

        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendFeedbackNotification(String userEmail, String userName, String feedbackMessage, String category) {
        String toEmail = "contact@snapdeploy.dev";
        String subject = "[Feedback] User Feedback from " + userName;
        String htmlContent = buildFeedbackEmail(userEmail, userName, feedbackMessage, category);

        sendEmail(toEmail, subject, htmlContent);
    }

    public void sendBugReportNotification(String userEmail, String userName, String title, String description,
                                         String stepsToReproduce, String severity) {
        String toEmail = "contact@snapdeploy.dev";
        String subject = "[Bug Report - " + severity.toUpperCase() + "] " + title;
        String htmlContent = buildBugReportEmail(userEmail, userName, title, description, stepsToReproduce, severity);

        sendEmail(toEmail, subject, htmlContent);
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Email sent successfully to: {} | Status: {}", toEmail, response.getStatusCode());
            } else {
                log.error("Failed to send email to: {} | Status: {} | Body: {}", 
                    toEmail, response.getStatusCode(), response.getBody());
            }
        } catch (IOException e) {
            log.error("Error sending email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    private String buildPasswordResetEmail(String userName, String otp) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .otp-code { background: #667eea; color: white; font-size: 32px; font-weight: bold; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0; letter-spacing: 5px; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 Password Reset Request</h1>
                    </div>
                    <div class="content">
                        <p>Hello <strong>%s</strong>,</p>
                        <p>We received a request to reset your password for your SnapDeploy account.</p>
                        <p>Use the following OTP code to reset your password:</p>
                        
                        <div class="otp-code">%s</div>
                        
                        <p><strong>This code will expire in 10 minutes.</strong></p>
                        
                        <div class="warning">
                            <strong>⚠️ Security Notice:</strong> If you didn't request this password reset, please ignore this email. Your account remains secure.
                        </div>
                        
                        <p>Best regards,<br>The SnapDeploy Team</p>
                    </div>
                    <div class="footer">
                        <p>SnapDeploy - Simple Container Hosting for Developers</p>
                        <p>© 2025 SnapDeploy. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, otp);
    }

    private String buildWelcomeEmail(String userName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .button { background: #667eea; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 20px 0; }
                    .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🚀 Welcome to SnapDeploy!</h1>
                    </div>
                    <div class="content">
                        <p>Hello <strong>%s</strong>,</p>
                        <p>Welcome to SnapDeploy! Your account has been successfully created.</p>
                        <p>Start deploying your containers in 60 seconds with our simple platform.</p>
                        
                        <a href="https://snapdeploy.dev/dashboard" class="button">Go to Dashboard</a>
                        
                        <p>Best regards,<br>The SnapDeploy Team</p>
                    </div>
                    <div class="footer">
                        <p>SnapDeploy - Simple Container Hosting for Developers</p>
                        <p>© 2025 SnapDeploy. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName);
    }

    private String buildFeedbackEmail(String userEmail, String userName, String feedbackMessage, String category) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .info-box { background: white; border-left: 4px solid #667eea; padding: 15px; margin: 20px 0; }
                    .feedback-box { background: #fff; padding: 20px; border-radius: 8px; margin: 20px 0; border: 1px solid #ddd; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>💬 User Feedback Received</h1>
                    </div>
                    <div class="content">
                        <div class="info-box">
                            <strong>From:</strong> %s (%s)<br>
                            <strong>Category:</strong> %s
                        </div>

                        <h3>Feedback Message:</h3>
                        <div class="feedback-box">
                            %s
                        </div>

                        <p><em>User has been awarded +50 bonus hours for providing feedback.</em></p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, userEmail, category != null ? category : "General", feedbackMessage);
    }

    private String buildBugReportEmail(String userEmail, String userName, String title, String description,
                                      String stepsToReproduce, String severity) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 700px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #ef4444 0%%, #dc2626 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }
                    .info-box { background: white; border-left: 4px solid #ef4444; padding: 15px; margin: 20px 0; }
                    .severity-critical { background: #fee2e2; border-left: 4px solid #dc2626; }
                    .severity-high { background: #fed7aa; border-left: 4px solid #ea580c; }
                    .severity-medium { background: #fef3c7; border-left: 4px solid #ca8a04; }
                    .severity-low { background: #dbeafe; border-left: 4px solid #3b82f6; }
                    .bug-section { background: white; padding: 20px; border-radius: 8px; margin: 15px 0; border: 1px solid #ddd; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🐛 Bug Report Received</h1>
                    </div>
                    <div class="content">
                        <div class="info-box severity-%s">
                            <strong>From:</strong> %s (%s)<br>
                            <strong>Severity:</strong> %s<br>
                            <strong>Title:</strong> %s
                        </div>

                        <h3>Description:</h3>
                        <div class="bug-section">
                            %s
                        </div>

                        <h3>Steps to Reproduce:</h3>
                        <div class="bug-section">
                            %s
                        </div>

                        <p><em>⚠️ Review this bug report and decide on bonus hours award (0-50 hours or 1 month free subscription)</em></p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(severity, userName, userEmail, severity.toUpperCase(), title, description, stepsToReproduce);
    }
}
