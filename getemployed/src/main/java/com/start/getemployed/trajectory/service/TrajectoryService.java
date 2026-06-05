package com.start.getemployed.trajectory.service;

import com.start.getemployed.entity.Skill;
import com.start.getemployed.entity.Trajectory;
import com.start.getemployed.entity.TrajectoryStep;
import com.start.getemployed.entity.User;
import com.start.getemployed.entity.UserSkill;
import com.start.getemployed.entity.Vacancy;
import com.start.getemployed.entity.VacancySkill;
import com.start.getemployed.exception.ResourceNotFoundException;
import com.start.getemployed.profile.repository.ProfileRepository;
import com.start.getemployed.skills.repository.SkillRepository;
import com.start.getemployed.skills.repository.UserSkillRepository;
import com.start.getemployed.trajectory.dto.TrajectoryDtos;
import com.start.getemployed.trajectory.repository.StepResourceRepository;
import com.start.getemployed.trajectory.repository.TrajectoryRepository;
import com.start.getemployed.trajectory.repository.TrajectoryStepRepository;
import com.start.getemployed.trajectory.service.AiTrajectoryPlanner.AiStep;
import com.start.getemployed.trajectory.service.AiTrajectoryPlanner.TrajectoryPlan;
import com.start.getemployed.vacancy.repository.UserVacancyRepository;
import com.start.getemployed.vacancy.repository.VacancyRepository;
import com.start.getemployed.vacancy.repository.VacancySkillRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrajectoryService {

  private final TrajectoryRepository trajectoryRepository;
  private final TrajectoryStepRepository stepRepository;
  private final StepResourceRepository resourceRepository;
  private final VacancyRepository vacancyRepository;
  private final VacancySkillRepository vacancySkillRepository;
  private final UserVacancyRepository userVacancyRepository;
  private final SkillRepository skillRepository;
  private final UserSkillRepository userSkillRepository;
  private final ProfileRepository profileRepository;
  private final AiTrajectoryPlanner aiTrajectoryPlanner;
  private final RedisTemplate<String, Object> redisTemplate;

  public TrajectoryDtos.AiStatusResponse aiStatus() {
    AiTrajectoryPlanner.AiStatus status = aiTrajectoryPlanner.status();
    return new TrajectoryDtos.AiStatusResponse(
        status.available(), status.model(), status.baseUrl(), status.message());
  }

  @Transactional(readOnly = true)
  public TrajectoryDtos.TrajectoryResponse active(User user) {
    return toResponse(activeTrajectory(user));
  }

  @Transactional(readOnly = true)
  public List<TrajectoryDtos.StepResponse> steps(User user, Short week, String status) {
    Trajectory trajectory = activeTrajectory(user);
    List<TrajectoryStep> steps =
        week == null
            ? stepRepository.findByTrajectoryIdOrderByWeekAscOrderAsc(trajectory.getId())
            : stepRepository.findByTrajectoryIdAndWeekOrderByOrderAsc(trajectory.getId(), week);
    return steps.stream()
        .filter(s -> status == null || status.equalsIgnoreCase(s.getStatus()))
        .map(this::toStepResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TrajectoryDtos.ResourceResponse> liveResources(User user, Long stepId) {
    TrajectoryStep step =
        stepRepository
            .findByIdAndUserId(stepId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Шаг не найден"));
    return aiTrajectoryPlanner
        .liveLearningResources(
            step.getTitle(),
            step.getDescription(),
            step.getSkill() == null ? null : step.getSkill().getName(),
            trajectoryRequest(user, null))
        .stream()
        .map(r -> new TrajectoryDtos.ResourceResponse(r.title(), r.url(), r.type()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TrajectoryDtos.WeekStepResponse> currentWeek(User user) {
    Trajectory trajectory = activeTrajectory(user);
    short week =
        (short)
            Math.min(Math.max(1, trajectory.getCompletedSteps() / 5 + 1), trajectory.getWeeks());
    return stepRepository
        .findByTrajectoryIdAndWeekOrderByOrderAsc(trajectory.getId(), week)
        .stream()
        .map(
            s ->
                new TrajectoryDtos.WeekStepResponse(
                    s.getId(),
                    s.getTitle(),
                    s.getSkill() == null ? null : s.getSkill().getName(),
                    s.getEstimatedHours(),
                    s.getStatus(),
                    s.getProgressPercent(),
                    s.getDeadline()))
        .toList();
  }

  @Transactional
  public TrajectoryDtos.GenerateResponse generate(
      User user, Long vacancyId, TrajectoryDtos.GenerateRequest request) {
    Vacancy vacancy =
        vacancyRepository
            .findById(vacancyId)
            .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
    if (!userVacancyRepository.existsByUserIdAndVacancyId(user.getId(), vacancyId)) {
      throw new ResourceNotFoundException("Вакансия не импортирована для текущего пользователя");
    }
    trajectoryRepository
        .findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())
        .ifPresent(
            t -> {
              t.setActive(false);
              t.setCompletedAt(LocalDateTime.now());
              trajectoryRepository.save(t);
            });
    Trajectory trajectory = new Trajectory();
    trajectory.setUser(user);
    trajectory.setVacancy(vacancy);
    trajectory.setHoursPerWeek(
        request.hoursPerWeek() == null ? (short) 15 : request.hoursPerWeek());
    trajectory.setWeeks(request.weeks() == null ? (short) 4 : request.weeks());
    trajectory = trajectoryRepository.save(trajectory);

    List<VacancySkill> gaps = vacancySkillRepository.findByVacancyId(vacancyId);
    List<UserSkill> userSkills = userSkillRepository.findByUserId(user.getId());
    TrajectoryPlan plan =
        aiTrajectoryPlanner.plan(
            vacancy,
            gaps,
            userSkills,
            trajectory.getWeeks(),
            trajectory.getHoursPerWeek(),
            request.focus(),
            trajectoryRequest(user, request.userRequest()));
    short order = 1;
    for (AiStep aiStep : plan.steps()) {
      createStep(user, trajectory, aiStep, order++);
    }
    trajectory.setTotalSteps(
        (int) stepRepository.findByTrajectoryIdOrderByWeekAscOrderAsc(trajectory.getId()).size());
    trajectoryRepository.save(trajectory);
    invalidate(user.getId());
    List<TrajectoryDtos.GeneratedStep> generated =
        stepRepository.findByTrajectoryIdOrderByWeekAscOrderAsc(trajectory.getId()).stream()
            .map(
                s ->
                    new TrajectoryDtos.GeneratedStep(
                        s.getId(),
                        s.getTitle(),
                        s.getSkill() == null ? null : s.getSkill().getName(),
                        s.getWeek(),
                        s.getOrder(),
                        s.getEstimatedHours(),
                        s.getStatus()))
            .toList();
    return new TrajectoryDtos.GenerateResponse(
        trajectory.getId(),
        trajectory.getTotalSteps(),
        trajectory.getWeeks(),
        generated.stream()
            .map(TrajectoryDtos.GeneratedStep::skillName)
            .filter(Objects::nonNull)
            .distinct()
            .toList(),
        generated);
  }

  @Transactional
  public TrajectoryDtos.PatchStepResponse patch(
      User user, Long stepId, TrajectoryDtos.PatchStepRequest request) {
    TrajectoryStep step =
        stepRepository
            .findByIdAndUserId(stepId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Шаг не найден"));
    if (request.status() != null) {
      if (!request.status().matches("PENDING|IN_PROGRESS|COMPLETED|SKIPPED")) {
        throw new IllegalArgumentException(
            "status must be PENDING, IN_PROGRESS, COMPLETED or SKIPPED");
      }
      step.setStatus(request.status());
      step.setCompletedAt("COMPLETED".equals(request.status()) ? LocalDateTime.now() : null);
    }
    if (request.progressPercent() != null) {
      step.setProgressPercent(request.progressPercent());
    }
    if (request.note() != null) {
      step.setNote(request.note());
    }
    stepRepository.save(step);
    Trajectory trajectory = recalc(step.getTrajectory());
    invalidate(user.getId());
    return new TrajectoryDtos.PatchStepResponse(
        new TrajectoryDtos.StepStatus(step.getId(), step.getStatus(), step.getCompletedAt()),
        new TrajectoryDtos.TrajectoryStatus(
            progress(trajectory), trajectory.getCompletedSteps(), trajectory.getTotalSteps()));
  }

  @Transactional
  public TrajectoryDtos.StepResponse put(
      User user, Long stepId, TrajectoryDtos.PutStepRequest request) {
    TrajectoryStep step =
        stepRepository
            .findByIdAndUserId(stepId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Шаг не найден"));
    if (request.deadline() != null) {
      step.setDeadline(request.deadline());
    }
    if (request.estimatedHours() != null) {
      step.setEstimatedHours(request.estimatedHours());
    }
    if (request.note() != null) {
      step.setNote(request.note());
    }
    invalidate(user.getId());
    return toStepResponse(stepRepository.save(step));
  }

  @Transactional
  public void deleteActive(User user) {
    Trajectory trajectory = activeTrajectory(user);
    resourceRepository.deleteByTrajectoryId(trajectory.getId());
    stepRepository.deleteByTrajectoryId(trajectory.getId());
    trajectoryRepository.deleteByIdAndUserId(trajectory.getId(), user.getId());
    invalidate(user.getId());
  }

  @Transactional(readOnly = true)
  public List<TrajectoryDtos.HistoryItem> history(User user) {
    return trajectoryRepository
        .findByUserIdAndActiveFalseOrderByCreatedAtDesc(user.getId())
        .stream()
        .map(
            t ->
                new TrajectoryDtos.HistoryItem(
                    t.getId(),
                    t.getVacancy() == null ? null : t.getVacancy().getTitle(),
                    t.getCompletedSteps(),
                    t.getTotalSteps(),
                    progress(t),
                    t.getCreatedAt().toLocalDate(),
                    t.getCompletedAt() == null ? null : t.getCompletedAt().toLocalDate()))
        .toList();
  }

  private void createStep(User user, Trajectory trajectory, AiStep aiStep, short order) {
    TrajectoryStep step = new TrajectoryStep();
    step.setUser(user);
    step.setTrajectory(trajectory);
    step.setSkill(resolveSkill(aiStep.skillName()));
    step.setTitle(aiStep.title());
    step.setDescription(aiStep.description());
    step.setType(aiStep.type());
    step.setEstimatedHours(aiStep.estimatedHours());
    step.setWeek(aiStep.week());
    step.setOrder(order);
    TrajectoryStep saved = stepRepository.save(step);
    // Learning resources are intentionally loaded live through Ollama/search context
    // and are not persisted, so users get fresh materials for the current step.
  }

  private Skill resolveSkill(String skillName) {
    if (skillName == null || skillName.isBlank()) {
      return null;
    }
    return skillRepository
        .findByNameIgnoreCase(skillName.trim())
        .orElseGet(
            () -> {
              Skill skill = new Skill();
              skill.setName(skillName.trim());
              skill.setCategory("AI_TRAJECTORY");
              skill.setPopularity(0.0);
              return skillRepository.save(skill);
            });
  }

  private String trajectoryRequest(User user, String userRequest) {
    String profileContext =
        profileRepository
            .findByUserId(user.getId())
            .map(profile -> profile.getGoal() == null ? "" : profile.getGoal())
            .orElse("");
    if (profileContext.isBlank()) {
      return userRequest;
    }
    if (userRequest == null || userRequest.isBlank()) {
      return "Контекст первого теста пользователя: " + profileContext;
    }
    return "Контекст первого теста пользователя: "
        + profileContext
        + ". Дополнительный запрос к этой траектории: "
        + userRequest;
  }

  private Trajectory activeTrajectory(User user) {
    return trajectoryRepository
        .findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Активная траектория не найдена"));
  }

  private Trajectory recalc(Trajectory trajectory) {
    int completed =
        (int) stepRepository.countByTrajectoryIdAndStatus(trajectory.getId(), "COMPLETED");
    trajectory.setCompletedSteps(completed);
    if (completed == trajectory.getTotalSteps() && trajectory.getTotalSteps() > 0) {
      trajectory.setActive(false);
      trajectory.setCompletedAt(LocalDateTime.now());
    }
    return trajectoryRepository.save(trajectory);
  }

  private TrajectoryDtos.TrajectoryResponse toResponse(Trajectory trajectory) {
    return new TrajectoryDtos.TrajectoryResponse(
        trajectory.getId(),
        trajectory.getUser().getId(),
        trajectory.getVacancy() == null ? null : trajectory.getVacancy().getId(),
        trajectory.getVacancy() == null ? null : trajectory.getVacancy().getTitle(),
        trajectory.getTotalSteps(),
        trajectory.getCompletedSteps(),
        progress(trajectory),
        trajectory.getWeeks(),
        Math.min(Math.max(1, trajectory.getCompletedSteps() / 5 + 1), trajectory.getWeeks()),
        trajectory.getCreatedAt(),
        trajectory.getCreatedAt().plusWeeks(trajectory.getWeeks()));
  }

  private TrajectoryDtos.StepResponse toStepResponse(TrajectoryStep step) {
    return new TrajectoryDtos.StepResponse(
        step.getId(),
        step.getTrajectory().getId(),
        step.getSkill() == null ? null : step.getSkill().getId(),
        step.getSkill() == null ? null : step.getSkill().getName(),
        step.getTitle(),
        step.getDescription(),
        step.getType(),
        step.getEstimatedHours(),
        step.getWeek(),
        step.getOrder(),
        step.getStatus(),
        step.getProgressPercent(),
        step.getNote(),
        step.getDeadline(),
        step.getCompletedAt(),
        resourceRepository.findByStepId(step.getId()).stream()
            .map(r -> new TrajectoryDtos.ResourceResponse(r.getTitle(), r.getUrl(), r.getType()))
            .toList());
  }

  private int progress(Trajectory trajectory) {
    if (trajectory.getTotalSteps() == null || trajectory.getTotalSteps() == 0) {
      return 0;
    }
    return (int)
        Math.round((double) trajectory.getCompletedSteps() * 100 / trajectory.getTotalSteps());
  }

  private void invalidate(Long userId) {
    redisTemplate.delete("trajectory:week:" + userId);
    redisTemplate.delete("dashboard:" + userId);
  }
}
