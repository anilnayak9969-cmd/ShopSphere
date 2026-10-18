package com.example.ecommerce.services.impl;

import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.enums.OtpPurpose;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.services.EmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends HTML emails through SMTP (configured with spring.mail.* properties).
 *
 * Development fallback: when spring.mail.username is empty, emails are not sent and the OTP is
 * printed in the server console instead, so you can test registration without a Gmail app
 * password. Always configure real SMTP credentials before deploying.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.mail.from-name:ShopSphere}")
    private String fromName;

    @Value("${app.frontend.public-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void sendOtpEmail(String to, String name, String otp, OtpPurpose purpose, int expiryMinutes) {
        boolean registration = purpose == OtpPurpose.REGISTRATION;
        String subject = registration ? "Verify your ShopSphere account" : "Reset your ShopSphere password";
        String intro = registration
                ? "Welcome to ShopSphere. Use this code to verify your email address:"
                : "We received a request to reset your password. Use this code to continue:";
        String body = """
                <p style="margin:0 0 16px">Hello %s,</p>
                <p style="margin:0 0 24px">%s</p>
                <div style="font-size:32px;letter-spacing:10px;font-weight:700;color:#0E1116;background:#F6F1E7;
                            border:1px solid #E4D3AE;border-radius:12px;padding:18px 0;text-align:center">%s</div>
                <p style="margin:24px 0 0;color:#6B6B6B">This code expires in %d minutes. If you did not request it,
                   you can safely ignore this email — your account is unchanged.</p>
                """.formatted(HtmlUtils.htmlEscape(name), intro, otp, expiryMinutes);

        if (!isConfigured()) {
            log.warn("[DEV MODE] Email not configured. {} code for {}: {}", purpose, to, otp);
            return;
        }
        send(to, subject, wrap(subject, body));
    }

    @Override
    public void sendOrderConfirmation(CustomerOrder order) {
        if (!isConfigured()) {
            log.info("[DEV MODE] Email not configured. Order {} confirmed for {}", order.getOrderNumber(),
                    order.getCustomer().getEmail());
            return;
        }
        try {
            NumberFormat inr = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            StringBuilder rows = new StringBuilder();
            for (OrderItem item : order.getItems()) {
                rows.append("""
                        <tr><td style="padding:8px 0">%s &times; %d</td>
                            <td style="padding:8px 0;text-align:right">%s</td></tr>
                        """.formatted(HtmlUtils.htmlEscape(item.getProductName()), item.getQuantity(),
                        inr.format(item.getLineTotal())));
            }
            String body = """
                    <p style="margin:0 0 16px">Hello %s,</p>
                    <p style="margin:0 0 20px">Thank you for your order. Your payment was received and order
                       <strong>%s</strong> has been placed.</p>
                    <table style="width:100%%;border-collapse:collapse;border-top:1px solid #E6E1D6">%s
                      <tr><td style="padding:12px 0;border-top:1px solid #E6E1D6"><strong>Total paid</strong></td>
                          <td style="padding:12px 0;border-top:1px solid #E6E1D6;text-align:right"><strong>%s</strong></td></tr>
                    </table>
                    <p style="margin:24px 0 0"><a href="%s/account/orders/%d" style="color:#8A6D2F">Track your order</a></p>
                    """.formatted(HtmlUtils.htmlEscape(order.getCustomer().getFullName()), order.getOrderNumber(), rows,
                    inr.format(order.getTotalAmount()), frontendUrl, order.getOrderId());
            send(order.getCustomer().getEmail(), "Order confirmed — " + order.getOrderNumber(),
                    wrap("Order confirmed", body));
        } catch (ApiException ex) {
            log.error("Order confirmation email failed for {}", order.getOrderNumber());
        }
    }

    private boolean isConfigured() {
        return fromAddress != null && !fromAddress.isBlank();
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException | UnsupportedEncodingException ex) {
            log.error("Failed to send '{}' to {}: {}", subject, to, ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_FAILED",
                    "We couldn't send the email right now. Please try again in a moment.");
        }
    }

    private String wrap(String title, String content) {
        return """
                <div style="background:#F4F1EA;padding:32px 12px;font-family:Arial,Helvetica,sans-serif;color:#1C1C1C">
                  <div style="max-width:520px;margin:0 auto;background:#FFFFFF;border-radius:16px;overflow:hidden;
                              border:1px solid #E6E1D6">
                    <div style="background:#0E1116;padding:22px 28px;color:#E9D8A6;font-size:20px;letter-spacing:3px">
                      SHOPSPHERE
                    </div>
                    <div style="padding:28px">
                      <h2 style="margin:0 0 18px;font-weight:600;font-size:20px">%s</h2>
                      %s
                    </div>
                  </div>
                </div>
                """.formatted(HtmlUtils.htmlEscape(title), content);
    }
}
