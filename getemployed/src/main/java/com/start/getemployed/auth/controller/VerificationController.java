package com.start.getemployed.auth.controller;

import com.start.getemployed.auth.service.EmailVerificationService;
import com.start.getemployed.auth.service.UserService;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.service.EmailSenderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.time.ZonedDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {
    private final EmailSenderService emailSenderService;
    private final EmailVerificationService emailVerificationService;
    private final UserService userService;

    public VerificationController(
            EmailVerificationService emailVerificationService,
            EmailSenderService emailSenderService,
            UserService userService
    ){
        this.emailVerificationService = emailVerificationService;
        this.emailSenderService = emailSenderService;
        this.userService = userService;
    }

    @Value("${app.frontend.home-page-url}")
    private String homePageUrl;
    @Value("${app.frontend.verify-failed-url}")
    private String verifyFailedUrl;

    @PostMapping("/send-email")
    public String send(@RequestParam String to) {
        emailSenderService.sendHtml(
                to,
                "GetEmployed — завершите регистрацию ✅",
                "mail/verify",
                Map.of(
                        "subject", "GetEmployed — тестовое письмо ✅",
                        "appName", "GetEmployed",
                        "name", "User",
                        "sentAt", ZonedDateTime.now().toString(),
                        "env", "local",
                        "actionUrl", "http://localhost:8080"));
        return "sent";
    }

    @PostMapping("/resend-verification")
    public Map<String, String> resendEmailVerification(@RequestParam String email) {
        User user = userService.findByEmailOrNull(email);

        if (user != null && !user.isEnabled()) {
            emailVerificationService.resendVerification(user);
        }
        return Map.of("status", "if_exists_sent");
    }

    @PostMapping("/verify")
    public RedirectView verify(@RequestParam("token") String token) {
        try {
            emailVerificationService.verify(token);
            return new RedirectView(homePageUrl);
        }
        catch (Exception e) {
            return new RedirectView(verifyFailedUrl);
        }
    }
}
