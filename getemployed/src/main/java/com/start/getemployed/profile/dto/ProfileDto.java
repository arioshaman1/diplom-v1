package com.start.getemployed.profile.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Этот объект летит в Redis и возвращается клиенту. Serializable обязателен для работы со
 * стандартным RedisSerializer.
 */
public record ProfileDto(
    Long id,
    Long userId,
    String name,
    String email,
    String goal,
    String level,
    String city,
    Integer areaId,
    Integer salaryMin,
    Integer salaryMax,
    Boolean remote,
    Boolean onboardingCompleted,
    LocalDateTime updatedAt)
    implements Serializable {}
