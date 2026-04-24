package com.start.getemployed.notification.kafka;

public class PasswordResetEmailEvent {

    private String email;
    private String token;

    public PasswordResetEmailEvent() {}

    public PasswordResetEmailEvent(String email, String token) {
        this.email = email;
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }
}