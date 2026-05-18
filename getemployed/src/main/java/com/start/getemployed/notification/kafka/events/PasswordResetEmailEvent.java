package com.start.getemployed.notification.kafka.events;

public record PasswordResetEmailEvent(
    String email, String name, String resetToken, int ttlMinutes) {}
