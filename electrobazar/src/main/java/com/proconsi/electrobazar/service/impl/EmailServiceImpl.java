package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.AppSetting;
import com.proconsi.electrobazar.repository.AppSettingRepository;
import com.proconsi.electrobazar.util.AesEncryptionUtil;
import com.proconsi.electrobazar.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final AppSettingRepository appSettingRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    public EmailServiceImpl(AppSettingRepository appSettingRepository, AesEncryptionUtil aesEncryptionUtil) {
        this.appSettingRepository = appSettingRepository;
        this.aesEncryptionUtil = aesEncryptionUtil;
    }

    private JavaMailSenderImpl getMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        String host = appSettingRepository.findByKey("mail.host").map(AppSetting::getValue).orElse("smtp.gmail.com");
        String portStr = appSettingRepository.findByKey("mail.port").map(AppSetting::getValue).orElse("587");
        String username = appSettingRepository.findByKey("mail.username").map(AppSetting::getValue).orElse("");
        String passwordEncrypted = appSettingRepository.findByKey("mail.password").map(AppSetting::getValue).orElse("");
        String password = aesEncryptionUtil.decrypt(passwordEncrypted);

        int port = Integer.parseInt(portStr);
        mailSender.setPort(port);
        mailSender.setHost(host);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.debug", "false");

        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        return mailSender;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            JavaMailSenderImpl mailSender = getMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(mailSender.getUsername());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send simple email to {}", to, e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    @Override
    public void sendEmailWithAttachment(String to, String subject, String body, File attachment) {
        try {
            byte[] content = Files.readAllBytes(attachment.toPath());
            sendEmailWithAttachment(to, subject, body, attachment.getName(), content);
        } catch (IOException e) {
            log.error("Failed to read attachment file", e);
            throw new RuntimeException("Email sending failed: cannot read attachment", e);
        }
    }

    @Override
    public void sendEmailWithAttachment(String to, String subject, String body, String attachmentName,
            byte[] attachmentContent) {
        try {
            JavaMailSenderImpl mailSender = getMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailSender.getUsername());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            helper.addAttachment(attachmentName, new ByteArrayResource(attachmentContent));

            mailSender.send(message);
            log.info("Email with attachment sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment to {}", to, e);
            throw new RuntimeException("Email sending failed", e);
        }
    }
}
