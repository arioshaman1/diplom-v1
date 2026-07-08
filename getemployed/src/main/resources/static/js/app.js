/* ═══════════════════════════════════════════════════════
   GetEmployed — app.js
   API base: current Spring Boot host /api/v1
═══════════════════════════════════════════════════════ */

const BACKEND_ORIGIN =
  window.location.port === '8080'
    ? `${window.location.protocol}//${window.location.hostname}:8080`
    : window.location.origin;
const BASE = `${BACKEND_ORIGIN}/api/v1`;

// ──────────── STATE ────────────
const state = {
  token: localStorage.getItem('ge_token') || null,
  user:  JSON.parse(localStorage.getItem('ge_user') || 'null'),
  vacPage: 0,
  recsPage: 0,
  vacSort: 'score,desc',
  vacRemote: false,
  vacEmployer: '',
  trajWeek: '',
  trajStatus: '',
  onboardingStep: 1,
  onboardingSkills: new Map(),
  onboardingInterests: new Set(),
  onboardingStrengths: new Set(),
  areas: [],
  trajectoryVacancies: [],
  trajectoryLoaderTimer: null,
  trajectoryLoaderStartedAt: 0,
};

const skillPresets = [
  ['Коммуникация с клиентами', 2], ['Работа с документами', 2], ['Excel', 2], ['Грамотная речь', 3],
  ['Продажи', 1], ['Переговоры', 1], ['Администрирование', 1], ['Работа в команде', 3],
  ['1С:Предприятие', 1], ['SQL', 1], ['Аналитика требований', 1], ['Тестирование', 1],
  ['Java', 1], ['Spring Boot', 1], ['JavaScript', 1], ['Git', 1],
];

const interestOptions = [
  ['clients', 'Общаться с людьми', ['Коммуникация с клиентами', 2], ['Грамотная речь', 2]],
  ['sales', 'Продавать и убеждать', ['Продажи', 1], ['Переговоры', 1]],
  ['docs', 'Документы и порядок', ['Работа с документами', 2], ['Excel', 1]],
  ['numbers', 'Таблицы и расчёты', ['Excel', 2], ['Аналитика данных', 1]],
  ['systems', 'Бизнес-системы и 1С', ['1С:Предприятие', 1], ['Язык запросов 1С', 1]],
  ['tech', 'Технологии и разработка', ['Git', 1], ['SQL', 1]],
  ['support', 'Помогать пользователям', ['Техническая поддержка', 1], ['Коммуникация с клиентами', 2]],
  ['creative', 'Тексты, визуал, маркетинг', ['Копирайтинг', 1], ['Маркетинг', 1]],
];

const strengthOptions = [
  ['accuracy', 'Внимательность'], ['learning', 'Быстро учусь'], ['empathy', 'Легко общаюсь'],
  ['logic', 'Люблю разбираться'], ['routine', 'Нормально отношусь к рутине'], ['initiative', 'Беру инициативу'],
];

const majorRussianCities = [
  { id: 1, name: 'Москва' },
  { id: 2, name: 'Санкт-Петербург' },
  { id: 4, name: 'Новосибирск' },
  { id: 3, name: 'Екатеринбург' },
  { id: 88, name: 'Казань' },
  { id: 66, name: 'Нижний Новгород' },
  { id: 104, name: 'Челябинск' },
  { id: 78, name: 'Самара' },
  { id: 76, name: 'Омск' },
  { id: 99, name: 'Ростов-на-Дону' },
  { id: 72, name: 'Уфа' },
  { id: 68, name: 'Красноярск' },
  { id: 26, name: 'Воронеж' },
  { id: 53, name: 'Пермь' },
  { id: 54, name: 'Волгоград' },
  { id: 55, name: 'Краснодар' },
  { id: 75, name: 'Саратов' },
  { id: 24, name: 'Тюмень' },
  { id: 95, name: 'Тольятти' },
  { id: 112, name: 'Ижевск' },
  { id: 98, name: 'Барнаул' },
  { id: 96, name: 'Ульяновск' },
  { id: 58, name: 'Иркутск' },
  { id: 77, name: 'Хабаровск' },
  { id: 44, name: 'Ярославль' },
  { id: 79, name: 'Владивосток' },
  { id: 92, name: 'Махачкала' },
  { id: 59, name: 'Томск' },
  { id: 51, name: 'Оренбург' },
  { id: 56, name: 'Кемерово' },
  { id: 10, name: 'Новокузнецк' },
  { id: 67, name: 'Рязань' },
  { id: 11, name: 'Астрахань' },
  { id: 21, name: 'Пенза' },
  { id: 90, name: 'Липецк' },
  { id: 36, name: 'Киров' },
  { id: 91, name: 'Чебоксары' },
  { id: 63, name: 'Калининград' },
  { id: 13, name: 'Балашиха' },
  { id: 22, name: 'Курск' },
  { id: 32, name: 'Севастополь' },
  { id: 35, name: 'Сочи' },
  { id: 80, name: 'Улан-Удэ' },
  { id: 15, name: 'Ставрополь' },
  { id: 23, name: 'Тверь' },
  { id: 69, name: 'Магнитогорск' },
  { id: 48, name: 'Иваново' },
  { id: 49, name: 'Брянск' },
  { id: 100, name: 'Белгород' },
  { id: 84, name: 'Сургут' },
];

// ──────────── API HELPER ────────────
async function api(method, path, body, params) {
  const url = new URL(BASE + path);
  if (params) Object.entries(params).forEach(([k,v]) => v !== '' && v != null && url.searchParams.set(k, v));
  const headers = {};
  if (state.token) headers['Authorization'] = `Bearer ${state.token}`;
  const opts = { method, headers, credentials: 'include' };
  if (body !== undefined && body !== null) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(url, opts);
  if (res.status === 204) return null;
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch (_) { json = text; }
  if (!res.ok) throw new Error(json?.error?.message || `HTTP ${res.status}`);
  return json;
}

// ──────────── TOAST ────────────
let toastTimer;
function toast(msg, type = 'success') {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = `toast ${type}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 3500);
}

// ──────────── SCREEN SWITCH ────────────
async function showApp() {
  document.getElementById('screen-auth').classList.remove('active');
  document.getElementById('screen-onboarding').classList.remove('active');
  try {
    const profile = await api('GET', '/profile');
    if (!profile.data.onboardingCompleted) {
      showOnboarding(profile.data);
      return;
    }
  } catch(e) {
    toast('Ошибка загрузки профиля: ' + e.message, 'error');
  }
  document.getElementById('screen-app').classList.add('active');
  loadDashboard();
}

async function loadAreas() {
  if (state.areas.length) return state.areas;
  state.areas = majorRussianCities;
  fillAreaSelect('on-area', 88);
  fillAreaSelect('imp-area', 88);
  fillAreaSelect('prof-area', 88);
  return state.areas;
}

function fillAreaSelect(id, selected) {
  const el = document.getElementById(id);
  if (!el) return;
  const current = selected ?? el.value;
  el.innerHTML = state.areas.map(a =>
    `<option value="${a.id}" ${String(a.id) === String(current) ? 'selected' : ''}>${esc(a.name)}</option>`
  ).join('');
}

function showAuth() {
  document.getElementById('screen-app').classList.remove('active');
  document.getElementById('screen-onboarding').classList.remove('active');
  document.getElementById('screen-auth').classList.add('active');
}

function showOnboarding(profile) {
  document.getElementById('screen-app').classList.remove('active');
  document.getElementById('screen-auth').classList.remove('active');
  document.getElementById('screen-onboarding').classList.add('active');
  loadAreas().then(() => fillAreaSelect('on-area', profile?.areaId || 88));
  if (profile) fillOnboardingProfile(profile);
  renderChoiceOptions();
  renderSkillPresets();
  renderSelectedOnboardingSkills();
  setOnboardingStep(1);
}

// ──────────── NAV ────────────
document.querySelectorAll('.nav-link').forEach(a => {
  a.addEventListener('click', e => {
    e.preventDefault();
    const page = a.dataset.page;
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    a.classList.add('active');
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.getElementById('page-' + page).classList.add('active');
    // Lazy load
    if (page === 'vacancies') loadVacancies();
    if (page === 'recommendations') loadRecs();
    if (page === 'trajectory') {
      loadTrajectory();
      const pendingId = state.pendingTrajVacancyId || null;
      state.pendingTrajVacancyId = null;
      loadTrajectoryVacancyOptions(pendingId);
    }
    if (page === 'skills') loadSkills();
    if (page === 'profile') loadProfile();
  });
});

// ──────────── AUTH TAB SWITCH ────────────
document.querySelectorAll('.auth-tab').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.auth-tab').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
    document.getElementById('form-' + btn.dataset.tab).classList.add('active');
    document.getElementById('auth-error').classList.add('hidden');
  });
});

function showAuthError(msg) {
  const el = document.getElementById('auth-error');
  el.textContent = msg;
  el.classList.remove('hidden');
}

// ──────────── LOGIN ────────────
document.getElementById('btn-login').addEventListener('click', async () => {
  const email = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;
  try {
    const res = await api('POST', '/auth/login', { email, password });
    state.token = res.data.accessToken;
    state.user  = res.data.user;
    localStorage.setItem('ge_token', state.token);
    localStorage.setItem('ge_user', JSON.stringify(state.user));
    showApp();
  } catch(e) { showAuthError(e.message); }
});

// ──────────── REGISTER ────────────
document.getElementById('btn-register').addEventListener('click', async () => {
  const name     = document.getElementById('reg-name').value.trim();
  const email    = document.getElementById('reg-email').value.trim();
  const password = document.getElementById('reg-password').value;
  try {
    await api('POST', '/auth/register', { name, email, password });
    toast('Аккаунт создан. Проверь почту и подтверди email.');
    document.querySelector('[data-tab="login"]').click();
    document.getElementById('login-email').value = email;
  } catch(e) { showAuthError(e.message); }
});

// ──────────── RESET PASSWORD ────────────
document.getElementById('btn-reset-link').addEventListener('click', () => {
  document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
  document.getElementById('form-reset').classList.add('active');
});
document.getElementById('btn-back-login').addEventListener('click', () => {
  document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
  document.getElementById('form-login').classList.add('active');
});
document.getElementById('btn-reset-send').addEventListener('click', async () => {
  const email = document.getElementById('reset-email').value.trim();
  try {
    await api('POST', '/auth/password/reset-request', { email });
    toast('Ссылка отправлена на почту ✓');
    document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
    document.getElementById('form-login').classList.add('active');
  } catch(e) { showAuthError(e.message); }
});

// ──────────── LOGOUT ────────────
document.getElementById('btn-logout').addEventListener('click', async () => {
  try { await api('POST', '/auth/logout'); } catch(_) {}
  state.token = null;
  state.user  = null;
  localStorage.removeItem('ge_token');
  localStorage.removeItem('ge_user');
  showAuth();
});

// ──────────────────────────────
//  ONBOARDING
// ──────────────────────────────
function fillOnboardingProfile(profile) {
  document.getElementById('on-goal').value = profile.goal || '';
  document.getElementById('on-level').value = profile.level || 'JUNIOR';
  document.getElementById('on-city').value = profile.city || '';
  fillAreaSelect('on-area', profile.areaId || 88);
  document.getElementById('on-salary-min').value = profile.salaryMin || '';
  document.getElementById('on-remote').checked = !!profile.remote;
}

function renderChoiceOptions() {
  const interests = document.getElementById('on-interest-options');
  const strengths = document.getElementById('on-strength-options');
  if (interests) {
    interests.innerHTML = interestOptions.map(([id, label]) => `
      <button class="choice-chip ${state.onboardingInterests.has(id) ? 'active' : ''}" onclick="toggleOnboardingInterest('${id}')">${esc(label)}</button>
    `).join('');
  }
  if (strengths) {
    strengths.innerHTML = strengthOptions.map(([id, label]) => `
      <button class="choice-chip ${state.onboardingStrengths.has(id) ? 'active' : ''}" onclick="toggleOnboardingStrength('${id}')">${esc(label)}</button>
    `).join('');
  }
}

function toggleOnboardingInterest(id) {
  if (state.onboardingInterests.has(id)) {
    state.onboardingInterests.delete(id);
  } else {
    state.onboardingInterests.add(id);
    const option = interestOptions.find(([optionId]) => optionId === id);
    if (option) option.slice(2).forEach(([skill, level]) => state.onboardingSkills.set(skill, level));
  }
  renderChoiceOptions();
  renderSkillPresets();
  renderSelectedOnboardingSkills();
}

function toggleOnboardingStrength(id) {
  if (state.onboardingStrengths.has(id)) state.onboardingStrengths.delete(id);
  else state.onboardingStrengths.add(id);
  renderChoiceOptions();
}

function setOnboardingStep(step) {
  state.onboardingStep = Math.min(Math.max(step, 1), 3);
  document.querySelectorAll('.onboarding-panel').forEach(panel => {
    panel.classList.toggle('active', Number(panel.dataset.panel) === state.onboardingStep);
  });
  document.querySelectorAll('.onboarding-step').forEach(item => {
    item.classList.toggle('active', Number(item.dataset.onStep) === state.onboardingStep);
    item.classList.toggle('done', Number(item.dataset.onStep) < state.onboardingStep);
  });
  document.getElementById('on-prev').disabled = state.onboardingStep === 1;
  document.getElementById('on-next').classList.toggle('hidden', state.onboardingStep === 3);
  document.getElementById('on-finish').classList.toggle('hidden', state.onboardingStep !== 3);
}

function renderSkillPresets() {
  const el = document.getElementById('on-skill-presets');
  el.innerHTML = skillPresets.map(([name, level]) => `
    <button class="skill-preset ${state.onboardingSkills.has(name) ? 'active' : ''}" onclick="toggleOnboardingSkill('${name}', ${level})">
      <span>${esc(name)}</span>
      <small>${level}/5</small>
    </button>
  `).join('');
}

function toggleOnboardingSkill(name, level = 1) {
  if (state.onboardingSkills.has(name)) {
    state.onboardingSkills.delete(name);
  } else {
    state.onboardingSkills.set(name, level);
  }
  renderSkillPresets();
  renderSelectedOnboardingSkills();
}

function renderSelectedOnboardingSkills() {
  const el = document.getElementById('on-selected-skills');
  const items = Array.from(state.onboardingSkills.entries());
  if (!items.length) {
    el.innerHTML = '<div class="empty-state compact">Выбери навыки выше или добавь свой</div>';
    return;
  }
  el.innerHTML = items.map(([name, level]) => `
    <div class="selected-skill">
      <span>${esc(name)}</span>
      <input type="number" min="0" max="5" value="${level}" onchange="setOnboardingSkillLevel('${esc(name)}', this.value)" />
      <button class="btn-sm" onclick="toggleOnboardingSkill('${esc(name)}')">Убрать</button>
    </div>
  `).join('');
}

function setOnboardingSkillLevel(name, value) {
  const level = Math.min(Math.max(parseInt(value, 10) || 0, 0), 5);
  state.onboardingSkills.set(name, level);
  renderSelectedOnboardingSkills();
}

document.getElementById('on-prev').addEventListener('click', () => setOnboardingStep(state.onboardingStep - 1));
document.getElementById('on-next').addEventListener('click', () => setOnboardingStep(state.onboardingStep + 1));
document.getElementById('on-skip-skills').addEventListener('click', () => {
  state.onboardingSkills.clear();
  renderSkillPresets();
  renderSelectedOnboardingSkills();
  setOnboardingStep(3);
});
document.getElementById('on-add-custom-skill').addEventListener('click', () => {
  const name = document.getElementById('on-custom-skill').value.trim();
  if (!name) return;
  state.onboardingSkills.set(name, 1);
  document.getElementById('on-custom-skill').value = '';
  renderSkillPresets();
  renderSelectedOnboardingSkills();
});

document.getElementById('on-finish').addEventListener('click', async () => {
  if (!validateOnboarding()) return;
  const statusEl = document.getElementById('onboarding-status');
  const finishBtn = document.getElementById('on-finish');
  statusEl.className = 'import-status accepted';
  statusEl.classList.remove('hidden');
  statusEl.textContent = '🤖 ИИ сохраняет ваш профиль и навыки...';
  finishBtn.disabled = true;

  const profileBody = {
    goal: buildOnboardingGoal(),
    level: document.getElementById('on-level').value,
    city: document.getElementById('on-city').value.trim(),
    areaId: +document.getElementById('on-area').value || undefined,
    salaryMin: +document.getElementById('on-salary-min').value || undefined,
    salaryMax: undefined,
    remote: document.getElementById('on-remote').checked,
  };
  const trajectoryPrefs = {
    hoursPerWeek: +document.getElementById('on-hours').value,
    weeks: +document.getElementById('on-weeks').value,
    focus: document.getElementById('on-focus').value,
    userRequest: document.getElementById('on-ai-request').value.trim(),
  };

  try {
    await api('PUT', '/profile', profileBody);
    for (const [name, level] of state.onboardingSkills.entries()) {
      await api('POST', '/profile/skills', { name, level }).catch(e => {
        if (!e.message.toLowerCase().includes('уже')) throw e;
      });
    }

    if (document.getElementById('on-import').checked) {
      statusEl.textContent = '🔍 Ищу вакансии (HH.ru → SuperJob.ru)...';
      await api('POST', '/vacancies/import', undefined, {
        role: profileBody.goal,
        areaId: profileBody.areaId || 1,
        pages: 3,
      });
    }

    statusEl.textContent = '🤖 ИИ анализирует вакансии и строит рекомендации...';
    await api('POST', '/recommendations/rebuild').catch(() => null);
    await generateInitialTrajectoryFromRecommendations(statusEl, trajectoryPrefs);

    document.getElementById('screen-onboarding').classList.remove('active');
    document.getElementById('screen-app').classList.add('active');
    toast('Готово! ИИ создал персональный план');
    loadDashboard();
  } catch(e) {
    statusEl.className = 'import-status error';
    statusEl.textContent = 'Ошибка: ' + e.message;
  } finally {
    finishBtn.disabled = false;
  }
});

function validateOnboarding() {
  const role = document.getElementById('on-goal').value.trim();
  if (!role && state.onboardingInterests.size === 0) {
    setOnboardingStep(1);
    toast('Укажи направление или выбери хотя бы один интерес', 'error');
    return false;
  }
  const hours = +document.getElementById('on-hours').value;
  const weeks = +document.getElementById('on-weeks').value;
  if (!hours || hours < 5 || hours > 40) {
    setOnboardingStep(3);
    toast('Часы в неделю должны быть от 5 до 40', 'error');
    return false;
  }
  if (!weeks || weeks < 2 || weeks > 8) {
    setOnboardingStep(3);
    toast('Количество недель должно быть от 2 до 8', 'error');
    return false;
  }
  return true;
}

function buildOnboardingGoal() {
  const role = document.getElementById('on-goal').value.trim();
  const interestLabels = interestOptions
      .filter(([id]) => state.onboardingInterests.has(id))
      .map(([, label]) => label);
  const strengthLabels = strengthOptions
      .filter(([id]) => state.onboardingStrengths.has(id))
      .map(([, label]) => label);
  const parts = [];
  if (role) parts.push(`Желаемая роль: ${role}`);
  if (interestLabels.length) parts.push(`Интересы: ${interestLabels.join(', ')}`);
  if (strengthLabels.length) parts.push(`Сильные стороны: ${strengthLabels.join(', ')}`);
  return parts.join('. ') || 'Подобрать подходящую вакансию по навыкам и предпочтениям';
}

async function generateInitialTrajectoryFromRecommendations(statusEl, trajectoryPrefs) {
  statusEl.textContent = '🤖 ИИ строит персональную траекторию обучения...';
  const recs = await api('GET', '/recommendations', null, { page: 0, limit: 1, minScore: 0 }).catch(() => null);
  const first = recs?.data?.[0];
  if (!first?.vacancyId) {
    statusEl.textContent = 'Рекомендации не найдены — траектория будет создана после импорта вакансий.';
    return;
  }
  statusEl.textContent = `🤖 ИИ анализирует вакансию «${first.vacancy?.title || ''}» и строит план...`;
  await api('POST', '/trajectory/generate', trajectoryPrefs, { vacancyId: first.vacancyId }).catch(() => null);
  statusEl.textContent = '✓ Персональная траектория создана ИИ';
}

// ──────────────────────────────
//  DASHBOARD
// ──────────────────────────────
async function loadDashboard() {
  if (state.user) {
    document.getElementById('dash-greeting').textContent =
      `Привет, ${state.user.name || state.user.email} 👋`;
  }
  try {
    const res = await api('GET', '/dashboard/overview');
    const d = res.data;

    document.getElementById('stat-match').textContent      = (d.matchPercent ?? '—') + '%';
    document.getElementById('stat-vacancies').textContent  = d.totalVacancies ?? '—';

    // Profile stats for nav display
    const ps = await api('GET', '/profile/stats').catch(() => null);
    if (ps) {
      document.getElementById('stat-skills').textContent    = ps.data.coveredSkills + '/' + ps.data.totalSkills;
      document.getElementById('stat-trajectory').textContent = ps.data.trajectoryProgress + '%';
    }

    renderTopSkills(d.topSkills || []);
    renderWeekPlan(d.weekPlan || []);
    renderTopRecs(d.topRecommendations || []);
  } catch(e) {
    toast('Ошибка загрузки дашборда: ' + e.message, 'error');
  }
}

function renderTopSkills(skills) {
  const el = document.getElementById('top-skills-list');
  el.innerHTML = skills.map(s => `
    <div class="skill-bar-item">
      <div class="skill-bar-label">
        <span>${esc(s.name)}</span>
        <span style="font-family:var(--mono);font-size:11px;color:var(--text-muted)">${Math.round(s.frequency*100)}%</span>
      </div>
      <div class="skill-bar-track">
        <div class="skill-bar-fill" style="width:${s.frequency*100}%"></div>
      </div>
    </div>
  `).join('') || '<div class="empty-state">Нет данных</div>';
}

function renderGaps(gaps) {
  const el = document.getElementById('skill-gaps-list');
  el.innerHTML = gaps.map(g => `
    <div class="gap-item ${g.importance === 'CRITICAL' ? 'critical' : 'high'}">
      <div>
        <div class="gap-name">${esc(g.name)}</div>
        <div class="gap-meta">Уровень: ${g.userLevel}→${g.requiredLevel} · ${g.affectedPercent || 0}% вакансий</div>
      </div>
      <span class="gap-badge badge-${g.importance === 'CRITICAL' ? 'critical' : 'high'}">${g.importance}</span>
    </div>
  `).join('') || '<div class="empty-state">Пробелов нет 🎉</div>';
}

function renderWeekPlan(plan) {
  const el = document.getElementById('week-plan-list');
  el.innerHTML = plan.map(s => `
    <div class="week-item">
      <div class="week-item-title">${esc(s.title)}</div>
      <div class="week-item-meta">${statusLabel(s.status)} · ~${s.estimatedHours || 0}ч</div>
      <div class="week-item-bar">
        <div class="week-item-fill" style="width:${s.progressPercent || 0}%"></div>
      </div>
    </div>
  `).join('') || '<div class="empty-state">Нет задач на эту неделю</div>';
}

function renderTopRecs(recs) {
  const el = document.getElementById('top-recs-list');
  el.innerHTML = recs.map(r => `
    <div class="rec-item" onclick="openVacancy('${r.vacancyId}')">
      <div class="rec-title">${esc(r.title || r.vacancy?.title || '')}</div>
      <div class="rec-employer">${esc(r.employer || r.vacancy?.employer || '')}${r.vacancy?.area ? ' · ' + esc(r.vacancy.area) : ''}</div>
      <div class="rec-score-row">
        <span class="rec-score">${r.score}%</span>
        <span class="rec-salary">${salaryStr(r.salaryMin || r.vacancy?.salaryMin, r.salaryMax || r.vacancy?.salaryMax)}</span>
      </div>
      ${r.gaps?.length ? `<div class="rec-gaps">${r.gaps.map(g => `<span class="tag">${esc(g.skill || g)}</span>`).join('')}</div>` : ''}
    </div>
  `).join('') || '<div class="empty-state">Нет рекомендаций</div>';
}

// Rebuild
document.getElementById('btn-rebuild').addEventListener('click', async () => {
  const btn = document.getElementById('btn-rebuild');
  btn.disabled = true;
  btn.textContent = '↻ Пересчитываю...';
  try {
    const res = await api('POST', '/recommendations/rebuild');
    toast('Пересчёт запущен (job: ' + res.data.jobId.slice(0,8) + '...)');
    setTimeout(() => loadDashboard(), 2000);
  } catch(e) { toast(e.message, 'error'); }
  finally { btn.disabled = false; btn.textContent = '↻ Пересчитать'; }
});

// Clear my vacancies
document.getElementById('btn-clear-vacancies').addEventListener('click', async () => {
  if (!confirm('Удалить все импортированные вакансии и рекомендации? Это действие нельзя отменить.')) return;
  const btn = document.getElementById('btn-clear-vacancies');
  btn.disabled = true;
  try {
    await api('DELETE', '/vacancies/clear');
    toast('Вакансии удалены');
    loadVacancies();
    loadDashboard();
  } catch(e) { toast(e.message, 'error'); }
  finally { btn.disabled = false; }
});

// Admin: delete all vacancies
document.getElementById('btn-admin-clear-all').addEventListener('click', async () => {
  if (!confirm('ВНИМАНИЕ: удалить ВСЕ вакансии для ВСЕХ пользователей? Это нельзя отменить.')) return;
  if (!confirm('Вы уверены? Все данные о вакансиях будут удалены безвозвратно.')) return;
  const btn = document.getElementById('btn-admin-clear-all');
  btn.disabled = true;
  try {
    await api('DELETE', '/admin/vacancies/all');
    toast('Все вакансии удалены');
    loadDashboard();
  } catch(e) { toast(e.message, 'error'); }
  finally { btn.disabled = false; }
});

// ──────────────────────────────
//  VACANCIES
// ──────────────────────────────
async function loadVacancies() {
  const el = document.getElementById('vacancies-list');
  el.innerHTML = '<div class="loading">Загрузка...</div>';
  try {
    const res = await api('GET', '/vacancies', null, {
      page: state.vacPage,
      size: 12,
      sort: state.vacSort,
      remote: state.vacRemote || undefined,
      employer: state.vacEmployer || undefined,
    });
    renderVacancyCards(res.data, el);
    renderPagination(res.pagination, 'vac-pagination', p => { state.vacPage = p; loadVacancies(); });
  } catch(e) { el.innerHTML = `<div class="empty-state">${e.message}</div>`; }
}

function renderVacancyCards(items, el) {
  if (!items?.length) { el.innerHTML = '<div class="empty-state"><div class="empty-state-icon">◻</div>Вакансий не найдено</div>'; return; }
  el.innerHTML = items.map(v => `
    <div class="vac-card" onclick="openVacancy('${v.id}')">
      <div class="vac-header">
        <div class="vac-title">${esc(v.title)}</div>
        <div class="vac-score">${v.score}%</div>
      </div>
      <div class="vac-employer">${esc(v.employer || '')} ${v.area ? '· ' + esc(v.area) : ''}</div>
      <div class="vac-meta">
        ${v.remote ? '<span class="vac-badge badge-remote">Remote</span>' : ''}
        ${v.salaryMin || v.salaryMax ? `<span class="vac-badge badge-salary">${salaryStr(v.salaryMin, v.salaryMax)}</span>` : ''}
      </div>
      ${v.gaps?.length ? `<div class="rec-gaps">${v.gaps.slice(0,4).map(g=>`<span class="tag">${esc(g)}</span>`).join('')}</div>` : ''}
      <div class="vac-actions" onclick="event.stopPropagation()">
        <button class="btn-sm" onclick="saveVacancy('${v.id}', this)">♡ Сохранить</button>
        <button class="btn-sm" onclick="openVacancy('${v.id}')">Подробнее →</button>
      </div>
    </div>
  `).join('');
}

async function saveVacancy(id, btn) {
  try {
    await api('POST', `/vacancies/${id}/save`);
    btn.textContent = '♥ Сохранено';
    btn.style.color = 'var(--accent)';
    toast('Вакансия сохранена');
  } catch(e) {
    if (e.message.includes('409') || e.message.toLowerCase().includes('conflict') || e.message.toLowerCase().includes('уже')) {
      toast('Уже сохранена', 'error');
    } else { toast(e.message, 'error'); }
  }
}

// Filters
document.getElementById('btn-vac-load').addEventListener('click', () => {
  state.vacPage = 0;
  state.vacSort = document.getElementById('vac-sort').value;
  state.vacRemote = document.getElementById('vac-remote').checked;
  state.vacEmployer = document.getElementById('vac-search').value.trim();
  loadVacancies();
});
document.getElementById('vac-sort').addEventListener('change', () => {
  state.vacSort = document.getElementById('vac-sort').value;
  state.vacPage = 0;
  loadVacancies();
});

// Import
document.getElementById('btn-import-open').addEventListener('click', () => {
  document.getElementById('import-panel').classList.toggle('hidden');
  loadAreas();
});
document.getElementById('btn-import-close').addEventListener('click', () => {
  document.getElementById('import-panel').classList.add('hidden');
});
document.getElementById('btn-import-start').addEventListener('click', async () => {
  const role  = document.getElementById('imp-role').value.trim();
  const area  = document.getElementById('imp-area').value;
  const exp   = document.getElementById('imp-exp').value;
  const pages = document.getElementById('imp-pages').value;
  const statusEl = document.getElementById('import-status');
  statusEl.classList.remove('hidden', 'error');
  statusEl.className = 'import-status accepted';
  statusEl.textContent = 'Запускаю импорт...';
  try {
    const res = await api('POST', '/vacancies/import', undefined, {
      role,
      areaId: area,
      experience: exp || undefined,
      pages,
    });
    const job = res.data;
    statusEl.textContent = `Принято. Job ID: ${job.jobId.slice(0,12)}... · ~${job.estimatedCount} вакансий`;
    toast('Импорт запущен');
    pollImport(job.jobId, statusEl);
  } catch(e) {
    statusEl.className = 'import-status error';
    statusEl.textContent = 'Ошибка: ' + e.message;
  }
});

async function pollImport(jobId, statusEl) {
  const max = 40; let i = 0;
  const sourceEmoji = { 'HH.ru': '🔶', 'SuperJob.ru': '🟢' };
  const tick = async () => {
    if (++i > max) return;
    try {
      const r = await api('GET', `/vacancies/import/${jobId}`);
      const d = r.data;
      const src = d.message?.match(/Источник: (.+)/)?.[1] || '';
      const srcTag = src ? ` ${sourceEmoji[src] || '·'} ${src}` : '';
      statusEl.textContent = `${d.status === 'RUNNING' ? '⏳' : d.status === 'COMPLETED' ? '✓' : '✕'} Импортировано: ${d.imported ?? 0} · Ошибки: ${d.errors ?? 0}${srcTag}${d.message && !src ? ' · ' + d.message : ''}`;
      if (d.status !== 'COMPLETED' && d.status !== 'FAILED') setTimeout(tick, 2500);
      else if (d.status === 'COMPLETED') {
        statusEl.textContent = `✓ Импортировано ${d.imported} вакансий${srcTag}. Нажми «Пересчитать» для рекомендаций.`;
        toast(`Импортировано ${d.imported} вакансий`);
        loadVacancies();
        setTimeout(() => {
          document.getElementById('import-panel').classList.add('hidden');
        }, 4000);
      } else {
        statusEl.className = 'import-status error';
      }
    } catch(_) {}
  };
  setTimeout(tick, 2500);
}

// ──────────────────────────────
//  VACANCY MODAL
// ──────────────────────────────
async function openVacancy(id) {
  const overlay = document.getElementById('vacancy-modal');
  const content = document.getElementById('modal-content');
  overlay.classList.remove('hidden');
  content.innerHTML = '<div class="loading">Загрузка...</div>';
  try {
    const [vr, rr] = await Promise.allSettled([
      api('GET', `/vacancies/${id}`),
      api('GET', `/recommendations/${id}`),
    ]);
    const v = vr.status === 'fulfilled' ? vr.value.data : null;
    const r = rr.status === 'fulfilled' ? rr.value.data : null;
    if (!v) { content.innerHTML = '<div class="empty-state">Не удалось загрузить</div>'; return; }

    content.innerHTML = `
      <div class="vac-modal-title">${esc(v.title)}</div>
      <div class="vac-modal-employer">${esc(v.employer || '')} ${v.area ? '· ' + esc(v.area) : ''}</div>
      <div class="vac-modal-grid">
        <div class="vac-modal-stat"><div class="vac-modal-stat-label">Match</div><div class="vac-modal-stat-val" style="color:var(--accent)">${v.score || r?.score || '—'}%</div></div>
        <div class="vac-modal-stat"><div class="vac-modal-stat-label">Зарплата</div><div class="vac-modal-stat-val">${salaryStr(v.salaryMin, v.salaryMax)}</div></div>
        <div class="vac-modal-stat"><div class="vac-modal-stat-label">Формат</div><div class="vac-modal-stat-val">${v.remote ? 'Удалённо' : 'Офис'}</div></div>
        <div class="vac-modal-stat"><div class="vac-modal-stat-label">SBERT score</div><div class="vac-modal-stat-val">${v.sbertScore || r?.sbertScore || '—'}</div></div>
      </div>
      ${v.description ? `<div style="margin-bottom:16px"><div class="card-header" style="margin-bottom:8px">Описание</div><p style="font-size:13px;color:var(--text-muted);line-height:1.7">${esc(v.description).slice(0,600)}${v.description.length>600?'…':''}</p></div>` : ''}
      ${v.skills?.length ? `
        <div class="card-header" style="margin-bottom:8px">Навыки</div>
        <div class="skill-matrix">
          ${v.skills.map(s => `
            <div class="skill-matrix-row ${s.covered ? 'covered' : 'missing'}">
              <span class="skill-matrix-name">${esc(s.name)}</span>
              <span class="skill-matrix-levels">${s.userLevel}/${s.requiredLevel}</span>
              <span class="skill-matrix-imp imp-${s.importance === 'MUST_HAVE' ? 'must' : 'nice'}">${s.importance === 'MUST_HAVE' ? 'must' : 'nice'}</span>
            </div>
          `).join('')}
        </div>
      ` : ''}
      ${r?.gaps?.length ? `
        <div class="card-header" style="margin:16px 0 8px">Пробелы</div>
        <div class="gaps-list">
          ${r.gaps.map(g => `<div class="gap-item ${g.importance==='CRITICAL'?'critical':'high'}">
            <div><div class="gap-name">${esc(g.skill)}</div><div class="gap-meta">${g.affectedVacanciesPercent||0}% вакансий требуют этот навык</div></div>
            <span class="gap-badge badge-${g.importance==='CRITICAL'?'critical':'high'}">${g.importance}</span>
          </div>`).join('')}
        </div>
      ` : ''}
      <div style="display:flex;gap:10px;margin-top:20px">
        ${v.url ? `<a href="${v.url}" target="_blank" class="btn-primary" style="text-decoration:none">Открыть на ${esc(vacancySource(v.hhId))} ↗</a>` : ''}
        <button class="btn-accent" onclick="saveVacancy('${v.id}', this)">♡ Сохранить</button>
        <button class="btn-ghost" onclick='generateTrajModal(${v.id}, ${JSON.stringify(v.title||"")}, ${JSON.stringify(v.employer||"")}, ${JSON.stringify(v.area||"")})'>→ Создать траекторию</button>
      </div>
    `;
  } catch(e) {
    content.innerHTML = `<div class="empty-state">${e.message}</div>`;
  }
}

function generateTrajModal(vacancyId, title, employer, area) {
  closeModal();
  state.pendingTrajVacancyId = vacancyId;
  state.pendingTrajVacancy = { id: vacancyId, title: title || String(vacancyId), employer: employer || '', area: area || '' };
  document.querySelector('[data-page="trajectory"]').click();
  document.getElementById('traj-gen-panel').classList.remove('hidden');
}

function vacancySource(hhId) {
  if (!hhId) return 'HH.ru';
  if (hhId.startsWith('sj_')) return 'SuperJob.ru';
  return 'HH.ru';
}

document.getElementById('modal-close').addEventListener('click', closeModal);
document.getElementById('vacancy-modal').addEventListener('click', e => {
  if (e.target === e.currentTarget) closeModal();
});
function closeModal() { document.getElementById('vacancy-modal').classList.add('hidden'); }

async function loadRecs() {
  const el = document.getElementById('recs-full-list');
  el.innerHTML = '<div class="loading">Загрузка...</div>';
  try {
    const res = await api('GET', '/recommendations', null, { page: state.recsPage, limit: 12, sortBy: 'score' });
    if (!res.data?.length) {
      el.innerHTML = '<div class="empty-state"><div class="empty-state-icon">◈</div>Нет рекомендаций. Сначала импортируй вакансии и нажми «Пересчитать».</div>';
      return;
    }
    el.innerHTML = res.data.map(r => `
      <div class="vac-card" onclick="openVacancy('${r.vacancyId}')">
        <div class="vac-header">
          <div class="vac-title">${esc(r.vacancy?.title || '')}</div>
          <div class="vac-score" title="AI-оценка соответствия">${r.score}%</div>
        </div>
        <div class="vac-employer">${esc(r.vacancy?.employer || '')}${r.vacancy?.area ? ' · ' + esc(r.vacancy.area) : ''}</div>
        <div class="vac-meta">
          ${r.vacancy?.salaryMin || r.vacancy?.salaryMax ? `<span class="vac-badge badge-salary">${salaryStr(r.vacancy.salaryMin, r.vacancy.salaryMax)}</span>` : ''}
          <span class="vac-badge" style="background:rgba(99,102,241,.15);color:#818cf8;font-size:10px">ИИ</span>
        </div>
        <div class="rec-gaps">${(r.gaps||[]).map(g=>`<span class="tag">${esc(g.skill)}</span>`).join('')}</div>
        <div style="font-family:var(--mono);font-size:10px;color:var(--text-dim);margin-top:8px">
          Семантика: ${r.sbertScore||'—'}% · Навыки: ${r.skillsCoverage||'—'}%
        </div>
        <div id="ai-explain-${r.vacancyId}" style="margin-top:8px" onclick="event.stopPropagation()">
          <button class="btn-sm" onclick="loadAiExplain('${r.vacancyId}')">🤖 Почему ИИ рекомендует?</button>
        </div>
      </div>
    `).join('');
    renderPagination(res.pagination, 'recs-pagination', p => { state.recsPage = p; loadRecs(); });
  } catch(e) { el.innerHTML = `<div class="empty-state">${e.message}</div>`; }
}

async function loadAiExplain(vacancyId) {
  const el = document.getElementById('ai-explain-' + vacancyId);
  if (!el) return;
  el.innerHTML = '<span style="font-size:12px;color:var(--text-muted)">🤖 Ollama анализирует...</span>';
  try {
    const res = await api('GET', `/recommendations/${vacancyId}/explain`);
    const d = res.data;
    const icon = d.aiGenerated ? '🤖' : '📊';
    el.innerHTML = `
      <div style="font-size:12px;color:var(--text-muted);line-height:1.6;padding:10px 12px;background:var(--card-bg);border-radius:8px;border-left:3px solid var(--accent);margin-top:6px">
        ${icon} <em>${esc(d.explanation)}</em>
      </div>
    `;
  } catch(e) {
    el.innerHTML = `<span style="font-size:11px;color:var(--danger)">${esc(e.message)}</span>`;
  }
}

// ──────────────────────────────
//  TRAJECTORY
// ──────────────────────────────
async function loadTrajectory() {
  loadAiStatus();
  try {
    const res = await api('GET', '/trajectory');
    const t = res.data;
    document.getElementById('traj-meta').textContent =
      `${esc(t.vacancyTitle || '')} · Неделя ${t.currentWeek}/${t.totalWeeks}`;
    const wrap = document.getElementById('traj-progress-wrap');
    wrap.classList.remove('hidden');
    document.getElementById('traj-progress-fill').style.width = t.progressPercent + '%';
    document.getElementById('traj-progress-label').textContent = t.progressPercent + '% (' + t.completedSteps + '/' + t.totalSteps + ')';

    // Fill week options
    const weekSel = document.getElementById('traj-week-filter');
    weekSel.innerHTML = '<option value="">Все недели</option>';
    for (let w = 1; w <= t.totalWeeks; w++) {
      weekSel.innerHTML += `<option value="${w}">Неделя ${w}</option>`;
    }
    document.getElementById('traj-filter').style.display = 'flex';
    loadTrajectorySteps();
  } catch(_) {
    document.getElementById('traj-meta').textContent = 'Нет активной траектории';
    document.getElementById('traj-progress-wrap').classList.add('hidden');
    document.getElementById('traj-filter').style.display = 'none';
    document.getElementById('trajectory-steps-list').innerHTML =
      '<div class="empty-state"><div class="empty-state-icon">→</div>Создай траекторию по вакансии</div>';
  }
}

async function loadAiStatus() {
  const el = document.getElementById('ai-status');
  if (!el) return;
  el.textContent = 'Ollama: проверка...';
  el.className = 'ai-status';
  try {
    const res = await api('GET', '/trajectory/ai/status');
    const s = res.data;
    el.textContent = s.available
      ? `Ollama: ${s.model} доступна`
      : `Ollama: недоступна (${s.message})`;
    el.classList.add(s.available ? 'ok' : 'fail');
  } catch(e) {
    el.textContent = 'Ollama: статус не получен';
    el.classList.add('fail');
  }
}

async function loadTrajectorySteps() {
  const el = document.getElementById('trajectory-steps-list');
  el.innerHTML = '<div class="loading">Загрузка шагов...</div>';
  try {
    const res = await api('GET', '/trajectory/steps', null, {
      week: state.trajWeek || undefined,
      status: state.trajStatus || undefined,
    });
    renderSteps(res.data, el);
  } catch(e) { el.innerHTML = `<div class="empty-state">${e.message}</div>`; }
}

function renderSteps(steps, el) {
  if (!steps?.length) { el.innerHTML = '<div class="empty-state">Шагов нет</div>'; return; }
  el.innerHTML = steps.map(s => {
    const dotCls = 'dot-' + s.status.toLowerCase().replace('_', '_');
    return `
    <div class="step-card" id="step-${s.id}">
      <div class="step-status-dot ${dotCls}"></div>
      <div class="step-body">
        <div class="step-title">${esc(s.title)}</div>
        ${s.description ? `<div class="step-desc">${esc(s.description)}</div>` : ''}
        <div class="step-meta">
          <span>${esc(s.skillName || '')}</span>
          <span>${typeLabel(s.type)}</span>
          <span>~${s.estimatedHours || 0}ч</span>
          <span>Нед.${s.week}</span>
          ${s.deadline ? `<span>до ${s.deadline}</span>` : ''}
        </div>
        <div class="step-resources" id="live-resources-${s.id}">
          <button class="btn-sm" onclick="loadLiveResources('${s.id}')">Подобрать материалы</button>
        </div>
        ${s.note ? `<div class="step-note">${esc(s.note)}</div>` : ''}
      </div>
      <div class="step-card-progress">
        <div class="step-pct">${s.progressPercent || 0}%</div>
        <div style="margin-top:6px">
          <button class="btn-sm" onclick='openStepModal("${s.id}","${s.status}",${s.progressPercent||0},${JSON.stringify(s.note || '')})'>Изменить</button>
        </div>
      </div>
    </div>
  `}).join('');
}

function renderResourceLink(r) {
  const title = esc(r.title || 'Ресурс');
  if (!r.url) return `<span class="step-resource">${title}</span>`;
  return `<a href="${esc(r.url)}" target="_blank" rel="noopener noreferrer" class="step-resource">${title}</a>`;
}

async function loadLiveResources(stepId) {
  const el = document.getElementById('live-resources-' + stepId);
  if (!el) return;
  el.innerHTML = '<span class="step-resource">Ищу материалы...</span>';
  try {
    const res = await api('GET', `/trajectory/steps/${stepId}/resources/live`);
    el.innerHTML = (res.data || []).map(renderResourceLink).join('') || '<span class="step-resource">Материалы не найдены</span>';
  } catch(e) {
    el.innerHTML = `<span class="step-resource">${esc(e.message)}</span>`;
  }
}

document.getElementById('btn-traj-filter').addEventListener('click', () => {
  state.trajWeek   = document.getElementById('traj-week-filter').value;
  state.trajStatus = document.getElementById('traj-status-filter').value;
  loadTrajectorySteps();
});

// Generate
document.getElementById('btn-traj-gen-open').addEventListener('click', () => {
  document.getElementById('traj-gen-panel').classList.toggle('hidden');
  loadTrajectoryVacancyOptions();
});
document.getElementById('btn-traj-gen-close').addEventListener('click', () => {
  document.getElementById('traj-gen-panel').classList.add('hidden');
});
document.getElementById('btn-traj-generate').addEventListener('click', async () => {
  const vacancyId = document.getElementById('gen-vacancy-id').value.trim();
  const hoursPerWeek = +document.getElementById('gen-hours').value;
  const weeks = +document.getElementById('gen-weeks').value;
  const focus = document.getElementById('gen-focus').value;
  const userRequest = document.getElementById('gen-ai-request').value.trim();
  if (!vacancyId) {
    toast('Выбери вакансию для траектории. Если список пустой, сначала импортируй вакансии.', 'error');
    return;
  }
  if (!hoursPerWeek || hoursPerWeek < 5 || hoursPerWeek > 40) {
    toast('Часы в неделю должны быть от 5 до 40', 'error');
    return;
  }
  if (!weeks || weeks < 2 || weeks > 8) {
    toast('Количество недель должно быть от 2 до 8', 'error');
    return;
  }
  const btn = document.getElementById('btn-traj-generate');
  const originalText = btn.textContent;
  try {
    btn.disabled = true;
    btn.textContent = 'Генерирую...';
    showTrajectoryLoader();
    toast('Генерирую траекторию. Локальная Ollama может отвечать до минуты.');
    await api('POST', '/trajectory/generate', { hoursPerWeek, weeks, focus, userRequest }, { vacancyId });
    toast('Траектория создана!');
    document.getElementById('traj-gen-panel').classList.add('hidden');
    loadTrajectory();
  } catch(e) { toast(e.message, 'error'); }
  finally {
    hideTrajectoryLoader();
    btn.disabled = false;
    btn.textContent = originalText;
  }
});

function showTrajectoryLoader() {
  const loader = document.getElementById('trajectory-loader');
  const text = document.getElementById('trajectory-loader-text');
  const time = document.getElementById('trajectory-loader-time');
  if (!loader || !time || !text) return;
  state.trajectoryLoaderStartedAt = Date.now();
  loader.classList.remove('hidden');
  text.textContent = 'Ollama читает вакансию, навыки и пожелания. Обычно это занимает до минуты.';
  time.textContent = '0 сек';
  clearInterval(state.trajectoryLoaderTimer);
  state.trajectoryLoaderTimer = setInterval(() => {
    const elapsed = Math.floor((Date.now() - state.trajectoryLoaderStartedAt) / 1000);
    time.textContent = elapsed < 60 ? `${elapsed} сек` : `${Math.floor(elapsed / 60)} мин ${elapsed % 60} сек`;
    if (elapsed >= 20 && elapsed < 45) {
      text.textContent = 'Модель формирует шаги и подбирает ресурсы. На CPU это может быть медленно.';
    } else if (elapsed >= 45) {
      text.textContent = 'Если Ollama не успеет, сервер вернёт запасной план по вакансии.';
    }
  }, 1000);
}

function hideTrajectoryLoader() {
  clearInterval(state.trajectoryLoaderTimer);
  state.trajectoryLoaderTimer = null;
  const loader = document.getElementById('trajectory-loader');
  if (loader) loader.classList.add('hidden');
}

async function loadTrajectoryVacancyOptions(selectedId) {
  const select = document.getElementById('gen-vacancy-id');
  if (!select) return;

  // If we came from a vacancy modal we already have the vacancy data — show it immediately
  // so the user can generate a trajectory without waiting for the list to load.
  const pending = state.pendingTrajVacancy || null;
  state.pendingTrajVacancy = null;
  if (selectedId && pending) {
    const label = [pending.title, pending.employer, pending.area].filter(Boolean).join(' · ');
    select.innerHTML = `<option value="${selectedId}" selected>${esc(label)}</option>`;
  } else {
    select.innerHTML = '<option value="">Загрузка вакансий...</option>';
  }

  // Load the full list in the background so the user can also pick a different vacancy
  try {
    const res = await api('GET', '/vacancies', null, { page: 0, size: 50, sort: 'fetchedAt,desc' });
    state.trajectoryVacancies = res.data || [];
    if (state.trajectoryVacancies.length) {
      // If list doesn't contain the pending vacancy (filter edge-case), add it manually
      const ids = state.trajectoryVacancies.map(v => String(v.id));
      let vacancies = state.trajectoryVacancies;
      if (selectedId && pending && !ids.includes(String(selectedId))) {
        vacancies = [pending, ...vacancies];
      }
      select.innerHTML = vacancies.map(v => `
        <option value="${v.id}" ${String(v.id) === String(selectedId) ? 'selected' : ''}>
          ${esc(v.title)}${v.employer ? ' · ' + esc(v.employer) : ''}${v.area ? ' · ' + esc(v.area) : ''}
        </option>
      `).join('');
    } else if (!pending) {
      select.innerHTML = '<option value="">Сначала импортируй вакансии</option>';
    }
    // if list empty but we have pending — keep the pre-populated option, generation will work
  } catch(_) {
    if (!pending) select.innerHTML = '<option value="">Не удалось загрузить вакансии</option>';
  }
}

// Delete trajectory
document.getElementById('btn-traj-delete').addEventListener('click', async () => {
  if (!confirm('Удалить текущую траекторию?')) return;
  try {
    await api('DELETE', '/trajectory');
    toast('Траектория удалена');
    loadTrajectory();
  } catch(e) { toast(e.message, 'error'); }
});

// Step modal
function openStepModal(id, status, progress, note) {
  document.getElementById('step-modal-id').value = id;
  document.getElementById('step-modal-status').value = status;
  document.getElementById('step-modal-progress').value = progress;
  document.getElementById('step-modal-note').value = note;
  document.getElementById('step-modal').classList.remove('hidden');
}
document.getElementById('step-modal-close').addEventListener('click', () => {
  document.getElementById('step-modal').classList.add('hidden');
});
document.getElementById('btn-step-save').addEventListener('click', async () => {
  const id = document.getElementById('step-modal-id').value;
  const status = document.getElementById('step-modal-status').value;
  const progressPercent = +document.getElementById('step-modal-progress').value;
  const note = document.getElementById('step-modal-note').value;
  try {
    const res = await api('PATCH', `/trajectory/steps/${id}`, { status, progressPercent, note });
    document.getElementById('step-modal').classList.add('hidden');
    toast('Шаг обновлён');
    // Update trajectory progress
    const tj = res.data.trajectory;
    if (tj) {
      document.getElementById('traj-progress-fill').style.width = tj.progressPercent + '%';
      document.getElementById('traj-progress-label').textContent = tj.progressPercent + '% (' + tj.completedSteps + '/' + tj.totalSteps + ')';
    }
    loadTrajectorySteps();
  } catch(e) { toast(e.message, 'error'); }
});

// ──────────────────────────────
//  SKILLS
// ──────────────────────────────
async function loadSkills() {
  const el = document.getElementById('skills-list');
  el.innerHTML = '<div class="loading">Загрузка...</div>';
  try {
    const res = await api('GET', '/profile/skills', null, { sort: 'level,desc' });
    renderSkillsTable(res.data, el);
  } catch(e) { el.innerHTML = `<div class="empty-state">${e.message}</div>`; }
}

function renderSkillsTable(skills, el) {
  if (!skills?.length) { el.innerHTML = '<div class="empty-state"><div class="empty-state-icon">◇</div>Нет навыков</div>'; return; }
  el.innerHTML = skills.map(s => `
    <div class="skill-row">
      <div class="skill-row-name">${esc(s.name)}</div>
      <div class="skill-row-cat">${esc(s.category || '')}</div>
      <div class="skill-row-level">
        ${[1,2,3,4,5].map(i => `<div class="level-dot ${i <= s.level ? 'filled' : ''}"></div>`).join('')}
      </div>
      <div style="font-family:var(--mono);font-size:11px;color:var(--text-muted);width:60px">${s.level}/5</div>
      ${s.verified ? '<span style="font-size:10px;color:var(--success)">✓</span>' : '<span style="font-size:10px;color:var(--text-dim)">·</span>'}
      <div class="skill-row-actions">
        <button class="btn-sm" onclick="editSkillLevel('${s.id}', ${s.level})">Уровень</button>
        <button class="btn-sm" style="color:var(--danger)" onclick="deleteSkill('${s.id}')">✕</button>
      </div>
    </div>
  `).join('');
}

async function editSkillLevel(userSkillId, current) {
  const lvl = prompt('Новый уровень (0–5):', current);
  if (lvl === null) return;
  const level = parseInt(lvl, 10);
  if (isNaN(level) || level < 0 || level > 5) { toast('Уровень должен быть 0–5', 'error'); return; }
  try {
    await api('PUT', `/profile/skills/${userSkillId}`, { level });
    toast('Уровень обновлён');
    loadSkills();
  } catch(e) { toast(e.message, 'error'); }
}

async function deleteSkill(userSkillId) {
  if (!confirm('Удалить навык?')) return;
  try {
    await api('DELETE', `/profile/skills/${userSkillId}`);
    toast('Навык удалён');
    loadSkills();
  } catch(e) { toast(e.message, 'error'); }
}

// Add skill
document.getElementById('btn-add-skill-open').addEventListener('click', () => {
  document.getElementById('add-skill-panel').classList.toggle('hidden');
});
document.getElementById('btn-skill-close').addEventListener('click', () => {
  document.getElementById('add-skill-panel').classList.add('hidden');
});
document.getElementById('btn-skill-add').addEventListener('click', async () => {
  const name = document.getElementById('skill-search-input').value.trim();
  const level = +document.getElementById('skill-level').value;
  if (!name) { toast('Введи название навыка', 'error'); return; }
  try {
    await api('POST', '/profile/skills', { name, level });
    toast('Навык добавлен');
    document.getElementById('add-skill-panel').classList.add('hidden');
    document.getElementById('skill-search-input').value = '';
    loadSkills();
  } catch(e) { toast(e.message, 'error'); }
});

// Skill autocomplete
let skillSearchTimer;
document.getElementById('skill-search-input').addEventListener('input', (e) => {
  clearTimeout(skillSearchTimer);
  const q = e.target.value.trim();
  if (q.length < 2) { document.getElementById('skill-suggestions').classList.add('hidden'); return; }
  skillSearchTimer = setTimeout(async () => {
    try {
      const res = await api('GET', '/skills/search', null, { q, limit: 8 });
      const sug = document.getElementById('skill-suggestions');
      if (!res.data?.length) { sug.classList.add('hidden'); return; }
      sug.innerHTML = res.data.map(s =>
        `<div class="suggestion-item" onclick="pickSuggestion('${esc(s.name)}')">${esc(s.name)} <span style="color:var(--text-dim)">·</span> <span style="color:var(--text-dim)">${esc(s.category || '')}</span></div>`
      ).join('');
      sug.classList.remove('hidden');
    } catch(_) {}
  }, 300);
});

function pickSuggestion(name) {
  document.getElementById('skill-search-input').value = name;
  document.getElementById('skill-suggestions').classList.add('hidden');
}
document.addEventListener('click', e => {
  if (!e.target.closest('#add-skill-panel')) {
    document.getElementById('skill-suggestions').classList.add('hidden');
  }
});

// ──────────────────────────────
//  PROFILE
// ──────────────────────────────
async function loadProfile() {
  // Show admin panel if user has ROLE_ADMIN
  const adminPanel = document.getElementById('admin-panel');
  if (adminPanel) {
    const isAdmin = state.user?.roles?.includes('ROLE_ADMIN');
    adminPanel.style.display = isAdmin ? '' : 'none';
  }
  try {
    await loadAreas();
    const [pr, sr] = await Promise.all([
      api('GET', '/profile'),
      api('GET', '/profile/stats'),
    ]);
    const p = pr.data;
    document.getElementById('prof-goal').value  = p.goal || '';
    document.getElementById('prof-level').value = p.level || 'JUNIOR';
    document.getElementById('prof-city').value  = p.city || '';
    fillAreaSelect('prof-area', p.areaId || 88);
    document.getElementById('prof-sal-min').value = p.salaryMin || '';
    document.getElementById('prof-sal-max').value = p.salaryMax || '';
    document.getElementById('prof-remote').checked = !!p.remote;

    const s = sr.data;
    document.getElementById('profile-stats').innerHTML = `
      <div class="profile-stat"><div class="profile-stat-val">${s.matchPercent}%</div><div class="profile-stat-lbl">Match</div></div>
      <div class="profile-stat"><div class="profile-stat-val">${s.coveredSkills}/${s.totalSkills}</div><div class="profile-stat-lbl">Навыки</div></div>
      <div class="profile-stat"><div class="profile-stat-val">${s.trajectoryProgress}%</div><div class="profile-stat-lbl">Траектория</div></div>
      <div class="profile-stat"><div class="profile-stat-val">${s.weeklyGoalProgress}%</div><div class="profile-stat-lbl">Неделя</div></div>
      <div class="profile-stat"><div class="profile-stat-val">${s.daysActive}</div><div class="profile-stat-lbl">Дней активен</div></div>
    `;
  } catch(e) { toast('Ошибка загрузки профиля: ' + e.message, 'error'); }
}

document.getElementById('btn-save-profile').addEventListener('click', async () => {
  const body = {
    goal:      document.getElementById('prof-goal').value.trim(),
    level:     document.getElementById('prof-level').value,
    city:      document.getElementById('prof-city').value.trim(),
    areaId:    +document.getElementById('prof-area').value || undefined,
    salaryMin: +document.getElementById('prof-sal-min').value || undefined,
    salaryMax: +document.getElementById('prof-sal-max').value || undefined,
    remote:    document.getElementById('prof-remote').checked,
  };
  try {
    await api('PUT', '/profile', body);
    toast('Профиль сохранён');
  } catch(e) { toast(e.message, 'error'); }
});

document.getElementById('btn-retake-onboarding').addEventListener('click', async () => {
  try {
    const profile = await api('GET', '/profile');
    showOnboarding(profile.data);
  } catch(e) {
    toast('Ошибка загрузки теста: ' + e.message, 'error');
  }
});

// ──────────────────────────────
//  PAGINATION
// ──────────────────────────────
function renderPagination(pg, containerId, onPage) {
  const el = document.getElementById(containerId);
  if (!pg || pg.totalPages <= 1) { el.innerHTML = ''; return; }
  const cur = pg.page;
  const total = pg.totalPages;
  let html = '';
  if (cur > 0) html += `<button class="page-btn" onclick="(${onPage})(${cur-1})">←</button>`;
  for (let p = Math.max(0, cur-2); p <= Math.min(total-1, cur+2); p++) {
    html += `<button class="page-btn ${p===cur?'active':''}" onclick="(${onPage})(${p})">${p+1}</button>`;
  }
  if (cur < total-1) html += `<button class="page-btn" onclick="(${onPage})(${cur+1})">→</button>`;
  el.innerHTML = html;
}

// ──────────────────────────────
//  UTILS
// ──────────────────────────────
function esc(str) {
  if (str === null || str === undefined) return '';
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function salaryStr(min, max) {
  if (!min && !max) return '';
  const fmt = n => n ? n.toLocaleString('ru-RU') : '';
  if (min && max) return fmt(min) + ' – ' + fmt(max) + ' ₽';
  if (min) return 'от ' + fmt(min) + ' ₽';
  return 'до ' + fmt(max) + ' ₽';
}

function statusLabel(s) {
  return { PENDING: 'Ожидает', IN_PROGRESS: 'В процессе', COMPLETED: 'Завершён', SKIPPED: 'Пропущен' }[s] || s;
}
function typeLabel(t) {
  return { LEARN: '📖 Учёба', PRACTICE: '⚡ Практика', PROJECT: '🏗 Проект', READ: '📄 Чтение' }[t] || t || '';
}

// ──────────────────────────────
//  INIT
// ──────────────────────────────
if (state.token && state.user) {
  showApp();
} else {
  showAuth();
}
loadAreas();
