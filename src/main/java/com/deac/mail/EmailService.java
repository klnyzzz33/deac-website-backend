package com.deac.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class EmailService {

    private final JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String username;

    @Autowired
    public EmailService(@Qualifier("javaMailSender") JavaMailSender emailSender, Executor mailExecutor) {
        this.emailSender = emailSender;
    }

    @Async("mailExecutor")
    public void sendMessage(String to, String subject, String text, List<Attachment> attachments) throws MessagingException {
        try {
            MimeMessage mimeMessage = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "utf-8");
            helper.setFrom(username);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);
            if (!attachments.isEmpty()) {
                for (Attachment attachment : attachments) {
                    helper.addAttachment(attachment.getName(), new ByteArrayResource(attachment.getContent()));
                }
            }
            emailSender.send(mimeMessage);
        } catch (MailException ignored) {
        }
    }

}
