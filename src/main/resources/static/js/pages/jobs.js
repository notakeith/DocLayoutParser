
import { $, escapeHtml, toast, download, copyText } from '../shared/dom.js';

const PAGE_SIZE = 20;
const state = { all: [], filtered: [], page: 0, q: '', statusFilter: 'ALL', templateFilter: 'ALL', pollTimer: null };


async function fetchJobs() {
    const r = await fetch('/api/jobs');
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
}
async function fetchJob(id) {
    const r = await fetch(`/api/jobs/${id}`);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
}


function fmtDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}
function fmtDuration(job) {
    if (!job.createdAt || !job.finishedAt) return '';
    const ms = new Date(job.finishedAt) - new Date(job.createdAt);
    return ms < 1000 ? ms + ' мс' : (ms / 1000).toFixed(1) + ' с';
}


function extractFields(job) { return Array.isArray(job?.results) ? job.results : []; }

const TYPE_LABEL = { TEXT: 'текст', NUMERIC: 'число', DATE: 'дата', SIGNATURE: 'подпись' };


function applyFilters() {
    let list = state.all.slice();
    if (state.statusFilter !== 'ALL') list = list.filter(j => j.status === state.statusFilter);
    if (state.templateFilter !== 'ALL') list = list.filter(j => j.template?.id === state.templateFilter);
    if (state.q) {
        const q = state.q.toLowerCase();
        list = list.filter(j =>
            (j.sourceFileName || '').toLowerCase().includes(q) ||
            (j.id || '').toLowerCase().includes(q) ||
            (j.template?.name || '').toLowerCase().includes(q)
        );
    }
    state.filtered = list;
    state.page = 0;
}

function currentPage() {
    return state.filtered.slice(state.page * PAGE_SIZE, (state.page + 1) * PAGE_SIZE);
}


function render() { applyFilters(); renderTable(); renderPagination(); updateLiveIndicator(); }

function renderTable() {
    const tbody = $('#jobsTbody');
    const page = currentPage();
    if (!page.length) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:48px;color:var(--ink-mute);font-style:italic">
            ${state.q || state.statusFilter !== 'ALL' ? 'Ничего не найдено' : 'Задач пока нет'}
        </td></tr>`;
        return;
    }
    tbody.innerHTML = page.map(job => {
        const fields = extractFields(job);
        return `<tr data-job-id="${job.id}" style="cursor:pointer">
            <td>
                <div style="font-weight:500">${escapeHtml(job.sourceFileName || 'Без имени')}</div>
                <div style="font-size:11px;color:var(--ink-faint);font-family:var(--font-mono)">${job.id}</div>
            </td>
            <td>${escapeHtml(job.template?.name || '—')}</td>
            <td><span class="status-badge ${job.status}">${statusLabel(job.status)}</span></td>
            <td style="font-size:13px;color:var(--ink-mute)">${fmtDate(job.createdAt)}</td>
            <td style="font-size:13px;color:var(--ink-mute)">${fields.length ? fields.length + ' пол.' : '—'}</td>
        </tr>`;
    }).join('');
    tbody.querySelectorAll('tr[data-job-id]').forEach(row =>
        row.addEventListener('click', () => openDrawer(row.dataset.jobId)));
}

function statusLabel(s) {
    return { DONE: 'Готово', FAILED: 'Ошибка', PROCESSING: 'Обработка', PENDING: 'Ожидание' }[s] ?? s;
}


function renderPagination() {
    const total = Math.ceil(state.filtered.length / PAGE_SIZE);
    const pag = $('#pagination');
    if (total <= 1) { pag.innerHTML = ''; return; }
    let html = `<button class="page-btn" id="pagePrev" ${state.page === 0 ? 'disabled' : ''}>←</button>`;
    for (let i = 0; i < total; i++) {
        if (total > 7 && Math.abs(i - state.page) > 2 && i !== 0 && i !== total - 1) {
            if (i === 1 || i === total - 2) html += `<span style="padding:0 4px;color:var(--ink-faint)">…</span>`;
            continue;
        }
        html += `<button class="page-btn ${i === state.page ? 'active' : ''}" data-pg="${i}">${i + 1}</button>`;
    }
    html += `<button class="page-btn" id="pageNext" ${state.page >= total - 1 ? 'disabled' : ''}>→</button>`;
    pag.innerHTML = html;
    pag.querySelectorAll('[data-pg]').forEach(b => b.addEventListener('click', () => { state.page = +b.dataset.pg; renderTable(); renderPagination(); }));
    pag.querySelector('#pagePrev')?.addEventListener('click', () => { state.page--; renderTable(); renderPagination(); });
    pag.querySelector('#pageNext')?.addEventListener('click', () => { state.page++; renderTable(); renderPagination(); });
}


function startLivePolling() {
    if (state.pollTimer) return;
    state.pollTimer = setInterval(async () => {
        const live = state.all.filter(j => j.status === 'PENDING' || j.status === 'PROCESSING');
        if (!live.length) { stopLivePolling(); return; }
        for (const job of live) {
            try {
                const updated = await fetchJob(job.id);
                const idx = state.all.findIndex(j => j.id === job.id);
                if (idx >= 0) state.all[idx] = updated;
                if (updated.status === 'DONE') toast(`Готово: ${updated.sourceFileName || updated.id}`, 'success');
                if (updated.status === 'FAILED') toast(`Ошибка: ${updated.sourceFileName || updated.id}`, 'error');

                if ($('#jobDrawer')?.dataset.jobId === updated.id) populateDrawer(updated);
            } catch { }
        }
        renderTable(); updateLiveIndicator();
    }, 3000);
}
function stopLivePolling() { clearInterval(state.pollTimer); state.pollTimer = null; updateLiveIndicator(); }
function updateLiveIndicator() {
    const live = state.all.some(j => j.status === 'PENDING' || j.status === 'PROCESSING');
    const el = $('#liveIndicator');
    if (el) el.style.display = live ? 'inline-flex' : 'none';
}


async function openDrawer(jobId) {
    const bd = $('#jobDrawerBd');
    const drawer = $('#jobDrawer');
    drawer.dataset.jobId = jobId;
    bd.classList.add('open');

    const cached = state.all.find(j => j.id === jobId);
    if (cached) populateDrawer(cached);

    try {
        const fresh = await fetchJob(jobId);
        const idx = state.all.findIndex(j => j.id === jobId);
        if (idx >= 0) state.all[idx] = fresh;
        populateDrawer(fresh);
    } catch { }
}

function populateDrawer(job) {
    const fields = extractFields(job);
    $('#drawerTitle').textContent = job.sourceFileName || 'Задача';
    $('#drawerStatus').className = `status-badge ${job.status}`;
    $('#drawerStatus').textContent = statusLabel(job.status);

    $('#drawerMeta').innerHTML = `
        <div class="drawer-meta-item"><span>Шаблон</span><b>${escapeHtml(job.template?.name || '—')}</b></div>
        <div class="drawer-meta-item"><span>Создана</span><b>${fmtDate(job.createdAt)}</b></div>
        <div class="drawer-meta-item"><span>Завершена</span><b>${fmtDate(job.finishedAt)}</b></div>
        ${job.finishedAt ? `<div class="drawer-meta-item"><span>Время</span><b>${fmtDuration(job)}</b></div>` : ''}
        ${job.errorMessage ? `<div class="drawer-meta-item" style="flex-basis:100%"><span>Ошибка</span><b style="color:var(--danger)">${escapeHtml(job.errorMessage)}</b></div>` : ''}
    `;

    if (!fields.length) {
        $('#drawerBody').innerHTML = `<div style="text-align:center;padding:40px;color:var(--ink-mute);font-style:italic">
            ${job.status === 'DONE' ? 'Поля не найдены' : 'Результаты появятся после завершения'}
        </div>`;
        return;
    }

    $('#drawerBody').innerHTML = fields.map(f => {
        const type = (f.fieldType ?? 'TEXT').toUpperCase();
        const name = f.fieldName ?? 'Поле';

        let valueHtml;
        if (type === 'SIGNATURE') {

            if (f.imageStorageKey) {
                valueHtml = `<div style="display:flex;align-items:center;gap:8px">
                    <span style="font-size:18px">✍️</span>
                    <span class="dr-value" style="font-style:italic;color:var(--ink-mute)">подпись зафиксирована</span>
                </div>`;
            } else {
                valueHtml = `<span class="dr-value empty">подпись отсутствует</span>`;
            }
        } else {
            const rawVal = f.textValue ?? null;

            const displayVal = rawVal != null
                ? escapeHtml(String(rawVal)).replace(/\n/g, '<br>')
                : null;
            valueHtml = displayVal
                ? `<div class="dr-value">${displayVal}</div>`
                : `<div class="dr-value empty">не распознано</div>`;
        }

        return `<div class="drawer-result-row">
            <div class="dr-label">
                <div class="dr-name">
                    <span class="dr-type-dot ${type}"></span>${escapeHtml(name)}
                </div>
                <div class="dr-type">${TYPE_LABEL[type] ?? type.toLowerCase()}</div>
            </div>
            ${valueHtml}
        </div>`;
    }).join('');
}

function closeDrawer() {
    $('#jobDrawerBd').classList.remove('open');
    delete $('#jobDrawer').dataset.jobId;
}


document.addEventListener('DOMContentLoaded', () => {
    $('#jobDrawerBd').addEventListener('click', e => { if (e.target === e.currentTarget) closeDrawer(); });
    $('#drawerClose').addEventListener('click', closeDrawer);
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeDrawer(); });

    $('#drawerExportJson').addEventListener('click', () => {
        const jobId = $('#jobDrawer')?.dataset.jobId;
        const job = state.all.find(j => j.id === jobId);
        if (job) download(`job-${jobId}.json`, JSON.stringify(job, null, 2), 'application/json');
    });

    $('#drawerCopyAll').addEventListener('click', () => {
        const jobId = $('#jobDrawer')?.dataset.jobId;
        const job = state.all.find(j => j.id === jobId);
        if (!job) return;
        const text = extractFields(job)
            .filter(f => (f.fieldType ?? '').toUpperCase() !== 'SIGNATURE')
            .map(f => `${f.fieldName ?? ''}\t${f.textValue ?? ''}`)
            .join('\n');
        copyText(text);
    });


    $('#jobSearch').addEventListener('input', e => { state.q = e.target.value; render(); });
    $('#statusFilter').addEventListener('change', e => { state.statusFilter = e.target.value; render(); });
    $('#templateFilter').addEventListener('change', e => { state.templateFilter = e.target.value; render(); });
    $('#btnRefresh').addEventListener('click', loadJobs);

    loadJobs();
});


function populateTemplateFilter() {
    const seen = new Map();
    state.all.forEach(j => { if (j.template) seen.set(j.template.id, j.template.name); });
    const sel = $('#templateFilter');
    const cur = sel.value;
    sel.innerHTML = '<option value="ALL">Все шаблоны</option>' +
        [...seen.entries()].map(([id, name]) =>
            `<option value="${id}" ${id === cur ? 'selected' : ''}>${escapeHtml(name)}</option>`
        ).join('');
}


async function loadJobs() {
    $('#jobsTbody').innerHTML = `<tr><td colspan="5" style="text-align:center;padding:48px">
        <div style="display:inline-flex;flex-direction:column;align-items:center;gap:12px;color:var(--ink-mute)">
            <div style="width:24px;height:24px;border:2px solid var(--border);border-top-color:var(--ink);border-radius:50%;animation:spin 0.8s linear infinite"></div>
            Загрузка…
        </div>
    </td></tr>`;
    try {
        state.all = await fetchJobs();
        populateTemplateFilter();
        render();
        if (state.all.some(j => j.status === 'PENDING' || j.status === 'PROCESSING')) startLivePolling();
    } catch (err) {
        console.error('loadJobs error:', err);
        $('#jobsTbody').innerHTML = `<tr><td colspan="5" style="text-align:center;padding:48px;color:var(--danger)">
            Не удалось загрузить историю: ${err.message}
        </td></tr>`;
    }
}
