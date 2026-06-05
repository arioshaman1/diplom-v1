package com.start.getemployed.common.api;

import java.time.Instant;
import java.util.UUID;

public record ApiEnvelope<T>(T data, Meta meta) {

  public static <T> ApiEnvelope<T> of(T data) {
    return new ApiEnvelope<>(data, Meta.create());
  }

  public record Meta(String requestId, Instant timestamp) {
    public static Meta create() {
      return new Meta(UUID.randomUUID().toString(), Instant.now());
    }
  }
}
