package com.start.getemployed.auth.controller.auth;

import com.start.getemployed.auth.service.EmailVerificationService;
import com.start.getemployed.auth.service.AuthService;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.service.EmailSenderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/")
public class VerificationController {
    private final EmailSenderService emailSenderService;
    private final EmailVerificationService emailVerificationService;
    private final AuthService authService;

    public VerificationController(
            EmailVerificationService emailVerificationService,
            EmailSenderService emailSenderService,
            AuthService authService
    ){
        this.emailVerificationService = emailVerificationService;
        this.emailSenderService = emailSenderService;
        this.authService = authService;
    }

    @Value("${app.frontend.home-page-url}")
    private String homePageUrl;
    @Value("${app.frontend.verify-failed-url}")
    private String verifyFailedUrl;

    @PostMapping("/resend-verification")
    public Map<String, String> resendEmailVerification(@RequestParam String email) {
        User user = authService.findByEmailOrNull(email);

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
