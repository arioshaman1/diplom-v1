package com.start.getemployed.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

public class AuthDto {
  public record UserDto(String id, String email, String name) {}
  public record RefreshResponse(String accessToken) {}



  @Data
  @NoArgsConstructor
  @AllArgsConstructor

  @Schema(description = "Запрос на аутентификацию")
  public static class LoginRequest {

    @Schema(description = "Email пользователя", example = "user@example.com")
    @NotBlank(message = "Email обязателен")
    private String email;

    @Schema(description = "Пароль", example = "MySecurePassword123!")
    @NotBlank(message = "Пароль обязателен")
    private String password;
  }
  public static class LoginResponse {
    private String accessToken;
    private UserDto user;

    public LoginResponse(String accessToken, UserDto user) {
      this.accessToken = accessToken;
      this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public UserDto getUser() { return user; }
  }

}

