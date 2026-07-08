package com.start.getemployed.admin.controller;

import com.start.getemployed.auth.dto.UserDto;
import com.start.getemployed.auth.repository.UserRepository;
import com.start.getemployed.common.api.ApiEnvelope;
import com.start.getemployed.common.api.PageEnvelope;
import com.start.getemployed.entity.Skill;
import com.start.getemployed.entity.User;
import com.start.getemployed.exception.ResourceAlreadyExistsException;
import com.start.getemployed.exception.ResourceNotFoundException;
import com.start.getemployed.skills.repository.SkillRepository;
import com.start.getemployed.vacancy.service.VacancyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

  private final UserRepository userRepository;
  private final SkillRepository skillRepository;
  private final VacancyService vacancyService;

  @GetMapping("/users")
  public PageEnvelope<List<UserAdminItem>> users() {
    Page<User> page = userRepository.findAll(PageRequest.of(0, 20));
    return PageEnvelope.of(
        page.stream()
            .map(
                u ->
                    new UserAdminItem(
                        u.getId(), u.getEmail(), u.getCreatedAt(), 0, u.getUpdatedAt()))
            .toList(),
        new PageEnvelope.Pagination(
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
  }

  @GetMapping("/users/{userId}")
  public ApiEnvelope<UserDto.Response> user(@PathVariable Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    UserDto.Response response = new UserDto.Response();
    response.setId(user.getId());
    response.setEmail(user.getEmail());
    response.setName(user.getName());
    response.setEnabled(user.isEnabled());
    response.setCreatedAt(user.getCreatedAt());
    response.setUpdatedAt(user.getUpdatedAt());
    return ApiEnvelope.of(response);
  }

  @DeleteMapping("/users/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(@PathVariable Long userId) {
    userRepository.deleteById(userId);
  }

  @GetMapping("/skills")
  public ApiEnvelope<List<SkillAdminItem>> skills() {
    return ApiEnvelope.of(
        skillRepository.findAll().stream()
            .map(s -> new SkillAdminItem(s.getId(), s.getName(), s.getCategory(), 0, 0))
            .toList());
  }

  @PostMapping("/skills")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiEnvelope<SkillAdminItem> addSkill(@Valid @RequestBody SkillRequest request) {
    if (skillRepository.findByNameIgnoreCase(request.name()).isPresent()) {
      throw new ResourceAlreadyExistsException("Навык уже существует");
    }
    Skill skill = new Skill();
    skill.setName(request.name());
    skill.setCategory(request.category());
    skill.setPopularity(0.0);
    Skill saved = skillRepository.save(skill);
    return ApiEnvelope.of(
        new SkillAdminItem(saved.getId(), saved.getName(), saved.getCategory(), 0, 0));
  }

  @PutMapping("/skills/{id}")
  public ApiEnvelope<SkillAdminItem> updateSkill(
      @PathVariable Long id, @Valid @RequestBody SkillRequest request) {
    Skill skill =
        skillRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Навык не найден"));
    skill.setName(request.name());
    skill.setCategory(request.category());
    Skill saved = skillRepository.save(skill);
    return ApiEnvelope.of(
        new SkillAdminItem(saved.getId(), saved.getName(), saved.getCategory(), 0, 0));
  }

  @DeleteMapping("/skills/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSkill(@PathVariable Long id) {
    skillRepository.deleteById(id);
  }

  @DeleteMapping("/vacancies/all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAllVacancies() {
    vacancyService.clearAllVacancies();
  }

  @PostMapping("/vacancies/reimport")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiEnvelope<JobAccepted> reimport() {
    return ApiEnvelope.of(new JobAccepted(UUID.randomUUID().toString()));
  }

  @GetMapping("/jobs/{jobId}")
  public ApiEnvelope<JobStatus> job(@PathVariable String jobId) {
    return ApiEnvelope.of(new JobStatus(jobId, "VACANCY_IMPORT", "COMPLETED", 100, 100, List.of()));
  }

  public record UserAdminItem(
      Long id,
      String email,
      java.time.LocalDateTime createdAt,
      long totalVacancies,
      java.time.LocalDateTime lastActive) {}

  public record SkillAdminItem(
      Long id, String name, String category, long vacancyCount, long userCount) {}

  public record SkillRequest(@NotBlank String name, String category, List<String> aliases) {}

  public record JobAccepted(String jobId) {}

  public record JobStatus(
      String jobId, String type, String status, int progress, int total, List<String> errors) {}
}
