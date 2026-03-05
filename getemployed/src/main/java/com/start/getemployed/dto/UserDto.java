package com.start.getemployed.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

public class UserDto {

    @Schema(description = "Запрос на создание пользователя")
    public static class CreateRequest {

        @Schema(description = "Email пользователя", example = "user@example.com")
        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный формат email")
        private String email;

        @Schema(description = "Пароль", example = "MySecurePassword123!")
        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
        private String password;

        @Schema(description = "Имя пользователя", example = "Иван Иванов")
        @NotBlank
        private String name;

        public CreateRequest() {
        }

        public @NotBlank(message = "Email обязателен") @Email(message = "Некорректный формат email") String getEmail() {
            return this.email;
        }

        public @NotBlank(message = "Пароль обязателен") @Size(min = 8, message = "Пароль должен содержать минимум 8 символов") String getPassword() {
            return this.password;
        }

        public @NotBlank String getName() {
            return this.name;
        }

        public void setEmail(@NotBlank(message = "Email обязателен") @Email(message = "Некорректный формат email") String email) {
            this.email = email;
        }

        public void setPassword(@NotBlank(message = "Пароль обязателен") @Size(min = 8, message = "Пароль должен содержать минимум 8 символов") String password) {
            this.password = password;
        }

        public void setName(@NotBlank String name) {
            this.name = name;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof CreateRequest)) return false;
            final CreateRequest other = (CreateRequest) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$email = this.getEmail();
            final Object other$email = other.getEmail();
            if (this$email == null ? other$email != null : !this$email.equals(other$email)) return false;
            final Object this$password = this.getPassword();
            final Object other$password = other.getPassword();
            if (this$password == null ? other$password != null : !this$password.equals(other$password)) return false;
            final Object this$name = this.getName();
            final Object other$name = other.getName();
            if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof CreateRequest;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $email = this.getEmail();
            result = result * PRIME + ($email == null ? 43 : $email.hashCode());
            final Object $password = this.getPassword();
            result = result * PRIME + ($password == null ? 43 : $password.hashCode());
            final Object $name = this.getName();
            result = result * PRIME + ($name == null ? 43 : $name.hashCode());
            return result;
        }

        public String toString() {
            return "UserDto.CreateRequest(email=" + this.getEmail() + ", password=" + this.getPassword() + ", name=" + this.getName() + ")";
        }
    }

    @Data
    @Schema(description = "Ответ с данными пользователя")
    public static class Response {

        @Schema(description = "ID пользователя", example = "1")
        private Long id;

        @Schema(description = "Email пользователя", example = "user@example.com")
        private String email;

        @Schema(description = "Имя пользователя", example = "Иван Иванов")
        private String name;

        @Schema(description = "Активен ли пользователь", example = "true")
        private boolean enabled;

        @Schema(description = "Дата создания")
        private LocalDateTime createdAt;

        @Schema(description = "Дата обновления")
        private LocalDateTime updatedAt;

        @Schema(description = "Роли пользователя")
        private Set<String> roles;
    }

    @Data
    @Schema(description = "Запрос на обновление пользователя")
    public static class UpdateRequest {

        @Schema(description = "Имя пользователя", example = "Иван Иванов")
        private String name;

        @Schema(description = "Активен ли пользователь", example = "true")
        private Boolean enabled;
    }

    @Data
    @Schema(description = "Запрос на смену пароля")
    public static class ChangePasswordRequest {

        @Schema(description = "Текущий пароль", example = "OldPassword123!")
        @NotBlank(message = "Текущий пароль обязателен")
        private String currentPassword;

        @Schema(description = "Новый пароль", example = "NewSecurePassword456!")
        @NotBlank(message = "Новый пароль обязателен")
        @Size(min = 8, message = "Новый пароль должен содержать минимум 8 символов")
        private String newPassword;
    }
}