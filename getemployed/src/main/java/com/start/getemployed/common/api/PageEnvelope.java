package com.start.getemployed.common.api;

import java.time.Instant;
import java.util.UUID;

public record PageEnvelope<T>(T data, Pagination pagination, Meta meta) {

  public static <T> PageEnvelope<T> of(T data, Pagination pagination) {
    return new PageEnvelope<>(data, pagination, Meta.create());
  }

  public record Pagination(int page, int size, long totalElements, int totalPages) {}

  public record Meta(String requestId, Instant timestamp) {
    public static Meta create() {
      return new Meta(UUID.randomUUID().toString(), Instant.now());
    }
  }
}
