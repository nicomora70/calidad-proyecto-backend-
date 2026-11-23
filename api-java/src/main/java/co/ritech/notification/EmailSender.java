package co.ritech.notification;

import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import co.ritech.config.RitechProperties;
import jakarta.mail.MessagingException;

/**
 * Sends notification e-mails through the client's SMTP server (SPRING_MAIL_HOST and related
 * variables). Without a host, e-mail is disabled and only the in-app inbox is used.
 */
@Component
public class EmailSender {

    static final String TEMPLATE = "mail/notification";
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private final JavaMailSender mailSender;
    private final ITemplateEngine templates;
    private final String from;

    public EmailSender(
            ObjectProvider<JavaMailSender> mailSender,
            ITemplateEngine templates,
            Environment environment,
            RitechProperties properties) {
        // A blank host still satisfies Spring Boot's mail auto-configuration, so check it here
        this.mailSender =
                StringUtils.hasText(environment.getProperty("spring.mail.host")) ? mailSender.getIfAvailable() : null;
        this.templates = templates;
        this.from = properties.notifications().mailFrom();
    }

    public boolean available() {
        return mailSender != null;
    }

    /**
     * @throws org.springframework.mail.MailException if the SMTP server rejects or cannot be reached
     */
    public void send(String to, String subject, String body) {
        if (mailSender == null) {
            throw new IllegalStateException("E-mail is not configured");
        }
        var message = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, render(subject, body));
        } catch (MessagingException ex) {
            throw new MailPreparationException("Could not build the e-mail", ex);
        }
        mailSender.send(message);
    }

    String render(String title, String body) {
        var context = new Context(ES);
        context.setVariable("title", title);
        context.setVariable("body", body);
        return templates.process(TEMPLATE, context);
    }
}
