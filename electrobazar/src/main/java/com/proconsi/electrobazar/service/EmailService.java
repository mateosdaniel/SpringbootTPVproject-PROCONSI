package com.proconsi.electrobazar.service;

import java.io.File;

/**
 * Service for sending emails.
 */
public interface EmailService {

    /**
     * Sends a simple email.
     * @param to Recipient email address.
     * @param subject Email subject.
     * @param body Email body.
     */
    void sendEmail(String to, String subject, String body);

    /**
     * Sends an email with an attachment.
     * @param to Recipient email address.
     * @param subject Email subject.
     * @param body Email body.
     * @param attachment Attachment file.
     */
    void sendEmailWithAttachment(String to, String subject, String body, File attachment);

    /**
     * Sends an email with an attachment as byte array.
     * @param to Recipient email address.
     * @param subject Email subject.
     * @param body Email body.
     * @param attachmentName Name of the attachment.
     * @param attachmentContent Content of the attachment.
     */
    void sendEmailWithAttachment(String to, String subject, String body, String attachmentName, byte[] attachmentContent);
}
