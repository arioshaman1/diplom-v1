package com.start.getemployed.vacancy.controller;

import com.start.getemployed.common.api.ApiEnvelope;
import com.start.getemployed.common.api.PageEnvelope;
import com.start.getemployed.common.api.UserContext;
import com.start.getemployed.entity.User;
import com.start.getemployed.vacancy.dto.ClusterDtos;
import com.start.getemployed.vacancy.dto.VacancyDtos;
import com.start.getemployed.vacancy.service.VacancyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class VacancyController {

  private final VacancyService vacancyService;
  private final UserContext userContext;

  @GetMapping("/vacancies")
  public PageEnvelope<List<VacancyDtos.VacancyListItem>> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "fetchedAt,desc") String sort,
      @RequestParam(required = false) Integer minScore,
      @RequestParam(required = false) Integer salaryMin,
      @RequestParam(required = false) Integer salaryMax,
      @RequestParam(required = false) Boolean remote,
      @RequestParam(required = false) Integer areaId,
      @RequestParam(required = false) String employer) {
    Page<VacancyDtos.VacancyListItem> result =
        vacancyService.list(
            userContext.currentUser(authentication),
            page,
            size,
            sort,
            minScore,
            salaryMin,
            salaryMax,
            remote,
            areaId,
            employer);
    return PageEnvelope.of(result.getContent(), vacancyService.pagination(result));
  }

  @GetMapping("/vacancies/{id}")
  public ApiEnvelope<VacancyDtos.VacancyDetail> detail(
      Authentication authentication, @PathVariable Long id) {
    return ApiEnvelope.of(vacancyService.detail(userContext.currentUser(authentication), id));
  }

  @GetMapping("/hh/areas")
  public ApiEnvelope<List<VacancyDtos.AreaOption>> hhAreas() {
    return ApiEnvelope.of(vacancyService.hhAreas());
  }

  @GetMapping("/recommendations/market-top-skills")
  public ApiEnvelope<List<ClusterDtos.ClusterItem>> getMarketTopSkills(
      @RequestParam String role, @RequestParam(required = false) Integer areaId) {

    return ApiEnvelope.of(vacancyService.fetchTopSkillsFromHhClusters(role, areaId));
  }

  @PostMapping("/vacancies/import")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiEnvelope<VacancyDtos.ImportAccepted> importVacancies(
      Authentication authentication,
      @RequestParam(required = false) String role,
      @RequestParam Integer areaId,
      @RequestParam(required = false) Integer salaryFrom,
      @RequestParam(required = false) String experience,
      @RequestParam(defaultValue = "5") int pages) {
    return ApiEnvelope.of(
        vacancyService.importVacancies(
            userContext.currentUser(authentication), role, areaId, salaryFrom, experience, pages));
  }

  @GetMapping("/vacancies/import/{jobId}")
  public ApiEnvelope<VacancyDtos.ImportStatus> importStatus(@PathVariable String jobId) {
    return ApiEnvelope.of(vacancyService.importStatus(jobId));
  }

  @PostMapping("/vacancies/{id}/save")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiEnvelope<VacancyDtos.SavedAt> save(
      Authentication authentication, @PathVariable Long id) {
    return ApiEnvelope.of(vacancyService.save(userContext.currentUser(authentication), id));
  }

  @DeleteMapping("/vacancies/{id}/save")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unsave(Authentication authentication, @PathVariable Long id) {
    vacancyService.unsave(userContext.currentUser(authentication), id);
  }

  @GetMapping("/vacancies/saved")
  public PageEnvelope<List<VacancyDtos.VacancyListItem>> saved(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<VacancyDtos.VacancyListItem> result =
        vacancyService.saved(userContext.currentUser(authentication), page, size);
    return PageEnvelope.of(result.getContent(), vacancyService.pagination(result));
  }

  @GetMapping("/recommendations")
  public PageEnvelope<List<VacancyDtos.RecommendationItem>> recommendations(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int limit,
      @RequestParam(defaultValue = "score") String sortBy,
      @RequestParam(defaultValue = "0") int minScore) {
    Page<VacancyDtos.RecommendationItem> result =
        vacancyService.recommendations(
            userContext.currentUser(authentication), page, limit, sortBy, minScore);
    return PageEnvelope.of(result.getContent(), vacancyService.pagination(result));
  }

  @GetMapping("/recommendations/{vacancyId}")
  public ApiEnvelope<VacancyDtos.RecommendationDetail> recommendationDetail(
      Authentication authentication, @PathVariable Long vacancyId) {
    return ApiEnvelope.of(
        vacancyService.recommendationDetail(userContext.currentUser(authentication), vacancyId));
  }

  @GetMapping("/recommendations/top-skills")
  public ApiEnvelope<List<VacancyDtos.TopSkill>> topSkills(
      Authentication authentication, @RequestParam(defaultValue = "10") int limit) {
    return ApiEnvelope.of(vacancyService.topSkills(userContext.currentUser(authentication), limit));
  }

  @GetMapping("/recommendations/skill-gaps")
  public ApiEnvelope<List<VacancyDtos.SkillGap>> skillGaps(
      Authentication authentication, @RequestParam(defaultValue = "0.1") double minFrequency) {
    return ApiEnvelope.of(
        vacancyService.skillGaps(userContext.currentUser(authentication), minFrequency));
  }

  @PostMapping("/recommendations/rebuild")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiEnvelope<VacancyDtos.RebuildAccepted> rebuild(Authentication authentication) {
    User user = userContext.currentUser(authentication);
    return ApiEnvelope.of(vacancyService.rebuild(user));
  }

  @DeleteMapping("/vacancies/clear")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearMyVacancies(Authentication authentication) {
    vacancyService.clearUserVacancies(userContext.currentUser(authentication));
  }

  @GetMapping("/recommendations/{vacancyId}/explain")
  public ApiEnvelope<VacancyDtos.ExplainResponse> explain(
      Authentication authentication, @PathVariable Long vacancyId) {
    return ApiEnvelope.of(
        vacancyService.explainRecommendation(userContext.currentUser(authentication), vacancyId));
  }
}
