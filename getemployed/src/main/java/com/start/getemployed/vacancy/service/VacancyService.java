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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
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

  @Value("${superjob.api-key:}")
  private String superjobApiKey;

  @Value("${hh.client-id:}")
  private String hhClientId;

  @Value("${hh.client-secret:}")
  private String hhClientSecret;

  private volatile String hhAccessToken;
  private volatile long hhTokenExpiresAt;

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

  public VacancyDtos.ImportAccepted importVacancies(
      User user, String role, Integer areaId, Integer salaryFrom, String experience, int pages) {
    String jobId = UUID.randomUUID().toString();
    int pageCount = Math.min(Math.max(pages, 1), 20);
    importJobs.put(jobId, new VacancyDtos.ImportStatus(jobId, "RUNNING", 0, 0));
    int imported = 0;
    int errors = 0;
    String query = importQuery(user, role);

    // Try HH.ru first
    boolean hhSuccess = false;
    try {
      for (int page = 0; page < pageCount; page++) {
        JsonNode root = fetchHhPage(query, areaId, salaryFrom, experience, page);
        JsonNode items = root.path("items");
        if (!items.isArray() || (items.isEmpty() && page == 0)) {
          break;
        }
        for (JsonNode item : items) {
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
      hhSuccess = imported > 0;
    } catch (IOException ex) {
      log.warn("HH.ru import failed: {}", ex.getMessage());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      importJobs.put(
          jobId,
          new VacancyDtos.ImportStatus(jobId, "FAILED", imported, errors + 1, "Импорт прерван"));
      return new VacancyDtos.ImportAccepted(jobId, "FAILED", imported);
    }

    // Fallback 1: SuperJob (proper keyword search, requires API key)
    if (!hhSuccess && superjobApiKey != null && !superjobApiKey.isBlank()) {
      importJobs.put(
          jobId,
          new VacancyDtos.ImportStatus(
              jobId, "RUNNING", imported, errors, "HH.ru недоступен, пробую SuperJob.ru..."));
      try {
        imported +=
            importFromSuperjob(user, query, areaId, salaryFrom, pageCount, jobId, imported, errors);
        hhSuccess = imported > 0;
      } catch (IOException ex) {
        log.warn("SuperJob import failed: {}", ex.getMessage());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        importJobs.put(
            jobId,
            new VacancyDtos.ImportStatus(jobId, "FAILED", imported, errors + 1, "Импорт прерван"));
        return new VacancyDtos.ImportAccepted(jobId, "FAILED", imported);
      }
    }

    if (!hhSuccess) {
      String keyHint =
          (superjobApiKey == null || superjobApiKey.isBlank())
              ? " Добавьте SUPERJOB_API_KEY для лучших результатов."
              : "";
      importJobs.put(
          jobId,
          new VacancyDtos.ImportStatus(
              jobId,
              "FAILED",
              imported,
              errors + 1,
              "Не удалось получить вакансии с HH.ru и SuperJob.ru." + keyHint));
      return new VacancyDtos.ImportAccepted(jobId, "FAILED", imported);
    }

    importJobs.put(jobId, new VacancyDtos.ImportStatus(jobId, "COMPLETED", imported, errors));
    return new VacancyDtos.ImportAccepted(jobId, "COMPLETED", imported);
  }

  public VacancyDtos.ImportStatus importStatus(String jobId) {
    return importJobs.getOrDefault(jobId, new VacancyDtos.ImportStatus(jobId, "NOT_FOUND", 0, 1));
  }

  public List<VacancyDtos.AreaOption> hhAreas() {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create("https://api.hh.ru/areas"))
              .header("Accept", "application/json")
              .header("User-Agent", "GetEmployed/1.0 (student project)");
      String token = getHhAccessToken();
      if (token != null) {
        builder.header("Authorization", "Bearer " + token);
      }
      HttpRequest request = builder.GET().build();
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

  // ─── AI Explanation ───────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public VacancyDtos.ExplainResponse explainRecommendation(User user, Long vacancyId) {
    Vacancy vacancy =
        vacancyRepository
            .findById(vacancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
    Score score = score(user, vacancy);
    List<String> userSkills = userSkillNames(user);
    List<String> vacSkills = vacancySkillNames(vacancy);
    String profile = profileContext(user);

    Optional<String> aiExplanation =
        aiTrajectoryPlanner.explainRecommendation(
            profile,
            userSkills,
            vacancy,
            vacSkills,
            score.total(),
            score.sbert(),
            score.coverage());

    String explanation =
        aiExplanation.orElseGet(() -> buildFallbackExplanation(user, vacancy, score));
    return new VacancyDtos.ExplainResponse(explanation, aiExplanation.isPresent());
  }

  private String buildFallbackExplanation(User user, Vacancy vacancy, Score score) {
    List<VacancySkill> skills = vacancySkillRepository.findByVacancyId(vacancy.getId());
    Map<Long, Short> levels = userLevels(user);
    long covered =
        skills.stream()
            .filter(vs -> levels.getOrDefault(vs.getSkill().getId(), (short) 0) >= 3)
            .count();
    String match =
        score.total() >= 70
            ? "Отличный вариант — рекомендуется подать резюме."
            : score.total() >= 50
                ? "Хорошая возможность для профессионального роста."
                : "Потребуется дополнительное обучение по ключевым навыкам.";
    return String.format(
        "Вакансия «%s» соответствует вашему профилю на %d%%: семантическое совпадение %d%%, "
            + "покрытие требуемых навыков %d%% (%d из %d). %s",
        safe(vacancy.getTitle()),
        score.total(),
        score.sbert(),
        score.coverage(),
        covered,
        skills.size(),
        match);
  }

  // ─── SuperJob.ru integration ──────────────────────────────────────────────

  private int importFromSuperjob(
      User user,
      String query,
      Integer hhAreaId,
      Integer salaryFrom,
      int pageCount,
      String jobId,
      int alreadyImported,
      int alreadyErrors)
      throws IOException, InterruptedException {
    int imported = 0;
    int errors = 0;
    Integer townId = hhAreaToSuperjobTown(hhAreaId);
    int count = 20; // SuperJob supports up to 100, 20 per "page" matches HH behaviour

    for (int page = 0; page < pageCount; page++) {
      JsonNode root;
      try {
        root = fetchSuperjobPage(query, townId, salaryFrom, page, count);
      } catch (IOException ex) {
        log.warn("SuperJob page {} fetch failed: {}", page, ex.getMessage());
        break;
      }

      JsonNode objects = root.path("objects");
      if (!objects.isArray() || objects.isEmpty()) {
        break;
      }
      for (JsonNode item : objects) {
        try {
          Vacancy vacancy = upsertSuperjobVacancy(item);
          linkVacancy(user, vacancy);
          imported++;
        } catch (RuntimeException ex) {
          log.debug("SuperJob vacancy upsert error: {}", ex.getMessage());
          errors++;
        }
      }
      importJobs.put(
          jobId,
          new VacancyDtos.ImportStatus(
              jobId,
              "RUNNING",
              alreadyImported + imported,
              alreadyErrors + errors,
              "Источник: SuperJob.ru"));

      boolean more = root.path("more").asBoolean(false);
      int total = root.path("total").asInt(0);
      if (!more || (page + 1) * count >= total) {
        break;
      }
    }
    return imported;
  }

  private JsonNode fetchSuperjobPage(
      String query, Integer townId, Integer salaryFrom, int page, int count)
      throws IOException, InterruptedException {
    StringBuilder url =
        new StringBuilder("https://api.superjob.ru/2.0/vacancies/")
            .append("?count=")
            .append(count)
            .append("&page=")
            .append(page);
    if (query != null && !query.isBlank()) {
      url.append("&keyword=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
    if (townId != null) {
      url.append("&town=").append(townId);
    }
    if (salaryFrom != null && salaryFrom > 0) {
      url.append("&payment_from=").append(salaryFrom).append("&no_agreement=1");
    }

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url.toString()))
            .header("Accept", "application/json")
            .header("User-Agent", "GetEmployed/1.0 (adaikin.andr321@gmail.com)")
            .header("X-Api-App-Id", superjobApiKey)
            .GET()
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new IOException(
          "SuperJob API вернул "
              + response.statusCode()
              + " — проверьте SUPERJOB_API_KEY в настройках");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("SuperJob API returned " + response.statusCode());
    }
    return objectMapper.readTree(response.body());
  }

  private Vacancy upsertSuperjobVacancy(JsonNode item) {
    String rawId = item.path("id").asText("unknown");
    String sjId = "sj_" + rawId;
    Vacancy vacancy = vacancyRepository.findByHhId(sjId).orElseGet(Vacancy::new);
    vacancy.setHhId(sjId);

    // Title
    String title = item.path("profession").asText("").trim();
    if (title.isBlank()) {
      title = "Вакансия SuperJob";
    } else if (title.length() > 255) {
      title = title.substring(0, 255);
    }
    vacancy.setTitle(title);

    // Employer
    String employer = item.path("client").path("title").asText(null);
    vacancy.setEmployer(employer);

    // Location
    String areaName = item.path("town").path("title").asText(null);
    vacancy.setArea(areaName);
    int sjTownId = item.path("town").path("id").asInt(0);
    Integer hhAreaId = SUPERJOB_TOWN_TO_HH_AREA.get(sjTownId);
    if (hhAreaId != null) {
      vacancy.setAreaId(hhAreaId);
    }

    // Salary — SuperJob has payment_from / payment_to; if agreement=true, salary is negotiable
    boolean agreement = item.path("agreement").asBoolean(false);
    if (!agreement) {
      JsonNode payFrom = item.path("payment_from");
      JsonNode payTo = item.path("payment_to");
      if (!payFrom.isMissingNode() && !payFrom.isNull() && payFrom.asInt(0) > 0) {
        vacancy.setSalaryMin(payFrom.asInt());
      }
      if (!payTo.isMissingNode() && !payTo.isNull() && payTo.asInt(0) > 0) {
        vacancy.setSalaryMax(payTo.asInt());
      }
    }
    // SuperJob always uses RUB for Russian vacancies
    vacancy.setCurrency("RUR");

    // Remote work: place_of_work id=2 means remote
    int placeId = item.path("place_of_work").path("id").asInt(0);
    vacancy.setRemote(placeId == 2);

    // Description: prefer vacancyRichText, fall back to work + candidat
    String richText = stripHtml(item.path("vacancyRichText").asText("")).trim();
    String work = item.path("work").asText("").trim();
    String description = richText.isBlank() ? work : richText;
    if (description.length() > 2000) {
      description = description.substring(0, 2000);
    }
    vacancy.setDescription(description.isBlank() ? null : description);

    // Requirements
    String candidat = stripHtml(item.path("candidat").asText("")).trim();
    if (candidat.length() > 1000) {
      candidat = candidat.substring(0, 1000);
    }
    vacancy.setRequirements(candidat.isBlank() ? null : candidat);

    // URL
    String link = item.path("link").asText(null);
    vacancy.setUrl(link);

    Vacancy saved = vacancyRepository.save(vacancy);
    attachSkillsFromKnownList(saved);
    return saved;
  }

  private static Integer hhAreaToSuperjobTown(Integer hhAreaId) {
    if (hhAreaId == null) {
      return null;
    }
    // SuperJob town IDs differ from HH area IDs
    return switch (hhAreaId) {
      case 1 -> 4; // Москва
      case 2 -> 14; // Санкт-Петербург
      case 4 -> 6; // Новосибирск
      case 3 -> 9; // Екатеринбург
      case 88 -> 88; // Казань
      case 66 -> 12; // Нижний Новгород
      case 104 -> 15; // Челябинск
      case 78 -> 78; // Самара
      case 76 -> 16; // Омск
      case 99 -> 10; // Ростов-на-Дону
      case 72 -> 72; // Уфа
      case 68 -> 19; // Красноярск
      case 26 -> 26; // Воронеж
      case 53 -> 17; // Пермь
      case 54 -> 11; // Волгоград
      case 55 -> 55; // Краснодар
      case 75 -> 75; // Саратов
      case 24 -> 24; // Тюмень
      default -> null; // unknown → no town filter, search all Russia
    };
  }

  // ─── HH.ru OAuth ──────────────────────────────────────────────────────────

  private synchronized String getHhAccessToken() throws IOException, InterruptedException {
    if (hhAccessToken != null && System.currentTimeMillis() < hhTokenExpiresAt - 60_000L) {
      return hhAccessToken;
    }
    if (hhClientId == null || hhClientId.isBlank() || hhClientSecret == null || hhClientSecret.isBlank()) {
      return null;
    }
    String body = "grant_type=client_credentials"
        + "&client_id=" + URLEncoder.encode(hhClientId, StandardCharsets.UTF_8)
        + "&client_secret=" + URLEncoder.encode(hhClientSecret, StandardCharsets.UTF_8);
    HttpRequest request = HttpRequest.newBuilder(URI.create("https://hh.ru/oauth/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("User-Agent", "GetEmployed/1.0 (adaikin.andr321@gmail.com)")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      log.warn("HH OAuth token request failed with status {}: {}", response.statusCode(), response.body());
      return null;
    }
    JsonNode json = objectMapper.readTree(response.body());
    String token = json.path("access_token").asText(null);
    if (token == null || token.isBlank()) {
      log.warn("HH OAuth response missing access_token: {}", response.body());
      return null;
    }
    long expiresIn = json.path("expires_in").asLong(1_209_600L);
    hhAccessToken = token;
    hhTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
    log.info("HH.ru access token obtained, expires in {}s", expiresIn);
    return hhAccessToken;
  }

  // ─── HH.ru helpers ────────────────────────────────────────────────────────

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

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url.toString()))
            .header("Accept", "application/json")
            .header("User-Agent", "GetEmployed/1.0 (adaikin.andr321@gmail.com)");
    String token = getHhAccessToken();
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 403 || response.statusCode() == 429) {
      throw new IOException("HH API returned " + response.statusCode() + " (rate limit or ban)");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("HH API returned " + response.statusCode());
    }
    return objectMapper.readTree(response.body());
  }

  // ─── Skill attachment ─────────────────────────────────────────────────────

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

  private static final Map<Integer, Integer> SUPERJOB_TOWN_TO_HH_AREA =
      Map.ofEntries(
          Map.entry(4, 1),
          Map.entry(14, 2),
          Map.entry(6, 4),
          Map.entry(9, 3),
          Map.entry(88, 88),
          Map.entry(12, 66),
          Map.entry(15, 104),
          Map.entry(78, 78),
          Map.entry(16, 76),
          Map.entry(10, 99),
          Map.entry(72, 72),
          Map.entry(19, 68),
          Map.entry(26, 26),
          Map.entry(17, 53),
          Map.entry(11, 54),
          Map.entry(55, 55),
          Map.entry(75, 75),
          Map.entry(24, 24));

  private static final List<String> KNOWN_SKILLS =
      List.of(
          "Java",
          "Python",
          "JavaScript",
          "TypeScript",
          "C++",
          "C#",
          "Go",
          "PHP",
          "Ruby",
          "Kotlin",
          "Swift",
          "Scala",
          "Rust",
          "Dart",
          "R",
          "Spring",
          "Spring Boot",
          "Spring MVC",
          "Spring Security",
          "Spring Data",
          "Spring Cloud",
          "Hibernate",
          "JPA",
          "Maven",
          "Gradle",
          "JUnit",
          "Mockito",
          "React",
          "Vue.js",
          "Angular",
          "Next.js",
          "Node.js",
          "HTML",
          "CSS",
          "Sass",
          "webpack",
          "Django",
          "Flask",
          "FastAPI",
          "Pandas",
          "NumPy",
          "SQL",
          "PostgreSQL",
          "MySQL",
          "MongoDB",
          "Redis",
          "Elasticsearch",
          "Oracle",
          "ClickHouse",
          "Cassandra",
          "SQLite",
          "Docker",
          "Kubernetes",
          "Git",
          "GitHub",
          "GitLab",
          "Jenkins",
          "Linux",
          "Bash",
          "Nginx",
          "Ansible",
          "Terraform",
          "CI/CD",
          "Kafka",
          "RabbitMQ",
          "gRPC",
          "REST API",
          "GraphQL",
          "Swagger",
          "OpenAPI",
          "Microservices",
          "OAuth",
          "JWT",
          "1С:Предприятие",
          "1С:Бухгалтерия",
          "1С:ERP",
          "1С:УТ",
          "Язык запросов 1С",
          "СКД",
          "БСП",
          "Управляемые формы",
          "Excel",
          "Word",
          "PowerPoint",
          "Outlook",
          "Google Sheets",
          "Google Docs",
          "Bitrix24",
          "AmoCRM",
          "Salesforce",
          "SAP",
          "CRM",
          "ERP",
          "Бухгалтерский учет",
          "Налоговый учет",
          "МСФО",
          "РСБУ",
          "Первичная документация",
          "Кадровый учет",
          "Документооборот",
          "Делопроизводство",
          "Кадровое делопроизводство",
          "Электронный документооборот",
          "Продажи",
          "B2B продажи",
          "B2C продажи",
          "Переговоры",
          "Холодные звонки",
          "Активные продажи",
          "Работа с клиентами",
          "Управление проектами",
          "Agile",
          "Scrum",
          "Kanban",
          "JIRA",
          "Confluence",
          "Аналитика данных",
          "Machine Learning",
          "Power BI",
          "Tableau",
          "Тестирование",
          "Автотестирование",
          "Selenium",
          "Postman",
          "Маркетинг",
          "SEO",
          "SMM",
          "Таргетированная реклама",
          "Контекстная реклама",
          "Google Analytics");

  private void attachSkillsFromKnownList(Vacancy vacancy) {
    Set<String> skills = matchKnownSkills(vacancy);
    if (skills.isEmpty()) {
      skills = inferCategorySkills(vacancy);
    }
    if (!skills.isEmpty()) {
      attachSkillNames(vacancy, skills);
    }
  }

  private Set<String> matchKnownSkills(Vacancy vacancy) {
    String text =
        (safe(vacancy.getTitle())
                + " "
                + safe(vacancy.getRequirements())
                + " "
                + safe(vacancy.getDescription()))
            .replaceAll("<[^>]+>", " ")
            .toLowerCase(Locale.ROOT);
    Set<String> found = new LinkedHashSet<>();
    for (String skill : KNOWN_SKILLS) {
      if (text.contains(skill.toLowerCase(Locale.ROOT))) {
        found.add(skill);
        if (found.size() >= 10) {
          break;
        }
      }
    }
    return found;
  }

  private Set<String> inferCategorySkills(Vacancy vacancy) {
    String titleLow = safe(vacancy.getTitle()).toLowerCase(Locale.ROOT);
    Set<String> result = new LinkedHashSet<>();
    if (titleLow.contains("1с") || titleLow.contains("1c")) {
      result.add("1С:Предприятие");
      result.add("Язык запросов 1С");
      result.add("Excel");
    } else if (titleLow.contains("java")) {
      result.add("Java");
      result.add("Spring Boot");
      result.add("SQL");
    } else if (titleLow.contains("python")) {
      result.add("Python");
      result.add("SQL");
      result.add("Git");
    } else if (titleLow.contains("javascript")
        || titleLow.contains("frontend")
        || titleLow.contains("react")) {
      result.add("JavaScript");
      result.add("React");
      result.add("HTML");
      result.add("CSS");
    } else if (titleLow.contains("бухгалтер")) {
      result.add("Бухгалтерский учет");
      result.add("1С:Бухгалтерия");
      result.add("Excel");
    } else if (titleLow.contains("продаж") || titleLow.contains("менеджер")) {
      result.add("Продажи");
      result.add("CRM");
      result.add("Переговоры");
    } else if (titleLow.contains("аналитик")) {
      result.add("SQL");
      result.add("Excel");
      result.add("Аналитика данных");
    } else if (titleLow.contains("тестировщ") || titleLow.contains("qa")) {
      result.add("Тестирование");
      result.add("Postman");
      result.add("SQL");
    } else if (titleLow.contains("devops") || titleLow.contains("системный")) {
      result.add("Docker");
      result.add("Linux");
      result.add("Git");
    }
    return result;
  }

  private void attachExtractedSkills(Vacancy vacancy) {
    for (String skillName : extractSkills(vacancy, null)) {
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
    found.addAll(inferRequirementsFromText(vacancy));
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

  private String unescapeHtml(String value) {
    return value
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'");
  }

  // ─── CRUD ─────────────────────────────────────────────────────────────────

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
    attachSkillsFromKnownList(saved);
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

  @Transactional
  public VacancyDtos.SavedAt save(User user, Long vacancyId) {
    Vacancy vacancy =
        vacancyRepository
            .findById(vacancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
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
      StringBuilder url =
          new StringBuilder("https://api.hh.ru/vacancies")
              .append("?text=")
              .append(URLEncoder.encode(role, StandardCharsets.UTF_8))
              .append("&clusters=true")
              .append("&per_page=0");
      if (areaId != null) {
        url.append("&area=").append(areaId);
      }
      HttpRequest.Builder clusterBuilder =
          HttpRequest.newBuilder(URI.create(url.toString()))
              .header("Accept", "application/json")
              .header("User-Agent", "GetEmployed/1.0 (student project)");
      String clusterToken = getHhAccessToken();
      if (clusterToken != null) {
        clusterBuilder.header("Authorization", "Bearer " + clusterToken);
      }
      HttpResponse<String> response =
          httpClient.send(clusterBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IOException("HH API returned status " + response.statusCode());
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode clustersNode = root.path("clusters");
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
          return items;
        }
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
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
  public void clearUserVacancies(User user) {
    recommendationRepository.deleteByUserId(user.getId());
    savedVacancyRepository.deleteByUserId(user.getId());
    userVacancyRepository.deleteByUserId(user.getId());
  }

  @Transactional
  public void clearAllVacancies() {
    recommendationRepository.deleteAll();
    savedVacancyRepository.deleteAll();
    userVacancyRepository.deleteAll();
    vacancySkillRepository.deleteAll();
    vacancyRepository.deleteAll();
    importJobs.clear();
  }

  @Transactional
  public VacancyDtos.RebuildAccepted rebuild(User user) {
    recommendationRepository.deleteByUserId(user.getId());
    List<Vacancy> vacancies =
        userVacancyRepository.findByUserIdOrderByImportedAtDesc(user.getId()).stream()
            .map(UserVacancy::getVacancy)
            .toList();
    for (Vacancy vacancy : vacancies) {
      vacancySkillRepository.deleteByVacancyId(vacancy.getId());
      attachSkillsFromKnownList(vacancy);
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

  // ─── Internal helpers ─────────────────────────────────────────────────────

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
            v.getTitle(), v.getEmployer(), v.getArea(), v.getSalaryMin(), v.getSalaryMax()),
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
    if (areaId != null && vacancy.getAreaId() != null && !areaId.equals(vacancy.getAreaId())) {
      return false;
    }
    return employer == null
        || employer.isBlank()
        || safe(vacancy.getEmployer())
            .toLowerCase(Locale.ROOT)
            .contains(employer.toLowerCase(Locale.ROOT));
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
