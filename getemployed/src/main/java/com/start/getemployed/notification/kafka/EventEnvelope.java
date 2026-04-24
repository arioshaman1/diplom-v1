package com.start.getemployed.notification.kafka;

public record EventEnvelope<T>(String type, T payload) {}