package com.start.getemployed.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AuthDto {

  @Data
  @Schema(description = "Запрос на аутентификацию")
  public static class LoginRequest {

    @Schema(description = "Email пользователя", example = "user@example.com")
    @NotBlank(message = "Email обязателен")
    private String email;

    @Schema(description = "Пароль", example = "MySecurePassword123!")
    @NotBlank(message = "Пароль обязателен")
    private String password;
  }

  @Data
  @Schema(description = "Ответ с JWT токеном")
  public static class LoginResponse {

    @Schema(description = "JWT токен доступа")
    private String accessToken;

    @Schema(description = "Тип токена", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Email пользователя")
    private String email;

    @Schema(description = "Имя пользователя")
    private String name;

    @Schema(description = "Роли пользователя")
    private java.util.Set<String> roles;

    public LoginResponse(
        String accessToken, String email, String name, java.util.Set<String> roles) {
      this.accessToken = accessToken;
      this.email = email;
      this.name = name;
      this.roles = roles;
    }
  }
}
