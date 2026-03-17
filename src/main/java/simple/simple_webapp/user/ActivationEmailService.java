package simple.simple_webapp.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
class ActivationEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.from}")
    private String from;

    ActivationEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    void sendActivationEmail(String to, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Activate your account");
        message.setText("Click the link below to activate your account (valid for 24 hours):\n\n"
                + baseUrl + "/activate?token=" + token);
        mailSender.send(message);
    }
}
