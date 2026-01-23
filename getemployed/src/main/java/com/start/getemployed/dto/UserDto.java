package com.start.getemployed.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

public class UserDto {

    @Data
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
        private String name;
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