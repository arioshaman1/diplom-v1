package com.start.getemployed.skills.controller;

import com.start.getemployed.common.api.ApiEnvelope;
import com.start.getemployed.common.api.UserContext;
import com.start.getemployed.skills.dto.SkillDtos;
import com.start.getemployed.skills.service.SkillsService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SkillsController {

  private final SkillsService skillsService;
  private final UserContext userContext;

  @GetMapping("/profile/skills")
  public ApiEnvelope<List<SkillDtos.UserSkillResponse>> list(
      Authentication authentication, @RequestParam(defaultValue = "updatedAt,desc") String sort) {
    return ApiEnvelope.of(skillsService.list(userContext.currentUser(authentication), sort));
  }

  @PostMapping("/profile/skills")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiEnvelope<SkillDtos.UserSkillResponse> add(
      Authentication authentication, @Valid @RequestBody SkillDtos.AddSkillRequest request) {
    return ApiEnvelope.of(skillsService.add(userContext.currentUser(authentication), request));
  }

  @PutMapping("/profile/skills/{userSkillId}")
  public ApiEnvelope<SkillDtos.UserSkillResponse> update(
      Authentication authentication,
      @PathVariable Long userSkillId,
      @Valid @RequestBody SkillDtos.UpdateSkillRequest request) {
    return ApiEnvelope.of(
        skillsService.update(userContext.currentUser(authentication), userSkillId, request));
  }

  @DeleteMapping("/profile/skills/{userSkillId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication authentication, @PathVariable Long userSkillId) {
    skillsService.delete(userContext.currentUser(authentication), userSkillId);
  }

  @GetMapping("/skills/search")
  public ApiEnvelope<List<SkillDtos.SkillSearchResponse>> search(
      @RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
    return ApiEnvelope.of(skillsService.search(q).stream().limit(limit).toList());
  }
}
