package com.start.getemployed.profile.mapper;

import com.start.getemployed.entity.Profile;
import com.start.getemployed.profile.dto.ProfileDto;
import com.start.getemployed.profile.dto.UpdateProfileRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

  @Mapping(source = "user.id", target = "userId")
  @Mapping(source = "user.name", target = "name")
  @Mapping(source = "user.email", target = "email")
  @Mapping(source = "onboardingComplete", target = "onboardingCompleted")
  ProfileDto toDto(Profile profile);

  @Mapping(target = "id", ignore = true) // ID не меняем
  @Mapping(target = "user", ignore = true) // Пользователя не меняем
  @Mapping(target = "updatedAt", ignore = true) // Обновится автоматически через @UpdateTimestamp
  @Mapping(target = "onboardingComplete", ignore = true) // Обычно меняется отдельной логикой
  void updateEntityFromRequest(UpdateProfileRequest request, @MappingTarget Profile profile);
}
