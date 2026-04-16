package com.start.getemployed;

public record AuthResult(
        String accessToken,
        String refreshToken
) {
}
