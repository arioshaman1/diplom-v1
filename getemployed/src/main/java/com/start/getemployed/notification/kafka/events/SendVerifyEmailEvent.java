package com.start.getemployed.notification.kafka.events;

public record SendVerifyEmailEvent(String email, String name, String rawToken, Long ttlMinutes) {}
