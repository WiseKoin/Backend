package com.wisekoin.backend.module.user.otp;

import com.wisekoin.backend.module.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpDeliveryService implements OtpDeliveryService {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public EmailOtpDeliveryService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.otp.from:}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String email, OtpPurpose purpose, String code, long expirationMinutes) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || fromAddress.isBlank()) {
            throw new ServiceUnavailableException(
                    "Email verification is temporarily unavailable. Please try again later.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("WiseKoin security verification code");
        message.setText(
                "Your WiseKoin verification code is "
                        + code
                        + ". It expires in "
                        + expirationMinutes
                        + " minutes. If you did not request this action, ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ServiceUnavailableException(
                    "We could not send a verification email. Please try again later.", exception);
        }
    }
}
