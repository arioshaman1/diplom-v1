package com.start.getemployed.kafka;

public record SendVerifyEmailEvent(
    Long userId, String email, String name, String rawToken, long ttlMinutes) {}
