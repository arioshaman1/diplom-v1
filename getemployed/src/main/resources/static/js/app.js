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
};

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
function showApp() {
  document.getElementById('screen-auth').classList.remove('active');
  document.getElementById('screen-app').classList.add('active');
  loadDashboard();
}

function showAuth() {
  document.getElementById('screen-app').classList.remove('active');
  document.getElementById('screen-auth').classList.add('active');
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
    if (page === 'trajectory') loadTrajectory();
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
    renderGaps(d.skillGaps || []);
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
      <div class="rec-title">${esc(r.title)}</div>
      <div class="rec-employer">${esc(r.employer || '')}</div>
      <div class="rec-score-row">
        <span class="rec-score">${r.score}%</span>
        <span class="rec-salary">${salaryStr(r.salaryMin, r.salaryMax)}</span>
      </div>
      ${r.gaps?.length ? `<div class="rec-gaps">${r.gaps.map(g => `<span class="tag">${esc(g)}</span>`).join('')}</div>` : ''}
    </div>
  `).join('') || '<div class="empty-state">Нет рекомендаций</div>';
}

// Rebuild
document.getElementById('btn-rebuild').addEventListener('click', async () => {
  try {
    const res = await api('POST', '/recommendations/rebuild');
    toast('Пересчёт запущен (job: ' + res.data.jobId.slice(0,8) + '...)');
  } catch(e) { toast(e.message, 'error'); }
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
  const max = 20; let i = 0;
  const tick = async () => {
    if (++i > max) return;
    try {
      const r = await api('GET', `/vacancies/import/${jobId}`);
      const d = r.data;
      statusEl.textContent = `Статус: ${d.status} · Импортировано: ${d.imported ?? '?'} · Ошибки: ${d.errors ?? 0}`;
      if (d.status !== 'COMPLETED' && d.status !== 'FAILED') setTimeout(tick, 3000);
      else if (d.status === 'COMPLETED') { toast(`Импортировано ${d.imported} вакансий`); loadVacancies(); }
    } catch(_) {}
  };
  setTimeout(tick, 3000);
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
        ${v.url ? `<a href="${v.url}" target="_blank" class="btn-primary" style="text-decoration:none">Открыть на HH.ru ↗</a>` : ''}
        <button class="btn-accent" onclick="saveVacancy('${v.id}', this)">♡ Сохранить</button>
        <button class="btn-ghost" onclick="generateTrajModal('${v.id}')">→ Создать траекторию</button>
      </div>
    `;
  } catch(e) {
    content.innerHTML = `<div class="empty-state">${e.message}</div>`;
  }
}

function generateTrajModal(vacancyId) {
  closeModal();
    document.querySelector('[data-page="trajectory"]').click();
  document.getElementById('gen-vacancy-id').value = vacancyId;
  document.getElementById('traj-gen-panel').classList.remove('hidden');
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
    if (!res.data?.length) { el.innerHTML = '<div class="empty-state"><div class="empty-state-icon">◈</div>Нет рекомендаций. Сначала импортируйте вакансии.</div>'; return; }
    el.innerHTML = res.data.map(r => `
      <div class="vac-card" onclick="openVacancy('${r.vacancyId}')">
        <div class="vac-header">
          <div class="vac-title">${esc(r.vacancy?.title || '')}</div>
          <div class="vac-score">${r.score}%</div>
        </div>
        <div class="vac-employer">${esc(r.vacancy?.employer || '')}</div>
        <div class="vac-meta">
          ${r.vacancy?.salaryMin || r.vacancy?.salaryMax ? `<span class="vac-badge badge-salary">${salaryStr(r.vacancy.salaryMin, r.vacancy.salaryMax)}</span>` : ''}
        </div>
        <div class="rec-gaps">${(r.gaps||[]).map(g=>`<span class="tag">${esc(g.skill)}</span>`).join('')}</div>
        <div style="font-family:var(--mono);font-size:10px;color:var(--text-dim);margin-top:8px">
          SBERT ${r.sbertScore||'—'} · Coverage ${r.skillsCoverage||'—'}%
        </div>
      </div>
    `).join('');
    renderPagination(res.pagination, 'recs-pagination', p => { state.recsPage = p; loadRecs(); });
  } catch(e) { el.innerHTML = `<div class="empty-state">${e.message}</div>`; }
}

// ──────────────────────────────
//  TRAJECTORY
// ──────────────────────────────
async function loadTrajectory() {
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
        <div class="step-meta">
          <span>${esc(s.skillName || '')}</span>
          <span>${typeLabel(s.type)}</span>
          <span>~${s.estimatedHours || 0}ч</span>
          <span>Нед.${s.week}</span>
          ${s.deadline ? `<span>до ${s.deadline}</span>` : ''}
        </div>
        ${s.resources?.length ? `<div class="step-resources">
          ${s.resources.map(r => `<a href="${r.url || '#'}" target="_blank" class="step-resource">${esc(r.title)}</a>`).join('')}
        </div>` : ''}
      </div>
      <div class="step-card-progress">
        <div class="step-pct">${s.progressPercent || 0}%</div>
        <div style="margin-top:6px">
          <button class="btn-sm" onclick="openStepModal('${s.id}','${s.status}',${s.progressPercent||0},'${esc(s.note||'')}')">Изменить</button>
        </div>
      </div>
    </div>
  `}).join('');
}

document.getElementById('btn-traj-filter').addEventListener('click', () => {
  state.trajWeek   = document.getElementById('traj-week-filter').value;
  state.trajStatus = document.getElementById('traj-status-filter').value;
  loadTrajectorySteps();
});

// Generate
document.getElementById('btn-traj-gen-open').addEventListener('click', () => {
  document.getElementById('traj-gen-panel').classList.toggle('hidden');
});
document.getElementById('btn-traj-gen-close').addEventListener('click', () => {
  document.getElementById('traj-gen-panel').classList.add('hidden');
});
document.getElementById('btn-traj-generate').addEventListener('click', async () => {
  const vacancyId = document.getElementById('gen-vacancy-id').value.trim();
  const hoursPerWeek = +document.getElementById('gen-hours').value;
  const weeks = +document.getElementById('gen-weeks').value;
  try {
    await api('POST', '/trajectory/generate', { hoursPerWeek, weeks }, { vacancyId });
    toast('Траектория создана!');
    document.getElementById('traj-gen-panel').classList.add('hidden');
    loadTrajectory();
  } catch(e) { toast(e.message, 'error'); }
});

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
  try {
    const [pr, sr] = await Promise.all([
      api('GET', '/profile'),
      api('GET', '/profile/stats'),
    ]);
    const p = pr.data;
    document.getElementById('prof-goal').value  = p.goal || '';
    document.getElementById('prof-level').value = p.level || 'JUNIOR';
    document.getElementById('prof-city').value  = p.city || '';
    document.getElementById('prof-area').value  = p.areaId || '';
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
