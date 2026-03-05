package com.start.getemployed.kafka;

import com.start.getemployed.VerificationTokenGen;
import com.start.getemployed.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Map;


@Component
@RequiredArgsConstructor
public class EmailEventConsumer {
    private final VerificationTokenGen verificationTokenGen;
    private final EmailSenderService emailSenderService;
    @Value("${app.frontend.verify-url-pattern}")
    private String verifyUrlPattern;

    @KafkaListener(topics = "email-event", groupId = "email-service")
    public void handle(SendVerifyEmailEvent event){
        Logger logger = LoggerFactory.getLogger(this.getClass());
        logger.info("CONSUMED CONSUMED CONSUMED CONSUMED CONSUMED CONSUMED CONSUMED CONSUMED CONSUMED{}",event.email());
        String link = String.format(verifyUrlPattern, event.rawToken());

        emailSenderService.sendHtml(
                event.email(),
                "Подтверждение почты - GetEmployed",
                "mail/verify", //
                Map.of(
                        "name", event.name(),
                        "actionUrl", link,
                        "ttlMinutes", event.ttlMinutes()
                )
        );
    }
}
