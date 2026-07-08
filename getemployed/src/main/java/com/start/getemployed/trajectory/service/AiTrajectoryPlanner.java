package com.start.getemployed.trajectory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.getemployed.entity.UserSkill;
import com.start.getemployed.entity.Vacancy;
import com.start.getemployed.entity.VacancySkill;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTrajectoryPlanner {

  private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
  private final ObjectMapper objectMapper;
  private final ExecutorService aiExecutor = Executors.newCachedThreadPool();

  @Value("${spring.ai.ollama.chat.options.model:unknown}")
  private String model;

  @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
  private String baseUrl;

  @Value("${app.ai.trajectory-timeout-seconds:60}")
  private long trajectoryTimeoutSeconds;

  public AiStatus status() {
    if (chatClientBuilder.getIfAvailable() == null) {
      return new AiStatus(false, model, baseUrl, "Spring AI ChatClient is not configured");
    }
    return isModelAvailable()
        ? new AiStatus(true, model, baseUrl, "Ollama model is available")
        : new AiStatus(
            false, model, baseUrl, "Ollama is unreachable or model is not pulled: " + model);
  }

  public TrajectoryPlan plan(
      Vacancy vacancy,
      List<VacancySkill> vacancySkills,
      List<UserSkill> userSkills,
      short weeks,
      short hoursPerWeek,
      String focus,
      String userRequest) {
    List<String> requiredSkills =
        vacancySkills.stream()
            .map(vs -> vs.getSkill().getName())
            .collect(LinkedHashSet<String>::new, LinkedHashSet::add, LinkedHashSet::addAll)
            .stream()
            .toList();
    List<String> vacancyKeywords = vacancyKeywords(vacancy, requiredSkills);
    List<String> userSkillDescriptions =
        userSkills.stream()
            .map(us -> us.getSkill().getName() + " уровень " + us.getLevel() + "/5")
            .toList();
    return callModel(
            vacancy,
            requiredSkills,
            vacancyKeywords,
            userSkillDescriptions,
            weeks,
            hoursPerWeek,
            focus,
            userRequest)
        .orElseGet(
            () ->
                fallbackPlan(vacancy, requiredSkills, vacancyKeywords, focus, userRequest, weeks));
  }

  public List<String> extractVacancyRequirements(Vacancy vacancy) {
    ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (builder == null || !isModelAvailable()) {
      return List.of();
    }
    try {
      String prompt =
          """
          Ты извлекаешь профессиональные требования из вакансии. Вакансия может быть любой:
          продажи, бухгалтерия, администрирование, производство, поддержка, маркетинг, IT и другие роли.
          Не добавляй Java/Spring/REST/SQL, если их нет в тексте. Ответь только JSON.

          Формат:
          {"requirements":["требование 1","требование 2"]}

          Правила:
          - 3-8 коротких требований.
          - Только то, что реально следует из текста.
          - Для не-IT ролей извлекай рабочие действия, инструменты, документы, коммуникации, процессы.

          Название: %s
          Работодатель: %s
          Требования: %s
          Описание: %s
          """
              .formatted(
                  safe(vacancy.getTitle()),
                  safe(vacancy.getEmployer()),
                  safe(vacancy.getRequirements()),
                  safe(vacancy.getDescription()));
      String raw = builder.build().prompt(prompt).call().content();
      JsonNode requirements = objectMapper.readTree(extractJson(raw)).path("requirements");
      List<String> result = new ArrayList<>();
      for (JsonNode item : requirements) {
        String value = item.asText("").trim();
        if (!value.isBlank() && value.length() <= 80) {
          result.add(value);
        }
      }
      return result.stream().distinct().limit(8).toList();
    } catch (RuntimeException | JsonProcessingException ex) {
      log.debug(
          "Ollama requirement extraction failed for vacancyId={}: {}",
          vacancy.getId(),
          ex.getMessage());
      return List.of();
    }
  }

  public Optional<Integer> matchVacancy(
      String profileContext, List<String> userSkills, Vacancy vacancy, List<String> vacancySkills) {
    ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (builder == null || !isModelAvailable()) {
      return Optional.empty();
    }
    try {
      String prompt =
          """
          Оцени соответствие пользователя вакансии по смыслу. Не оценивай выше только потому, что вакансия IT.
          Учитывай желаемую роль, интересы, формат, город, уровень, навыки и реальные требования вакансии.
          Ответь только JSON: {"score": 0}

          Правила:
          - score от 0 до 100.
          - 80-100: почти точное совпадение направления и требований.
          - 50-79: направление подходит, есть пробелы.
          - 20-49: частичное совпадение.
          - 0-19: другое направление.

          Профиль пользователя: %s
          Навыки пользователя: %s

          Вакансия:
          Название: %s
          Работодатель: %s
          Требования вакансии: %s
          Текст вакансии: %s
          """
              .formatted(
                  safe(profileContext),
                  userSkills,
                  safe(vacancy.getTitle()),
                  safe(vacancy.getEmployer()),
                  vacancySkills,
                  safe(vacancy.getRequirements()) + " " + safe(vacancy.getDescription()));
      String raw = builder.build().prompt(prompt).call().content();
      int score = objectMapper.readTree(extractJson(raw)).path("score").asInt(-1);
      return score < 0 ? Optional.empty() : Optional.of(Math.min(100, Math.max(0, score)));
    } catch (RuntimeException | JsonProcessingException ex) {
      log.debug(
          "Ollama vacancy match failed for vacancyId={}: {}", vacancy.getId(), ex.getMessage());
      return Optional.empty();
    }
  }

  public List<AiResource> liveLearningResources(
      String title, String description, String skillName, String profileContext) {
    ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (builder != null && isModelAvailable()) {
      try {
        String prompt =
            """
            Подбери учебные материалы под конкретный шаг подготовки. Не кешируйся: каждый ответ должен
            исходить из текущего шага и профиля. Ответь только JSON.

            Формат:
            {"resources":[{"title":"...","url":"https://...","type":"DOC|COURSE|ARTICLE|PRACTICE"}]}

            Правила:
            - 3-5 материалов.
            - Не придумывай точные URL статей. Если не уверен, дай официальный раздел или поисковую ссылку.
            - Материалы должны подходить профессии, а не всегда быть про программирование.
            - Для не-IT ролей можно давать официальные справки, профстандарты, справочники, тренажёры, поисковые запросы.

            Профиль: %s
            Шаг: %s
            Описание шага: %s
            Навык/тема: %s
            """
                .formatted(safe(profileContext), safe(title), safe(description), safe(skillName));
        String raw = builder.build().prompt(prompt).call().content();
        JsonNode resources = objectMapper.readTree(extractJson(raw)).path("resources");
        List<AiResource> result = new ArrayList<>();
        for (JsonNode item : resources) {
          String resourceTitle = item.path("title").asText("").trim();
          if (!resourceTitle.isBlank()) {
            result.add(
                new AiResource(
                    limit(resourceTitle, 255),
                    item.path("url").asText("").trim(),
                    normalizeResourceType(item.path("type").asText("ARTICLE"))));
          }
        }
        if (!result.isEmpty()) {
          return result.stream().limit(5).toList();
        }
      } catch (RuntimeException | JsonProcessingException ex) {
        log.debug("Ollama live resource search failed: {}", ex.getMessage());
      }
    }
    return curatedResources(skillName).stream().limit(3).toList();
  }

  public Optional<String> explainRecommendation(
      String profileContext,
      List<String> userSkills,
      Vacancy vacancy,
      List<String> vacancySkills,
      int totalScore,
      int semanticScore,
      int coverageScore) {
    ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (builder == null || !isModelAvailable()) {
      return Optional.empty();
    }
    try {
      String prompt =
          """
          Ты карьерный консультант. Напиши персонализированное объяснение (4-5 предложений),
          почему эта конкретная вакансия подходит пользователю. Обращайся на "вы".
          Без заголовков, только связный русский текст.

          Обязательно включи в ответ:
          1. Почему направление вакансии совпадает с профилем и целями пользователя.
          2. Конкретные навыки пользователя, которые востребованы именно в этой роли.
          3. Что даст эта работа пользователю — рост, опыт или смена направления.
          4. Практический совет: что сделать прямо сейчас (отправить резюме, подучить 1-2 навыка).
          Если есть заметные пробелы в навыках — упомяни 1-2 самых важных для подготовки.

          Профиль пользователя: %s
          Навыки пользователя: %s

          Вакансия: %s (%s)
          Навыки и требования вакансии: %s

          Итоговое совпадение: %d%% (семантика: %d%%, покрытие навыков: %d%%)
          """
              .formatted(
                  safe(profileContext),
                  userSkills,
                  safe(vacancy.getTitle()),
                  safe(vacancy.getEmployer()),
                  vacancySkills,
                  totalScore,
                  semanticScore,
                  coverageScore);
      String response = builder.build().prompt(prompt).call().content();
      if (response != null && !response.isBlank()) {
        return Optional.of(response.trim());
      }
      return Optional.empty();
    } catch (RuntimeException ex) {
      log.debug("AI explain recommendation failed: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  private Optional<TrajectoryPlan> callModel(
      Vacancy vacancy,
      List<String> requiredSkills,
      List<String> vacancyKeywords,
      List<String> userSkills,
      short weeks,
      short hoursPerWeek,
      String focus,
      String userRequest) {
    ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
    if (builder == null || !isModelAvailable()) {
      return Optional.empty();
    }
    Future<String> future = null;
    try {
      String prompt =
          buildPrompt(
              vacancy,
              requiredSkills,
              vacancyKeywords,
              userSkills,
              weeks,
              hoursPerWeek,
              focus,
              userRequest);
      long startedAt = System.nanoTime();
      log.info(
          "Generating trajectory with Ollama model={} vacancyId={} title='{}' skills={} timeout={}s",
          model,
          vacancy.getId(),
          vacancy.getTitle(),
          requiredSkills,
          trajectoryTimeoutSeconds);
      future = aiExecutor.submit(() -> builder.build().prompt(prompt).call().content());
      String raw = future.get(trajectoryTimeoutSeconds, TimeUnit.SECONDS);
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      log.info(
          "Ollama trajectory response received in {} ms for vacancyId={} chars={}",
          elapsedMs,
          vacancy.getId(),
          raw == null ? 0 : raw.length());
      TrajectoryPlan plan = objectMapper.readValue(extractJson(raw), TrajectoryPlan.class);
      if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(normalize(plan, weeks, hoursPerWeek));
    } catch (TimeoutException ex) {
      if (future != null) {
        future.cancel(true);
      }
      log.warn(
          "Ollama trajectory generation timed out after {} seconds for vacancyId={}",
          trajectoryTimeoutSeconds,
          vacancy.getId());
      return Optional.empty();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (ExecutionException ex) {
      log.warn(
          "Ollama trajectory generation failed for vacancyId={}: {}",
          vacancy.getId(),
          ex.getMessage());
      return Optional.empty();
    } catch (RuntimeException | JsonProcessingException ex) {
      log.warn(
          "Ollama trajectory response was not usable for vacancyId={}: {}",
          vacancy.getId(),
          ex.getMessage());
      return Optional.empty();
    }
  }

  private boolean isOllamaReachable() {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
      HttpRequest request =
          HttpRequest.newBuilder(ollamaUri("/api/tags"))
              .timeout(Duration.ofMillis(800))
              .GET()
              .build();
      int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      return status >= 200 && status < 300;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException | IllegalArgumentException ex) {
      return false;
    }
  }

  private boolean isModelAvailable() {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
      HttpRequest request =
          HttpRequest.newBuilder(ollamaUri("/api/tags"))
              .timeout(Duration.ofMillis(800))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return false;
      }
      JsonNode models = objectMapper.readTree(response.body()).path("models");
      for (JsonNode item : models) {
        if (matchesConfiguredModel(item.path("name").asText())
            || matchesConfiguredModel(item.path("model").asText())) {
          return true;
        }
      }
      return false;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException | IllegalArgumentException ex) {
      return false;
    }
  }

  private boolean matchesConfiguredModel(String availableModel) {
    String configured = normalizeModelName(model);
    String available = normalizeModelName(availableModel);
    return !configured.isBlank()
        && (available.equals(configured)
            || available.equals(configured + ":latest")
            || available.startsWith(configured + ":"));
  }

  private String normalizeModelName(String value) {
    return safe(value).trim().toLowerCase(Locale.ROOT);
  }

  private URI ollamaUri(String path) {
    String root = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
    String normalizedRoot = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    return URI.create(normalizedRoot + normalizedPath);
  }

  private String buildPrompt(
      Vacancy vacancy,
      List<String> requiredSkills,
      List<String> vacancyKeywords,
      List<String> userSkills,
      short weeks,
      short hoursPerWeek,
      String focus,
      String userRequest) {
    return """
        Ты карьерный наставник. Пользователь может искать любую работу: IT, 1С, продажи,
        администрирование, бухгалтерию, поддержку, аналитику, маркетинг, производство и другие роли.
        Составь практическую траекторию подготовки под конкретную вакансию.
        Ответь только валидным JSON без markdown.

        Формат:
        {
          "summary": "короткое объяснение плана",
          "steps": [
            {
              "title": "конкретное действие",
              "description": "что именно сделать и какой результат получить",
              "type": "LEARN|PRACTICE|PROJECT|READ",
              "skillName": "название навыка",
              "week": 1,
              "estimatedHours": 3,
              "resources": [
                {"title": "что искать или открыть", "url": "https://...", "type": "DOC|COURSE|ARTICLE|PRACTICE"}
              ]
            }
          ]
        }

        Требования:
        - Недель: %d.
        - Часов в неделю: %d.
        - Сделай 1-2 шага на каждую неделю, максимум 10 шагов всего.
        - План должен быть уникальным именно для этой вакансии. Не выдавай универсальный шаблон.
        - Каждый шаг должен опираться на конкретные слова из вакансии, стек, обязанности или домен.
        - В title/description шагов используй конкретику: технологии, тип сервиса, формат задач, требования работодателя.
        - Начинай с самых важных пробелов, затем дай мини-проект под вакансию.
        - Мини-проект должен повторять смысл вакансии, а не быть абстрактным TODO/CRUD без контекста.
        - Если вакансии отличаются, итоговые шаги и мини-проект обязаны отличаться.
        - Учитывай фокус пользователя: %s. practice = больше задач, theory = закрыть теорию, project = собрать проект, interview = подготовка к собеседованию.
        - Учитывай сохранённый карьерный контекст и цель пользователя: %s. Это важное поле, оно должно менять темы, порядок шагов и проект.
        - Не подменяй стек вакансии IT-стеком. Если вакансия не про Java/Spring/REST, не добавляй Java/Spring/REST.
        - Для не-IT ролей используй реальные рабочие действия: документы, регламенты, коммуникации, таблицы, CRM/1С, продажи, отчёты, процессы.
        - Пиши по-русски, названия технологий оставляй как принято.
        - В каждом шаге добавь 1-3 ресурса: официальная документация, статья или практический тренажер/тест.
        - Не придумывай несуществующие URL. Если не уверен в точной статье, дай официальный раздел документации или страницу практики.
        - Для практики предпочитай интерактивные задачи, тесты, kata, SQLBolt, MDN, Spring Guides, официальную документацию.

        Вакансия:
        Название: %s
        Работодатель: %s
        Требования: %s
        Описание: %s

        Навыки вакансии: %s
        Ключевые слова вакансии: %s
        Навыки пользователя: %s
        """
        .formatted(
            weeks,
            hoursPerWeek,
            safe(focus),
            safe(userRequest),
            safe(vacancy.getTitle()),
            safe(vacancy.getEmployer()),
            safe(vacancy.getRequirements()),
            safe(vacancy.getDescription()),
            requiredSkills,
            vacancyKeywords,
            userSkills);
  }

  private TrajectoryPlan fallbackPlan(
      Vacancy vacancy,
      List<String> requiredSkills,
      List<String> vacancyKeywords,
      String focus,
      String userRequest,
      short weeks) {
    boolean oneC = isOneCContext(vacancy, userRequest);
    List<String> skills = oneC ? oneCSkills(requiredSkills) : requiredSkills;
    if (skills.isEmpty()) {
      skills = inferSkills(vacancy);
    }
    if (skills.isEmpty()) {
      skills = fallbackRoleSkills(vacancy, userRequest);
    }
    List<AiStep> steps = new ArrayList<>();
    String role = vacancyRole(vacancy);
    String project = projectIdea(vacancy, skills, vacancyKeywords);
    int index = 0;
    for (String skill : skills) {
      short week = (short) (index % weeks + 1);
      String keyword =
          vacancyKeywords.isEmpty() ? role : vacancyKeywords.get(index % vacancyKeywords.size());
      steps.add(
          new AiStep(
              "Разобрать " + skill + " для роли " + role,
              "Изучи "
                  + skill
                  + " в контексте вакансии: "
                  + keyword
                  + ". Составь конспект по требованиям работодателя и список вопросов для проверки.",
              "LEARN",
              skill,
              week,
              (short) 3,
              defaultResources(skill)));
      steps.add(
          new AiStep(
              "Закрепить " + skill + " на задаче под " + role,
              "Сделай практическую задачу для сценария: "
                  + keyword
                  + ". Результат оформи в репозитории или заметках с коротким README.",
              "PRACTICE",
              skill,
              week,
              (short) 3,
              defaultResources(skill)));
      index++;
    }
    steps.add(
        new AiStep(
            "Собрать мини-проект: " + project,
            "Сделай проект под вакансию \""
                + safe(vacancy.getTitle())
                + "\": "
                + project
                + ". В README опиши стек, сценарии, API/интерфейс, тесты и как это связано с требованиями работодателя"
                + fallbackTail(focus, userRequest),
            "PROJECT",
            skills.get(0),
            weeks,
            (short) 5,
            curatedResources(skills.get(0))));
    return normalize(
        new TrajectoryPlan("План под вакансию: " + safe(vacancy.getTitle()), steps),
        weeks,
        (short) 15);
  }

  private List<String> vacancyKeywords(Vacancy vacancy, List<String> requiredSkills) {
    Set<String> keywords = new LinkedHashSet<>(requiredSkills);
    String text =
        (safe(vacancy.getTitle())
                + " "
                + safe(vacancy.getRequirements())
                + " "
                + safe(vacancy.getDescription()))
            .toLowerCase(Locale.ROOT);
    for (String keyword :
        List.of(
            "api",
            "backend",
            "frontend",
            "микросервис",
            "интеграции",
            "банк",
            "финтех",
            "e-commerce",
            "аналитика",
            "тестирование",
            "автоматизация",
            "legacy",
            "нагрузка",
            "безопасность",
            "crm",
            "erp",
            "mobile",
            "data",
            "cloud",
            "devops")) {
      if (text.contains(keyword)) {
        keywords.add(keyword);
      }
    }
    if (text.contains("1с") || text.contains("1c")) {
      keywords.add("1С:Предприятие");
      keywords.add("конфигурации 1С");
      keywords.add("язык запросов 1С");
    }
    if (!safe(vacancy.getEmployer()).isBlank()) {
      keywords.add("работодатель: " + vacancy.getEmployer());
    }
    return keywords.stream().limit(10).toList();
  }

  private List<String> fallbackRoleSkills(Vacancy vacancy, String userRequest) {
    String role = (vacancyRole(vacancy) + " " + safe(userRequest)).toLowerCase(Locale.ROOT);
    if (role.contains("1с") || role.contains("1c")) {
      return oneCSkills(List.of());
    }
    if (role.contains("frontend")) {
      return List.of("JavaScript", "HTML", "CSS", "React");
    }
    if (role.contains("python")) {
      return List.of("Python", "SQL", "REST API", "Git");
    }
    if (role.contains("qa") || role.contains("тест")) {
      return List.of("Тестирование", "SQL", "REST API", "Git");
    }
    if (role.contains("аналит")) {
      return List.of("SQL", "Аналитика требований", "Документация", "API");
    }
    if (role.contains("продаж") || role.contains("менеджер")) {
      return List.of("Продажи", "Переговоры", "CRM", "Коммуникация с клиентами");
    }
    if (role.contains("администратор") || role.contains("офис")) {
      return List.of(
          "Работа с документами", "Коммуникация с клиентами", "Excel", "Организация процессов");
    }
    if (role.contains("бухгалтер")) {
      return List.of("Бухгалтерский учет", "1С:Предприятие", "Excel", "Первичная документация");
    }
    if (role.contains("поддерж")) {
      return List.of(
          "Техническая поддержка",
          "Коммуникация с клиентами",
          "Работа с обращениями",
          "База знаний");
    }
    if (role.contains("маркет")) {
      return List.of("Маркетинг", "Копирайтинг", "Аналитика", "Контент-план");
    }
    return List.of(
        "Профессиональная терминология роли",
        "Ключевые обязанности",
        "Рабочие инструменты",
        "Коммуникация");
  }

  private boolean isOneCContext(Vacancy vacancy, String userRequest) {
    String text =
        (safe(vacancy.getTitle())
                + " "
                + safe(vacancy.getRequirements())
                + " "
                + safe(vacancy.getDescription())
                + " "
                + safe(userRequest))
            .toLowerCase(Locale.ROOT);
    return text.contains("1с") || text.contains("1c");
  }

  private List<String> oneCSkills(List<String> existingSkills) {
    LinkedHashSet<String> skills = new LinkedHashSet<>();
    existingSkills.stream()
        .filter(
            skill -> {
              String normalized = safe(skill).toLowerCase(Locale.ROOT);
              return normalized.contains("1с")
                  || normalized.contains("1c")
                  || normalized.contains("sql")
                  || normalized.contains("скд")
                  || normalized.contains("бсп");
            })
        .forEach(skills::add);
    skills.add("1С:Предприятие");
    skills.add("Язык запросов 1С");
    skills.add("СКД");
    skills.add("Управляемые формы");
    skills.add("БСП");
    skills.add("SQL");
    return skills.stream().limit(6).toList();
  }

  private String vacancyRole(Vacancy vacancy) {
    String title = safe(vacancy.getTitle()).trim();
    return title.isBlank() ? "выбранной роли" : limit(title, 80);
  }

  private String projectIdea(Vacancy vacancy, List<String> skills, List<String> keywords) {
    String role = vacancyRole(vacancy);
    String stack = String.join(", ", skills.stream().limit(4).toList());
    String context =
        keywords.isEmpty()
            ? safe(vacancy.getEmployer())
            : String.join(", ", keywords.stream().limit(4).toList());
    if (role.toLowerCase(Locale.ROOT).contains("frontend")) {
      return "интерфейс для сценария вакансии с фильтрами, состояниями загрузки и адаптивной версткой";
    }
    if (role.toLowerCase(Locale.ROOT).contains("1с")
        || role.toLowerCase(Locale.ROOT).contains("1c")) {
      return "обработка или подсистема 1С с документом, справочником, регистром, отчетом на СКД и запросами к данным";
    }
    if (role.toLowerCase(Locale.ROOT).contains("qa")
        || role.toLowerCase(Locale.ROOT).contains("тест")) {
      return "набор автотестов и чек-листов для API/веб-сценария из вакансии";
    }
    if (role.toLowerCase(Locale.ROOT).contains("продаж")
        || role.toLowerCase(Locale.ROOT).contains("менеджер")) {
      return "воронка продаж с CRM-карточками, скриптом общения, обработкой возражений и отчётом по лидам";
    }
    if (role.toLowerCase(Locale.ROOT).contains("администратор")) {
      return "рабочий регламент администратора: документы, заявки, расписание, коммуникация с клиентами и отчётность";
    }
    return "портфолио-кейс под вакансию: рабочий процесс, инструменты, типовые задачи и результат с контекстом: "
        + context;
  }

  private String fallbackTail(String focus, String userRequest) {
    StringBuilder tail = new StringBuilder();
    if (focus != null && !focus.isBlank()) {
      tail.append(". Фокус: ").append(focus.trim());
    }
    if (userRequest != null && !userRequest.isBlank()) {
      tail.append(". Дополнительно: ").append(userRequest.trim());
    }
    return tail.toString();
  }

  private TrajectoryPlan normalize(TrajectoryPlan plan, short weeks, short hoursPerWeek) {
    List<AiStep> normalized =
        plan.steps().stream()
            .filter(step -> step.title() != null && !step.title().isBlank())
            .sorted(Comparator.comparingInt(step -> safeWeek(step.week(), weeks)))
            .map(
                step ->
                    new AiStep(
                        limit(step.title(), 255),
                        step.description(),
                        normalizeType(step.type()),
                        step.skillName(),
                        (short) safeWeek(step.week(), weeks),
                        normalizeHours(step.estimatedHours(), hoursPerWeek),
                        normalizeResources(step.skillName(), step.resources())))
            .limit(Math.max(weeks * 4L, 6L))
            .toList();
    return new TrajectoryPlan(plan.summary(), normalized);
  }

  private List<AiResource> defaultResources(String skill) {
    return curatedResources(skill);
  }

  private List<AiResource> normalizeResources(String skill, List<AiResource> resources) {
    List<AiResource> normalized = new ArrayList<>();
    if (resources != null) {
      resources.stream()
          .filter(r -> r != null && r.title() != null && !r.title().isBlank())
          .limit(3)
          .forEach(
              r ->
                  normalized.add(
                      new AiResource(
                          limit(r.title(), 255),
                          r.url() == null ? "" : r.url().trim(),
                          normalizeResourceType(r.type()))));
    }
    boolean hasLink = normalized.stream().anyMatch(r -> r.url() != null && !r.url().isBlank());
    if (!hasLink) {
      normalized.addAll(curatedResources(skill).stream().limit(2).toList());
    }
    return normalized.stream().limit(4).toList();
  }

  private String normalizeResourceType(String type) {
    if (type == null) {
      return "ARTICLE";
    }
    String normalized = type.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "DOC", "COURSE", "PRACTICE" -> normalized;
      default -> "ARTICLE";
    };
  }

  private List<AiResource> curatedResources(String skill) {
    String normalized = safe(skill).toLowerCase(Locale.ROOT);
    if (normalized.contains("1с")
        || normalized.contains("1c")
        || normalized.contains("скд")
        || normalized.contains("бсп")
        || normalized.contains("управляем")) {
      return List.of(
          new AiResource("Документация 1С:Предприятие", "https://v8.1c.ru/platforma/", "DOC"),
          new AiResource("ИТС 1С", "https://its.1c.ru/", "DOC"),
          new AiResource("Практика и статьи по 1С", "https://infostart.ru/1c/", "ARTICLE"));
    }
    if (normalized.contains("spring")) {
      return List.of(
          new AiResource("Spring Guides", "https://spring.io/guides", "PRACTICE"),
          new AiResource(
              "Spring Boot Reference", "https://docs.spring.io/spring-boot/index.html", "DOC"));
    }
    if (normalized.contains("java")) {
      return List.of(
          new AiResource("Dev.java Tutorials", "https://dev.java/learn/", "DOC"),
          new AiResource("CodingBat Java Practice", "https://codingbat.com/java", "PRACTICE"));
    }
    if (normalized.contains("sql") || normalized.contains("postgres")) {
      return List.of(
          new AiResource("SQLBolt интерактивные задачи", "https://sqlbolt.com/", "PRACTICE"),
          new AiResource(
              "PostgreSQL Tutorial",
              "https://www.postgresql.org/docs/current/tutorial.html",
              "DOC"));
    }
    if (normalized.contains("rest") || normalized.contains("http") || normalized.contains("api")) {
      return List.of(
          new AiResource("MDN HTTP", "https://developer.mozilla.org/en-US/docs/Web/HTTP", "DOC"),
          new AiResource("REST API Tutorial", "https://restfulapi.net/", "ARTICLE"));
    }
    if (normalized.contains("docker")) {
      return List.of(
          new AiResource("Docker Get Started", "https://docs.docker.com/get-started/", "DOC"),
          new AiResource("Play with Docker", "https://labs.play-with-docker.com/", "PRACTICE"));
    }
    if (normalized.contains("git")) {
      return List.of(
          new AiResource("Learn Git Branching", "https://learngitbranching.js.org/", "PRACTICE"),
          new AiResource("Git Book", "https://git-scm.com/book/en/v2", "DOC"));
    }
    if (normalized.contains("junit") || normalized.contains("test")) {
      return List.of(
          new AiResource(
              "JUnit 5 User Guide", "https://junit.org/junit5/docs/current/user-guide/", "DOC"),
          new AiResource(
              "JUnit 5 практические примеры", "https://www.baeldung.com/junit-5", "ARTICLE"));
    }
    if (normalized.contains("kafka")) {
      return List.of(
          new AiResource("Apache Kafka Quickstart", "https://kafka.apache.org/quickstart", "DOC"),
          new AiResource("Kafka Documentation", "https://kafka.apache.org/documentation/", "DOC"));
    }
    if (normalized.contains("redis")) {
      return List.of(
          new AiResource("Redis Docs", "https://redis.io/docs/latest/", "DOC"),
          new AiResource("Try Redis", "https://try.redis.io/", "PRACTICE"));
    }
    if (normalized.contains("javascript") || normalized.equals("js")) {
      return List.of(
          new AiResource(
              "MDN JavaScript Guide",
              "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide",
              "DOC"),
          new AiResource(
              "JavaScript задачи",
              "https://www.freecodecamp.org/learn/javascript-algorithms-and-data-structures-v8/",
              "PRACTICE"));
    }
    if (normalized.contains("react")) {
      return List.of(
          new AiResource("React Learn", "https://react.dev/learn", "DOC"),
          new AiResource(
              "React Challenges", "https://react.dev/learn/tutorial-tic-tac-toe", "PRACTICE"));
    }
    if (normalized.contains("html") || normalized.contains("css")) {
      return List.of(
          new AiResource(
              "MDN Learn Web Development", "https://developer.mozilla.org/en-US/docs/Learn", "DOC"),
          new AiResource(
              "Frontend Mentor Challenges",
              "https://www.frontendmentor.io/challenges",
              "PRACTICE"));
    }
    return List.of(
        new AiResource("Roadmap.sh по направлению", "https://roadmap.sh/", "ARTICLE"),
        new AiResource(
            "Практические задания: " + safe(skill),
            "https://www.google.com/search?q="
                + URLEncoder.encode(safe(skill) + " practice tasks tests", StandardCharsets.UTF_8),
            "PRACTICE"));
  }

  private List<String> inferSkills(Vacancy vacancy) {
    String text =
        (safe(vacancy.getTitle())
                + " "
                + safe(vacancy.getRequirements())
                + " "
                + safe(vacancy.getDescription()))
            .toLowerCase(Locale.ROOT);
    return List.of(
            "Java",
            "Spring Boot",
            "1С:Предприятие",
            "Язык запросов 1С",
            "СКД",
            "БСП",
            "Управляемые формы",
            "SQL",
            "PostgreSQL",
            "REST API",
            "Docker",
            "Git",
            "JUnit",
            "Kafka",
            "Redis")
        .stream()
        .filter(skill -> text.contains(skill.toLowerCase(Locale.ROOT)))
        .toList();
  }

  private String extractJson(String raw) {
    if (raw == null) {
      return "{}";
    }
    String cleaned = raw.trim();
    if (cleaned.startsWith("```")) {
      cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
    }
    int start = cleaned.indexOf('{');
    int end = cleaned.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return cleaned.substring(start, end + 1);
    }
    return cleaned;
  }

  private int safeWeek(Short week, short maxWeeks) {
    if (week == null) {
      return 1;
    }
    return Math.min(Math.max(1, week), maxWeeks);
  }

  private Short normalizeHours(Short hours, short hoursPerWeek) {
    int maxPerStep = Math.max(1, hoursPerWeek / 2);
    if (hours == null) {
      return (short) Math.min(3, maxPerStep);
    }
    return (short) Math.min(Math.max(1, hours), Math.max(1, maxPerStep));
  }

  private String normalizeType(String type) {
    if (type == null) {
      return "LEARN";
    }
    String normalized = type.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "PRACTICE", "PROJECT", "READ" -> normalized;
      default -> "LEARN";
    };
  }

  private String limit(String value, int max) {
    if (value == null || value.length() <= max) {
      return value;
    }
    return value.substring(0, max);
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  public record TrajectoryPlan(String summary, List<AiStep> steps) {}

  public record AiStatus(boolean available, String model, String baseUrl, String message) {}

  public record AiStep(
      String title,
      String description,
      String type,
      String skillName,
      Short week,
      Short estimatedHours,
      List<AiResource> resources) {}

  public record AiResource(String title, String url, String type) {}
}
