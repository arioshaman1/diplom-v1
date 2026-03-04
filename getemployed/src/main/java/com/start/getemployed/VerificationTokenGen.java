package com.start.getemployed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class VerificationTokenGen {
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.email-token-pepper}")
    private String pepperEmail;

    public String newRawToken() {
        byte[] b = new byte[32];
        secureRandom.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((rawToken + pepperEmail).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
