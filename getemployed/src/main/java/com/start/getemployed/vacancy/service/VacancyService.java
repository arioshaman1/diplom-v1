package com.start.getemployed.vacancy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.getemployed.common.api.PageEnvelope;
import com.start.getemployed.entity.Recommendation;
import com.start.getemployed.entity.SavedVacancy;
import com.start.getemployed.entity.Skill;
import com.start.getemployed.entity.User;
import com.start.getemployed.entity.UserSkill;
import com.start.getemployed.entity.UserVacancy;
import com.start.getemployed.entity.Vacancy;
import com.start.getemployed.entity.VacancySkill;
import com.start.getemployed.exception.ResourceAlreadyExistsException;
import com.start.getemployed.exception.ResourceNotFoundException;
import com.start.getemployed.profile.repository.ProfileRepository;
import com.start.getemployed.skills.repository.SkillRepository;
import com.start.getemployed.skills.repository.UserSkillRepository;
import com.start.getemployed.trajectory.service.AiTrajectoryPlanner;
import com.start.getemployed.vacancy.dto.ClusterDtos;
import com.start.getemployed.vacancy.dto.VacancyDtos;
import com.start.getemployed.vacancy.repository.RecommendationRepository;
import com.start.getemployed.vacancy.repository.SavedVacancyRepository;
import com.start.getemployed.vacancy.repository.UserVacancyRepository;
import com.start.getemployed.vacancy.repository.VacancyRepository;
import com.start.getemployed.vacancy.repository.VacancySkillRepository;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VacancyService {

  private final VacancyRepository vacancyRepository;
  private final VacancySkillRepository vacancySkillRepository;
  private final SkillRepository skillRepository;
  private final UserSkillRepository userSkillRepository;
  private final ProfileRepository profileRepository;
  private final RecommendationRepository recommendationRepository;
  private final SavedVacancyRepository savedVacancyRepository;
  private final UserVacancyRepository userVacancyRepository;
  private final AiTrajectoryPlanner aiTrajectoryPlanner;
  private final ObjectMapper objectMapper;
  private final Map<String, VacancyDtos.ImportStatus> importJobs = new ConcurrentHashMap<>();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Transactional(readOnly = true)
  public Page<VacancyDtos.VacancyListItem> list(
      User user,
      int page,
      int size,
      String sort,
      Integer minScore,
      Integer salaryMin,
      Integer salaryMax,
      Boolean remote,
      Integer areaId,
      String employer) {
    PersonalizedFilters filters =
        personalizedFilters(user, salaryMin, salaryMax, remote, areaId, employer);
    List<VacancyDtos.VacancyListItem> items =
        userVacancyRepository.findByUserIdOrderByImportedAtDesc(user.getId()).stream()
            .map(UserVacancy::getVacancy)
            .filter(
                vacancy ->
                    matchesFilters(
                        vacancy,
                        filters.salaryMin(),
                        filters.salaryMax(),
                        filters.remote(),
                        filters.areaId(),
                        filters.employer()))
            .map(v -> toListItem(user, v))
            .filter(v -> v.score() >= (minScore == null ? 0 : minScore))
            .sorted(listComparator(sort))
            .toList();
    int from = Math.min(Math.max(page, 0) * size, items.size());
    int to = Math.min(from + size, items.size());
    Pageable pageable = PageRequest.of(page, size);
    return new PageImpl<>(items.subList(from, to), pageable, items.size());
  }

  @Transactional(readOnly = true)
  public VacancyDtos.VacancyDetail detail(User user, Long id) {
    Vacancy vacancy =
        vacancyRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
    if (!userVacancyRepository.existsByUserIdAndVacancyId(user.getId(), id)) {
      throw new ResourceNotFoundException("Вакансия не импортирована для текущего пользователя");
    }
    Score score = score(user, vacancy);
    return new VacancyDtos.VacancyDetail(
        vacancy.getId(),
        vacancy.getHhId(),
        vacancy.getTitle(),
        vacancy.getEmployer(),
        vacancy.getDescription(),
        vacancy.getRequirements(),
        vacancy.getArea(),
        vacancy.getSalaryMin(),
        vacancy.getSalaryMax(),
        vacancy.getRemote(),
        score.total(),
        score.sbert(),
        score.coverage(),
        skillItems(user, vacancy),
        vacancy.getUrl(),
        vacancy.getFetchedAt());
  }

  @Transactional
  public VacancyDtos.ImportAccepted importVacancies(
      User user, String role, Integer areaId, Integer salaryFrom, String experience, int pages) {
    String jobId = UUID.randomUUID().toString();
    int pageCount = Math.min(Math.max(pages, 1), 20);
    importJobs.put(jobId, new VacancyDtos.ImportStatus(jobId, "RUNNING", 0, 0));
    int imported = 0;
    int errors = 0;
    String query = importQuery(user, role);
    try {
      for (int page = 0; page < pageCount; page++) {
        JsonNode root = fetchHhPage(query, areaId, salaryFrom, experience, page);
        for (JsonNode item : root.path("items")) {
          try {
            linkVacancy(user, upsertVacancy(item));
            imported++;
          } catch (RuntimeException ex) {
            errors++;
          }
        }
        importJobs.put(jobId, new VacancyDtos.ImportStatus(jobId, "RUNNING", imported, errors));
        if (page + 1 >= root.path("pages").asInt(pageCount)) {
          break;
        }
      }
      importJobs.put(jobId, new VacancyDtos.ImportStatus(jobId, "COMPLETED", imported, errors));
      return new VacancyDtos.ImportAccepted(jobId, "COMPLETED", imported);
    } catch (IOException ex) {
      importJobs.put(
          jobId,
          new VacancyDtos.ImportStatus(
              jobId,
              "FAILED",
              imported,
              errors + 1,
              "HH отклонил запрос: "
                  + ex.getMessage()
                  + ". Семантический анализ запускается отдельно после успешного импорта."));
      return new VacancyDtos.ImportAccepted(jobId, "FAILED", imported);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      importJobs.put(jobId, new VacancyDtos.ImportStatus(jobId, "FAILED", imported, errors + 1));
      return new VacancyDtos.ImportAccepted(jobId, "FAILED", imported);
    }
  }

  public VacancyDtos.ImportStatus importStatus(String jobId) {
    return importJobs.getOrDefault(jobId, new VacancyDtos.ImportStatus(jobId, "NOT_FOUND", 0, 1));
  }

  public List<VacancyDtos.AreaOption> hhAreas() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create("https://api.hh.ru/areas"))
              .header("Accept", "application/json")
              .header("User-Agent", "GetEmployed/1.0 (student project)")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return fallbackAreas();
      }
      List<VacancyDtos.AreaOption> areas = new ArrayList<>();
      for (JsonNode country : objectMapper.readTree(response.body())) {
        flattenAreas(country, "", areas);
      }
      return areas.stream()
          .sorted(Comparator.comparing(VacancyDtos.AreaOption::name))
          .distinct()
          .toList();
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return fallbackAreas();
    }
  }

  private void flattenAreas(JsonNode node, String parentName, List<VacancyDtos.AreaOption> target) {
    String name = node.path("name").asText("");
    String label = parentName.isBlank() ? name : name + " · " + parentName;
    Integer id = parseInteger(node.path("id").asText(null));
    JsonNode children = node.path("areas");
    if (id != null && !name.isBlank()) {
      target.add(new VacancyDtos.AreaOption(id, label));
    }
    for (JsonNode child : children) {
      flattenAreas(child, name, target);
    }
  }

  private List<VacancyDtos.AreaOption> fallbackAreas() {
    return List.of(
        new VacancyDtos.AreaOption(1, "Москва"),
        new VacancyDtos.AreaOption(88, "Казань"),
        new VacancyDtos.AreaOption(2, "Санкт-Петербург"),
        new VacancyDtos.AreaOption(4, "Новосибирск"),
        new VacancyDtos.AreaOption(3, "Екатеринбург"),
        new VacancyDtos.AreaOption(66, "Нижний Новгород"));
  }

  private JsonNode fetchHhPage(
      String role, Integer areaId, Integer salaryFrom, String experience, int page)
      throws IOException, InterruptedException {
    StringBuilder url =
        new StringBuilder("https://api.hh.ru/vacancies")
            .append("?area=")
            .append(areaId)
            .append("&page=")
            .append(page)
            .append("&per_page=20");
    if (role != null && !role.isBlank()) {
      url.append("&text=").append(URLEncoder.encode(role, StandardCharsets.UTF_8));
    }
    if (salaryFrom != null) {
      url.append("&salary=").append(salaryFrom);
    }
    if (experience != null && !experience.isBlank()) {
      url.append("&experience=").append(URLEncoder.encode(experience, StandardCharsets.UTF_8));
    }

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url.toString()))
            .header("Accept", "application/json")
            .header("User-Agent", "GetEmployed/1.0 (student project)")
            .header("HH-User-Agent", "GetEmployed/1.0 (getemployedemailsender@mail.ru)")
            .GET()
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("HH API returned " + response.statusCode());
    }
    return objectMapper.readTree(response.body());
  }

  private void attachSkillNames(Vacancy vacancy, Set<String> skillNames) {
    for (String skillName : skillNames) {
      Skill skill =
          skillRepository
              .findByNameIgnoreCase(skillName)
              .orElseGet(() -> createMarketSkill(skillName));
      if (!vacancySkillRepository.existsByVacancyIdAndSkillId(vacancy.getId(), skill.getId())) {
        VacancySkill vacancySkill = new VacancySkill();
        vacancySkill.setVacancy(vacancy);
        vacancySkill.setSkill(skill);
        vacancySkill.setImportance(isMustHave(vacancy, skillName) ? "MUST_HAVE" : "NICE_TO_HAVE");
        vacancySkillRepository.save(vacancySkill);
      }
    }
  }

  private int importFromHhHtml(
      User user,
      String role,
      Integer areaId,
      Integer salaryFrom,
      String experience,
      int pageCount,
      String jobId)
      throws IOException, InterruptedException {
    int imported = 0;
    for (int page = 0; page < pageCount; page++) {
      String html = fetchHhHtmlPage(role, areaId, salaryFrom, experience, page);
      int importedOnPage = 0;
      Pattern pattern =
          Pattern.compile(
              "data-qa=\"serp-item__title\"[^>]*href=\"([^\"]+)\"[^>]*>.*?"
                  + "data-qa=\"serp-item__title-text\"[^>]*>(.*?)</span>",
              Pattern.DOTALL);
      Matcher matcher = pattern.matcher(html);
      while (matcher.find()) {
        String url = unescapeHtml(matcher.group(1));
        String hhId = extractHhId(url);
        String title = stripHtml(unescapeHtml(matcher.group(2)));
        if (hhId == null || title == null || title.isBlank()) {
          continue;
        }
        Vacancy vacancy = vacancyRepository.findByHhId(hhId).orElseGet(Vacancy::new);
        vacancy.setHhId(hhId);
        vacancy.setTitle(title);
        vacancy.setEmployer("HH.ru");
        vacancy.setAreaId(areaId);
        vacancy.setRequirements(role);
        vacancy.setSalaryMin(salaryFrom);
        vacancy.setRemote(false);
        vacancy.setUrl(url);
        Vacancy saved = vacancyRepository.save(vacancy);
        attachExtractedSkills(saved);
        linkVacancy(user, saved);
        imported++;
        importedOnPage++;
      }
      importJobs.put(jobId, new VacancyDtos.ImportStatus(jobId, "RUNNING", imported, 0));
      if (importedOnPage == 0) {
        break;
      }
    }
    return imported;
  }

  private String fetchHhHtmlPage(
      String role, Integer areaId, Integer salaryFrom, String experience, int page)
      throws IOException, InterruptedException {
    StringBuilder url =
        new StringBuilder("https://hh.ru/search/vacancy")
            .append("?area=")
            .append(areaId)
            .append("&page=")
            .append(page);
    if (role != null && !role.isBlank()) {
      url.append("&text=").append(URLEncoder.encode(role, StandardCharsets.UTF_8));
    }
    if (salaryFrom != null) {
      url.append("&salary=").append(salaryFrom);
    }
    if (experience != null && !experience.isBlank()) {
      url.append("&experience=").append(URLEncoder.encode(experience, StandardCharsets.UTF_8));
    }

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url.toString()))
            .header("Accept", "text/html")
            .header(
                "User-Agent",
                "Mozilla/5.0 AppleWebKit/537.36 Chrome/120.0 Safari/537.36 GetEmployed/1.0")
            .GET()
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("HH HTML returned " + response.statusCode());
    }
    return response.body();
  }

  private Vacancy upsertVacancy(JsonNode item) {
    String hhId = item.path("id").asText();
    Vacancy vacancy = vacancyRepository.findByHhId(hhId).orElseGet(Vacancy::new);
    vacancy.setHhId(hhId);
    vacancy.setTitle(item.path("name").asText("Без названия"));
    vacancy.setEmployer(item.path("employer").path("name").asText(null));
    vacancy.setArea(item.path("area").path("name").asText(null));
    vacancy.setAreaId(parseInteger(item.path("area").path("id").asText(null)));
    vacancy.setSalaryMin(nullableInt(item.path("salary").path("from")));
    vacancy.setSalaryMax(nullableInt(item.path("salary").path("to")));
    vacancy.setCurrency(item.path("salary").path("currency").asText(null));
    vacancy.setRemote(isRemote(item));
    vacancy.setExperience(item.path("experience").path("id").asText(null));
    vacancy.setUrl(item.path("alternate_url").asText(null));
    vacancy.setDescription(stripHtml(item.path("snippet").path("responsibility").asText("")));
    vacancy.setRequirements(stripHtml(item.path("snippet").path("requirement").asText("")));
    Vacancy saved = vacancyRepository.save(vacancy);
    attachFastKeywords(saved);
    return saved;
  }

  private void linkVacancy(User user, Vacancy vacancy) {
    if (userVacancyRepository.existsByUserIdAndVacancyId(user.getId(), vacancy.getId())) {
      return;
    }
    UserVacancy userVacancy = new UserVacancy();
    userVacancy.setUser(user);
    userVacancy.setVacancy(vacancy);
    userVacancyRepository.save(userVacancy);
  }

  private void attachExtractedSkills(Vacancy vacancy) {
    attachExtractedSkills(vacancy, null);
  }

  private void attachFastKeywords(Vacancy vacancy) {
    Set<String> keywords = new LinkedHashSet<>();
    keywords.addAll(splitKeywordText(vacancy.getTitle()));
    keywords.addAll(splitKeywordText(vacancy.getRequirements()));
    keywords.addAll(splitKeywordText(vacancy.getDescription()));
    if (keywords.isEmpty()) {
      keywords.add("Понимание роли: " + safe(vacancy.getTitle()));
    }
    attachSkillNames(vacancy, keywords.stream().limit(8).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll));
  }

  private Set<String> splitKeywordText(String value) {
    Set<String> result = new LinkedHashSet<>();
    String text = safe(value).replaceAll("<[^>]+>", " ").replaceAll("[\\r\\n]+", " ");
    for (String part : text.split("[.;•,/:()\\-]")) {
      String cleaned = part.replaceAll("\\s+", " ").trim();
      if (cleaned.length() >= 4 && cleaned.length() <= 60) {
        result.add(cleaned);
      }
      if (result.size() >= 4) {
        break;
      }
    }
    return result;
  }

  private void attachExtractedSkills(Vacancy vacancy, JsonNode hhDetail) {
    for (String skillName : extractSkills(vacancy, hhDetail)) {
      Skill skill =
          skillRepository
              .findByNameIgnoreCase(skillName)
              .orElseGet(() -> createMarketSkill(skillName));
      if (!vacancySkillRepository.existsByVacancyIdAndSkillId(vacancy.getId(), skill.getId())) {
        VacancySkill vacancySkill = new VacancySkill();
        vacancySkill.setVacancy(vacancy);
        vacancySkill.setSkill(skill);
        vacancySkill.setImportance(isMustHave(vacancy, skillName) ? "MUST_HAVE" : "NICE_TO_HAVE");
        vacancySkillRepository.save(vacancySkill);
      }
    }
  }

  private Skill createMarketSkill(String name) {
    Skill skill = new Skill();
    skill.setName(name);
    skill.setCategory("MARKET");
    skill.setPopularity(0.0);
    return skillRepository.save(skill);
  }

  private Set<String> extractSkills(Vacancy vacancy, JsonNode hhDetail) {
    Set<String> found = new LinkedHashSet<>();
    if (hhDetail != null) {
      for (JsonNode skill : hhDetail.path("key_skills")) {
        String name = skill.path("name").asText("").trim();
        if (!name.isBlank()) {
          found.add(name);
        }
      }
    }
    if (!found.isEmpty()) {
      return found;
    }
    found.addAll(aiTrajectoryPlanner.extractVacancyRequirements(vacancy));
    if (!found.isEmpty()) {
      return found;
    }
    if (found.isEmpty()) {
      found.addAll(inferRequirementsFromText(vacancy));
    }
    return found;
  }

  private boolean isMustHave(Vacancy vacancy, String skillName) {
    String requirements = safe(vacancy.getRequirements()).toLowerCase(Locale.ROOT);
    return requirements.contains(skillName.toLowerCase(Locale.ROOT));
  }

  private Set<String> inferRequirementsFromText(Vacancy vacancy) {
    String text =
        String.join(
                " ",
                safe(vacancy.getTitle()),
                safe(vacancy.getRequirements()),
                safe(vacancy.getDescription()))
            .replaceAll("[\\r\\n]+", " ");
    Set<String> result = new LinkedHashSet<>();
    for (String marker :
        List.of("требования", "обязанности", "навыки", "опыт", "знание", "умение")) {
      int idx = text.toLowerCase(Locale.ROOT).indexOf(marker);
      if (idx < 0) {
        continue;
      }
      String fragment = text.substring(idx, Math.min(text.length(), idx + 500));
      for (String part : fragment.split("[.;•,]")) {
        String cleaned = part.replaceAll("\\s+", " ").trim();
        if (cleaned.length() >= 4 && cleaned.length() <= 70) {
          result.add(cleaned);
        }
        if (result.size() >= 6) {
          return result;
        }
      }
    }
    String title = safe(vacancy.getTitle()).trim();
    if (!title.isBlank()) {
      result.add("Понимание роли: " + title);
    }
    result.add("Профессиональная коммуникация");
    result.add("Рабочие инструменты профессии");
    return result.stream()
        .limit(6)
        .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
  }

  private String importQuery(User user, String role) {
    if (role != null && !role.isBlank()) {
      return compactRoleQuery(role);
    }
    return profileRepository
        .findByUserId(user.getId())
        .map(profile -> compactRoleQuery(profile.getGoal()))
        .orElse("");
  }

  private String compactRoleQuery(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String cleaned =
        value
            .replaceAll("(?i)Желаемая роль:", "")
            .replaceAll("(?i)Интересы:.*", "")
            .replaceAll("(?i)Сильные стороны:.*", "")
            .replaceAll("\\s+", " ")
            .trim();
    int dot = cleaned.indexOf('.');
    if (dot > 0) {
      cleaned = cleaned.substring(0, dot).trim();
    }
    return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
  }

  private boolean isRemote(JsonNode item) {
    if ("remote".equals(item.path("schedule").path("id").asText())) {
      return true;
    }
    for (JsonNode format : item.path("work_format")) {
      if ("REMOTE".equalsIgnoreCase(format.path("id").asText())
          || "Удаленно".equalsIgnoreCase(format.path("name").asText())) {
        return true;
      }
    }
    return false;
  }

  private Integer nullableInt(JsonNode node) {
    return node.isMissingNode() || node.isNull() ? null : node.asInt();
  }

  private Integer parseInteger(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String stripHtml(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return unescapeHtml(value.replaceAll("<[^>]+>", "")).trim();
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String extractHhId(String url) {
    Matcher matcher = Pattern.compile("/vacancy/(\\d+)").matcher(url);
    return matcher.find() ? matcher.group(1) : null;
  }

  private String unescapeHtml(String value) {
    return value
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'");
  }

  @Transactional
  public VacancyDtos.SavedAt save(User user, Long vacancyId) {
    Vacancy vacancy =
        vacancyRepository
            .findById(vacancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
    if (!userVacancyRepository.existsByUserIdAndVacancyId(user.getId(), vacancyId)) {
      throw new ResourceNotFoundException("Вакансия не импортирована для текущего пользователя");
    }
    if (!userVacancyRepository.existsByUserIdAndVacancyId(user.getId(), vacancyId)) {
      throw new ResourceNotFoundException("Вакансия не импортирована для текущего пользователя");
    }
    if (savedVacancyRepository.existsByUserIdAndVacancyId(user.getId(), vacancyId)) {
      throw new ResourceAlreadyExistsException("Вакансия уже сохранена");
    }
    SavedVacancy saved = new SavedVacancy();
    saved.setUser(user);
    saved.setVacancy(vacancy);
    return new VacancyDtos.SavedAt(savedVacancyRepository.save(saved).getSavedAt());
  }

  @Transactional
  public void unsave(User user, Long vacancyId) {
    SavedVacancy saved =
        savedVacancyRepository
            .findByUserIdAndVacancyId(user.getId(), vacancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Сохраненная вакансия не найдена"));
    savedVacancyRepository.delete(saved);
  }

  @Transactional(readOnly = true)
  public Page<VacancyDtos.VacancyListItem> saved(User user, int page, int size) {
    return savedVacancyRepository
        .findByUserId(user.getId(), PageRequest.of(page, size))
        .map(s -> toListItem(user, s.getVacancy()));
  }

  @Transactional(readOnly = true)
  public Page<VacancyDtos.RecommendationItem> recommendations(
      User user, int page, int limit, String sortBy, int minScore) {
    Sort sort =
        "salary".equals(sortBy)
            ? Sort.by("vacancy.salaryMax").descending()
            : Sort.by("score").descending();
    return recommendationRepository
        .findByUserIdAndScoreGreaterThanEqual(
            user.getId(), (short) minScore, PageRequest.of(page, limit, sort))
        .map(this::toRecommendationItem);
  }

  @Transactional(readOnly = true)
  public List<VacancyDtos.RecommendationItem> topRecommendations(User user, int limit) {
    return recommendationRepository.findTop5ByUserIdOrderByScoreDesc(user.getId()).stream()
        .limit(limit)
        .map(this::toRecommendationItem)
        .toList();
  }

  @Transactional(readOnly = true)
  public VacancyDtos.RecommendationDetail recommendationDetail(User user, Long vacancyId) {
    Recommendation recommendation =
        recommendationRepository
            .findByUserIdAndVacancyId(user.getId(), vacancyId)
            .orElseGet(() -> buildTransientRecommendation(user, vacancyId));
    Vacancy vacancy = recommendation.getVacancy();
    List<VacancyDtos.VacancySkillItem> skills = skillItems(user, vacancy);
    List<String> covered =
        skills.stream()
            .filter(VacancyDtos.VacancySkillItem::covered)
            .map(VacancyDtos.VacancySkillItem::name)
            .toList();
    List<VacancyDtos.DetailedGap> gaps =
        skills.stream()
            .filter(s -> !s.covered())
            .map(
                s ->
                    new VacancyDtos.DetailedGap(
                        s.skillId(), s.name(), s.importance(), s.userLevel(), s.requiredLevel(), 0))
            .toList();
    String formula =
        "0.7 * "
            + recommendation.getSbertScore()
            + " + 0.3 * "
            + recommendation.getSkillsCoverage()
            + " = "
            + recommendation.getScore();
    return new VacancyDtos.RecommendationDetail(
        vacancyId,
        recommendation.getScore(),
        recommendation.getSbertScore(),
        recommendation.getSkillsCoverage(),
        new VacancyDtos.MatchBreakdown(0.7, 0.3, formula),
        covered,
        gaps);
  }

  @Transactional(readOnly = true)
  public List<ClusterDtos.ClusterItem> fetchTopSkillsFromHhClusters(String role, Integer areaId) {
    try {
      // Строим URL с параметром clusters=true
      StringBuilder url =
          new StringBuilder("https://api.hh.ru/vacancies")
              .append("?text=")
              .append(URLEncoder.encode(role, StandardCharsets.UTF_8))
              .append("&clusters=true")
              .append("&per_page=0"); // Нам не нужны сами вакансии, только аналитика!

      if (areaId != null) {
        url.append("&area=").append(areaId);
      }

      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url.toString()))
              .header("Accept", "application/json")
              .header("User-Agent", "GetEmployed/1.0 (student project)")
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IOException("HH API returned status " + response.statusCode());
      }

      // Читаем дерево JSON
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode clustersNode = root.path("clusters");

      // Ищем среди кластеров тот, у которого id == "skills"
      for (JsonNode clusterNode : clustersNode) {
        if ("skills".equals(clusterNode.path("id").asText())) {
          List<ClusterDtos.ClusterItem> items = new ArrayList<>();
          for (JsonNode itemNode : clusterNode.path("items")) {
            items.add(
                new ClusterDtos.ClusterItem(
                    itemNode.path("name").asText(),
                    itemNode.path("url").asText(),
                    itemNode.path("count").asInt()));
          }
          return items; // Возвращаем топ навыков от самого HH
        }
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      // В случае ошибки возвращаем пустой список или логируем
      return List.of();
    }
    return List.of();
  }

  @Transactional(readOnly = true)
  public List<VacancyDtos.TopSkill> topSkills(int limit) {
    Map<Long, Counter> counters = new HashMap<>();
    vacancySkillRepository
        .findAll()
        .forEach(
            vs ->
                counters.computeIfAbsent(vs.getSkill().getId(), id -> new Counter(vs.getSkill()))
                    .count++);
    long vacancyCount = Math.max(1, vacancyRepository.count());
    return counters.values().stream()
        .sorted(Comparator.comparingLong(Counter::count).reversed())
        .limit(limit)
        .map(
            c ->
                new VacancyDtos.TopSkill(
                    c.skill.getId(), c.skill.getName(), (double) c.count / vacancyCount, c.count))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<VacancyDtos.SkillGap> skillGaps(User user, double minFrequency) {
    Map<Long, Short> userLevels = userLevels(user);
    List<Vacancy> userVacancies =
        userVacancyRepository.findByUserIdOrderByImportedAtDesc(user.getId()).stream()
            .map(UserVacancy::getVacancy)
            .toList();
    long vacancyCount = Math.max(1, userVacancies.size());
    return userVacancies.stream()
        .flatMap(v -> vacancySkillRepository.findByVacancyId(v.getId()).stream())
        .filter(vs -> userLevels.getOrDefault(vs.getSkill().getId(), (short) 0) < 3)
        .collect(
            HashMap<Long, Counter>::new,
            (m, vs) ->
                m.computeIfAbsent(vs.getSkill().getId(), id -> new Counter(vs.getSkill())).count++,
            Map::putAll)
        .values()
        .stream()
        .map(
            c ->
                new VacancyDtos.SkillGap(
                    c.skill.getId(),
                    c.skill.getName(),
                    "HIGH",
                    userLevels.getOrDefault(c.skill.getId(), (short) 0),
                    3,
                    (double) c.count / vacancyCount,
                    c.count,
                    (int) Math.round((double) c.count * 100 / vacancyCount)))
        .filter(g -> g.frequency() >= minFrequency)
        .toList();
  }

  @Transactional
  public VacancyDtos.RebuildAccepted rebuild(User user) {
    recommendationRepository.deleteByUserId(user.getId());
    List<Vacancy> vacancies =
        userVacancyRepository.findByUserIdOrderByImportedAtDesc(user.getId()).stream()
            .map(UserVacancy::getVacancy)
            .toList();
    for (Vacancy vacancy : vacancies) {
      if (vacancySkillRepository.findByVacancyId(vacancy.getId()).isEmpty()) {
        attachExtractedSkills(vacancy);
      }
      Score score = score(user, vacancy);
      Recommendation recommendation =
          recommendationRepository
              .findByUserIdAndVacancyId(user.getId(), vacancy.getId())
              .orElseGet(
                  () -> {
                    Recommendation r = new Recommendation();
                    r.setUser(user);
                    r.setVacancy(vacancy);
                    return r;
                  });
      recommendation.setScore((short) score.total());
      recommendation.setSbertScore((short) score.sbert());
      recommendation.setSkillsCoverage((short) score.coverage());
      recommendationRepository.save(recommendation);
    }
    return new VacancyDtos.RebuildAccepted(
        UUID.randomUUID().toString(), "ACCEPTED", vacancies.size());
  }

  public PageEnvelope.Pagination pagination(Page<?> page) {
    return new PageEnvelope.Pagination(
        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public long userVacancyCount(User user) {
    return userVacancyRepository.countByUserId(user.getId());
  }

  @Transactional(readOnly = true)
  public List<VacancyDtos.TopSkill> topSkills(User user, int limit) {
    Map<Long, Counter> counters = new HashMap<>();
    List<UserVacancy> userVacancies =
        userVacancyRepository.findByUserIdOrderByImportedAtDesc(user.getId());
    userVacancies.forEach(
        uv ->
            vacancySkillRepository
                .findByVacancyId(uv.getVacancy().getId())
                .forEach(
                    vs ->
                        counters.computeIfAbsent(
                                vs.getSkill().getId(), id -> new Counter(vs.getSkill()))
                            .count++));
    long vacancyCount = Math.max(1, userVacancies.size());
    return counters.values().stream()
        .sorted(Comparator.comparingLong(Counter::count).reversed())
        .limit(limit)
        .map(
            c ->
                new VacancyDtos.TopSkill(
                    c.skill.getId(), c.skill.getName(), (double) c.count / vacancyCount, c.count))
        .toList();
  }

  private PersonalizedFilters personalizedFilters(
      User user,
      Integer salaryMin,
      Integer salaryMax,
      Boolean remote,
      Integer areaId,
      String employer) {
    return profileRepository
        .findByUserId(user.getId())
        .map(
            profile ->
                new PersonalizedFilters(
                    salaryMin == null ? profile.getSalaryMin() : salaryMin,
                    salaryMax == null ? profile.getSalaryMax() : salaryMax,
                    remote == null ? activeRemoteFilter(profile.getRemote()) : remote,
                    areaId == null ? profile.getAreaId() : areaId,
                    employer))
        .orElse(new PersonalizedFilters(salaryMin, salaryMax, remote, areaId, employer));
  }

  private Boolean activeRemoteFilter(Boolean profileRemote) {
    return Boolean.TRUE.equals(profileRemote) ? Boolean.TRUE : null;
  }

  private Comparator<VacancyDtos.VacancyListItem> listComparator(String sort) {
    String normalized = sort == null || sort.isBlank() ? "score,desc" : sort;
    boolean asc = normalized.toLowerCase().endsWith(",asc");
    Comparator<VacancyDtos.VacancyListItem> comparator;
    if (normalized.startsWith("salary")) {
      comparator =
          Comparator.comparing(
              v ->
                  v.salaryMax() == null
                      ? v.salaryMin() == null ? 0 : v.salaryMin()
                      : v.salaryMax());
    } else if (normalized.startsWith("fetchedAt")) {
      comparator =
          Comparator.comparing(
              VacancyDtos.VacancyListItem::fetchedAt,
              Comparator.nullsLast(Comparator.naturalOrder()));
    } else {
      comparator = Comparator.comparingInt(VacancyDtos.VacancyListItem::score);
    }
    comparator = asc ? comparator : comparator.reversed();
    return comparator.thenComparing(
        VacancyDtos.VacancyListItem::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }

  public VacancyDtos.VacancyListItem toListItem(User user, Vacancy vacancy) {
    Score score = score(user, vacancy);
    return new VacancyDtos.VacancyListItem(
        vacancy.getId(),
        vacancy.getHhId(),
        vacancy.getTitle(),
        vacancy.getEmployer(),
        vacancy.getArea(),
        vacancy.getSalaryMin(),
        vacancy.getSalaryMax(),
        vacancy.getRemote(),
        score.total(),
        gaps(user, vacancy),
        vacancy.getFetchedAt());
  }

  private record PersonalizedFilters(
      Integer salaryMin, Integer salaryMax, Boolean remote, Integer areaId, String employer) {}

  private VacancyDtos.RecommendationItem toRecommendationItem(Recommendation r) {
    Vacancy v = r.getVacancy();
    return new VacancyDtos.RecommendationItem(
        v.getId(),
        r.getScore(),
        r.getSbertScore(),
        r.getSkillsCoverage(),
        gaps(r.getUser(), v).stream().map(g -> new VacancyDtos.Gap(g, "HIGH")).toList(),
        new VacancyDtos.VacancySummary(
            v.getTitle(), v.getEmployer(), v.getSalaryMin(), v.getSalaryMax()),
        r.getUpdatedAt());
  }

  private Recommendation buildTransientRecommendation(User user, Long vacancyId) {
    Vacancy vacancy =
        vacancyRepository
            .findById(vacancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
    Score score = score(user, vacancy);
    Recommendation recommendation = new Recommendation();
    recommendation.setUser(user);
    recommendation.setVacancy(vacancy);
    recommendation.setScore((short) score.total());
    recommendation.setSbertScore((short) score.sbert());
    recommendation.setSkillsCoverage((short) score.coverage());
    return recommendation;
  }

  private List<VacancyDtos.VacancySkillItem> skillItems(User user, Vacancy vacancy) {
    Map<Long, Short> userLevels = userLevels(user);
    return vacancySkillRepository.findByVacancyId(vacancy.getId()).stream()
        .map(
            vs -> {
              short userLevel = userLevels.getOrDefault(vs.getSkill().getId(), (short) 0);
              return new VacancyDtos.VacancySkillItem(
                  vs.getSkill().getId(),
                  vs.getSkill().getName(),
                  vs.getImportance(),
                  3,
                  userLevel,
                  userLevel >= 3);
            })
        .toList();
  }

  private List<String> gaps(User user, Vacancy vacancy) {
    return skillItems(user, vacancy).stream()
        .filter(s -> !s.covered())
        .map(VacancyDtos.VacancySkillItem::name)
        .toList();
  }

  private Score score(User user, Vacancy vacancy) {
    List<VacancySkill> skills = vacancySkillRepository.findByVacancyId(vacancy.getId());
    if (skills.isEmpty()) {
      return new Score(0, 0, 0);
    }
    Map<Long, Short> levels = userLevels(user);
    double totalWeight = 0.0;
    double matchedWeight = 0.0;
    for (VacancySkill skill : skills) {
      double weight = "MUST_HAVE".equals(skill.getImportance()) ? 2.0 : 1.0;
      short userLevel = levels.getOrDefault(skill.getSkill().getId(), (short) 0);
      double skillRatio = Math.min(userLevel, (short) 3) / 3.0;
      totalWeight += weight;
      matchedWeight += weight * skillRatio;
    }
    int coverage = totalWeight == 0.0 ? 0 : (int) Math.round(matchedWeight * 100 / totalWeight);
    int semantic = semanticScore(user, vacancy);
    int total = (int) Math.round(semantic * 0.6 + coverage * 0.4);
    return new Score(total, semantic, coverage);
  }

  private int semanticScore(User user, Vacancy vacancy) {
    return aiTrajectoryPlanner
        .matchVacancy(
            profileContext(user), userSkillNames(user), vacancy, vacancySkillNames(vacancy))
        .orElseGet(() -> fallbackSemanticScore(user, vacancy));
  }

  private String profileContext(User user) {
    return profileRepository
        .findByUserId(user.getId())
        .map(
            profile ->
                String.join(
                    ". ",
                    safe(profile.getGoal()),
                    safe(profile.getLevel()),
                    safe(profile.getCity()),
                    Boolean.TRUE.equals(profile.getRemote()) ? "нужна удаленная работа" : ""))
        .orElse("");
  }

  private List<String> userSkillNames(User user) {
    return userSkillRepository.findByUserId(user.getId()).stream()
        .map(us -> us.getSkill().getName() + " " + us.getLevel() + "/5")
        .toList();
  }

  private List<String> vacancySkillNames(Vacancy vacancy) {
    return vacancySkillRepository.findByVacancyId(vacancy.getId()).stream()
        .map(vs -> vs.getSkill().getName())
        .toList();
  }

  private int fallbackSemanticScore(User user, Vacancy vacancy) {
    String profile = profileContext(user).toLowerCase(Locale.ROOT);
    String text =
        String.join(
                " ",
                safe(vacancy.getTitle()),
                safe(vacancy.getEmployer()),
                safe(vacancy.getDescription()))
            .toLowerCase(Locale.ROOT);
    int score = 35;
    for (String token : profile.split("[^a-zа-я0-9+#.]+")) {
      if (token.length() >= 4 && text.contains(token)) {
        score += 8;
      }
    }
    return Math.min(100, score);
  }

  private Map<Long, Short> userLevels(User user) {
    Map<Long, Short> levels = new HashMap<>();
    for (UserSkill userSkill : userSkillRepository.findByUserId(user.getId())) {
      levels.put(userSkill.getSkill().getId(), userSkill.getLevel());
    }
    return levels;
  }

  private Specification<Vacancy> filter(
      Integer salaryMin, Integer salaryMax, Boolean remote, Integer areaId, String employer) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (salaryMin != null) {
        predicates.add(
            cb.or(
                cb.isNull(root.get("salaryMax")),
                cb.greaterThanOrEqualTo(root.get("salaryMax"), salaryMin)));
      }
      if (salaryMax != null) {
        predicates.add(
            cb.or(
                cb.isNull(root.get("salaryMin")),
                cb.lessThanOrEqualTo(root.get("salaryMin"), salaryMax)));
      }
      if (remote != null) {
        predicates.add(cb.equal(root.get("remote"), remote));
      }
      if (areaId != null) {
        predicates.add(cb.equal(root.get("areaId"), areaId));
      }
      if (employer != null && !employer.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("employer")), "%" + employer.toLowerCase() + "%"));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private boolean matchesFilters(
      Vacancy vacancy,
      Integer salaryMin,
      Integer salaryMax,
      Boolean remote,
      Integer areaId,
      String employer) {
    if (salaryMin != null && vacancy.getSalaryMax() != null && vacancy.getSalaryMax() < salaryMin) {
      return false;
    }
    if (salaryMax != null && vacancy.getSalaryMin() != null && vacancy.getSalaryMin() > salaryMax) {
      return false;
    }
    if (remote != null && !remote.equals(vacancy.getRemote())) {
      return false;
    }
    if (areaId != null && !areaId.equals(vacancy.getAreaId())) {
      return false;
    }
    return employer == null
        || employer.isBlank()
        || safe(vacancy.getEmployer())
            .toLowerCase(Locale.ROOT)
            .contains(employer.toLowerCase(Locale.ROOT));
  }

  private Sort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return Sort.by("fetchedAt").descending();
    }
    String[] parts = sort.split(",");
    String property = "salary_max".equals(parts[0]) ? "salaryMax" : parts[0];
    Sort.Direction direction =
        parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    if ("score".equals(property)) {
      property = "fetchedAt";
    }
    return Sort.by(direction, property);
  }

  private record Score(int total, int sbert, int coverage) {}

  private static final class Counter {
    private final Skill skill;
    private long count;

    private Counter(Skill skill) {
      this.skill = skill;
    }

    private long count() {
      return count;
    }
  }
}
