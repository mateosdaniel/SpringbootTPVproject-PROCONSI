package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.AppSetting;
import com.proconsi.electrobazar.repository.AppSettingRepository;
import com.proconsi.electrobazar.util.AesEncryptionUtil;
import com.proconsi.electrobazar.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final AppSettingRepository appSettingRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    private JavaMailSender getDynamicMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        
        String host = appSettingRepository.findByKey("mail.host").map(AppSetting::getValue).orElse("smtp.gmail.com");
        String portStr = appSettingRepository.findByKey("mail.port").map(AppSetting::getValue).orElse("587");
        String username = appSettingRepository.findByKey("mail.username").map(AppSetting::getValue).orElse("");
        String encryptedPassword = appSettingRepository.findByKey("mail.password").map(AppSetting::getValue).orElse("");
        String password = aesEncryptionUtil.decrypt(encryptedPassword);

        sender.setHost(host);
        sender.setPort(Integer.parseInt(portStr));
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        return sender;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("Sending simple email to {}", to);
        JavaMailSender mailSender = getDynamicMailSender();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    @Override
    public void sendEmailWithAttachment(String to, String subject, String body, File attachment) {
        log.info("Sending email with file attachment to {}", to);
        try {
            JavaMailSender mailSender = getDynamicMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachment.getName(), attachment);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment", e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    @Override
    public void sendEmailWithAttachment(String to, String subject, String body, String attachmentName, byte[] attachmentContent) {
        log.info("Sending email with byte array attachment to {}", to);
        try {
            JavaMailSender mailSender = getDynamicMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachmentName, new ByteArrayResource(attachmentContent));
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment", e);
            throw new RuntimeException("Email sending failed", e);
        }
    }
}
