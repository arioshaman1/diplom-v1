package com.start.getemployed.profile.controller;

import com.start.getemployed.common.api.ApiEnvelope;
import com.start.getemployed.common.api.UserContext;
import com.start.getemployed.profile.dto.ProfileDto;
import com.start.getemployed.profile.dto.UpdateProfileRequest;
import com.start.getemployed.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;
  private final UserContext userContext;

  @GetMapping
  public ApiEnvelope<ProfileDto> get(Authentication authentication) {
    return ApiEnvelope.of(profileService.get(userContext.currentUser(authentication)));
  }

  @PutMapping
  public ApiEnvelope<ProfileDto> update(
      Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
    return ApiEnvelope.of(profileService.update(userContext.currentUser(authentication), request));
  }

  @GetMapping("/stats")
  public ApiEnvelope<ProfileService.Stats> stats(Authentication authentication) {
    return ApiEnvelope.of(profileService.stats(userContext.currentUser(authentication)));
  }
}
