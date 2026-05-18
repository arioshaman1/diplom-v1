package com.start.getemployed.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Данные, которые приходят с фронтенда при редактировании. */
public record UpdateProfileRequest(
    @NotBlank(message = "Цель не может быть пустой") String goal,
    @NotBlank(message = "Уровень (Junior/Middle) обязателен") String level,
    String city,
    Integer areaId,
    @Positive(message = "Зарплата должна быть положительной") Integer salaryMin,
    @Positive(message = "Зарплата должна быть положительной") Integer salaryMax,
    @NotNull Boolean remote) {}
