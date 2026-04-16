package com.start.getemployed.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

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

    public String getAccessToken() {
      return this.accessToken;
    }

    public String getTokenType() {
      return this.tokenType;
    }

    public String getEmail() {
      return this.email;
    }

    public String getName() {
      return this.name;
    }

    public Set<String> getRoles() {
      return this.roles;
    }

    public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
    }

    public void setTokenType(String tokenType) {
      this.tokenType = tokenType;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setRoles(Set<String> roles) {
      this.roles = roles;
    }

    public boolean equals(final Object o) {
      if (o == this) return true;
      if (!(o instanceof LoginResponse)) return false;
      final LoginResponse other = (LoginResponse) o;
      if (!other.canEqual((Object) this)) return false;
      final Object this$accessToken = this.getAccessToken();
      final Object other$accessToken = other.getAccessToken();
      if (this$accessToken == null ? other$accessToken != null : !this$accessToken.equals(other$accessToken))
        return false;
      final Object this$tokenType = this.getTokenType();
      final Object other$tokenType = other.getTokenType();
      if (this$tokenType == null ? other$tokenType != null : !this$tokenType.equals(other$tokenType)) return false;
      final Object this$email = this.getEmail();
      final Object other$email = other.getEmail();
      if (this$email == null ? other$email != null : !this$email.equals(other$email)) return false;
      final Object this$name = this.getName();
      final Object other$name = other.getName();
      if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
      final Object this$roles = this.getRoles();
      final Object other$roles = other.getRoles();
      if (this$roles == null ? other$roles != null : !this$roles.equals(other$roles)) return false;
      return true;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof LoginResponse;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      final Object $accessToken = this.getAccessToken();
      result = result * PRIME + ($accessToken == null ? 43 : $accessToken.hashCode());
      final Object $tokenType = this.getTokenType();
      result = result * PRIME + ($tokenType == null ? 43 : $tokenType.hashCode());
      final Object $email = this.getEmail();
      result = result * PRIME + ($email == null ? 43 : $email.hashCode());
      final Object $name = this.getName();
      result = result * PRIME + ($name == null ? 43 : $name.hashCode());
      final Object $roles = this.getRoles();
      result = result * PRIME + ($roles == null ? 43 : $roles.hashCode());
      return result;
    }

    public String toString() {
      return "AuthDto.LoginResponse(accessToken=" + this.getAccessToken() + ", tokenType=" + this.getTokenType() + ", email=" + this.getEmail() + ", name=" + this.getName() + ", roles=" + this.getRoles() + ")";
    }
  }
}
