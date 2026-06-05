package com.start.getemployed.common.api;

import java.util.UUID;

public record ErrorEnvelope(ErrorBody error, Meta meta) {

  public static ErrorEnvelope of(String code, String message, int status) {
    return new ErrorEnvelope(
        new ErrorBody(code, message, status), new Meta(UUID.randomUUID().toString()));
  }

  public record ErrorBody(String code, String message, int status) {}

  public record Meta(String requestId) {}
}
