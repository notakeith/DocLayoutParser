
import { $, escapeHtml, toast } from '../shared/dom.js';
import { fetchTemplates, deleteTemplate } from '../shared/api.js';

const state = { all: [], view: 'grid', sort: 'recent', q: '' };


function showSkeleton() {
    $('#content').innerHTML =
        '<div class="grid">' +
        Array(6).fill('<div class="skel-card"><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div></div>').join('') +
        '</div>';
}


async function load() {
    showSkeleton();
    try {
        state.all = await fetchTemplates();
        updateStats();
        render();
    } catch {
        $('#content').innerHTML =
            '<div class="empty"><h3>Не удалось загрузить</h3><p>Проверьте соединение с сервером и обновите страницу.</p></div>';
    }
}

function updateStats() {
    const total = state.all.length;
    const fields = state.all.reduce((s, t) => s + (t.entries?.length || 0), 0);
    const dates = state.all.map(t => t.createdAt).filter(Boolean).sort();
    const last = dates.length
        ? new Date(dates.at(-1)).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
        : '—';
    $('#statTotal').textContent = total;
    $('#statFields').textContent = fields;
    $('#statLast').textContent = last;
}


function filteredSorted() {
    let list = state.all.slice();
    if (state.q) {
        const q = state.q.toLowerCase();
        list = list.filter(t => (t.name || '').toLowerCase().includes(q) || (t.id || '').toLowerCase().includes(q));
    }
    if (state.sort === 'name') list.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'ru'));
    else if (state.sort === 'fields') list.sort((a, b) => (b.entries?.length || 0) - (a.entries?.length || 0));
    else list.sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''));
    return list;
}

function fmtDate(iso) {
    if (!iso) return '';
    try { return new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' }); }
    catch { return iso; }
}

function previewClass(t) { return t.baseWidth > t.baseHeight ? 'landscape' : 'portrait'; }

function miniDoc(t) {
    const n = Math.min(t.entries?.length || 0, 4);
    const boxes = Array.from({ length: n }, (_, i) =>
        `<i class="box ${i === n - 1 && n > 2 ? 'sig' : ''}"></i>`
    ).join('');
    return `<div class="doc-mini"><i></i><i></i><i></i>${boxes}</div>`;
}


function renderGrid(list) {
    return '<div class="grid">' + list.map(t => `
        <article class="card">
            <div class="card-preview ${previewClass(t)}">${miniDoc(t)}</div>
            <div class="card-body">
                <h3 class="card-name">${escapeHtml(t.name)}</h3>
                <div class="card-meta">
                    <span>${t.entries?.length || 0} полей</span>
                    <span class="dot"></span>
                    <span>${t.baseWidth}×${t.baseHeight}</span>
                    ${t.createdAt ? `<span class="dot"></span><span>${fmtDate(t.createdAt)}</span>` : ''}
                </div>
                <div class="card-id" style="margin-top:6px">${t.id}</div>
            </div>
            <div class="card-actions">
                <a class="btn btn-primary" href="/templates/recognize/${t.id}">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-6l-2-2H5a2 2 0 0 0-2 2Z"/></svg>
                    Распознать
                </a>
                <button class="icon-btn" title="Удалить" data-action="delete" data-id="${t.id}" data-name="${escapeHtml(t.name)}">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
                </button>
            </div>
        </article>`).join('') + '</div>';
}

function renderTable(list) {
    return `<div class="table-wrap"><table>
        <thead><tr>
            <th style="width:45%">Документ</th>
            <th>Формат</th><th>Полей</th><th>Создан</th>
            <th style="text-align:right">Действия</th>
        </tr></thead>
        <tbody>${list.map(t => `<tr>
            <td><div class="row-name">${escapeHtml(t.name)}</div><div class="card-id">${t.id}</div></td>
            <td><span class="badge">${t.baseWidth} × ${t.baseHeight}</span></td>
            <td><span class="badge">${t.entries?.length || 0}</span></td>
            <td style="color:var(--ink-mute);font-size:13px">${fmtDate(t.createdAt) || '—'}</td>
            <td><div class="row-actions">
                <a class="btn btn-sm" href="/templates/recognize/${t.id}">Распознать</a>
                <button class="btn btn-sm btn-danger" data-action="delete" data-id="${t.id}" data-name="${escapeHtml(t.name)}">Удалить</button>
            </div></td>
        </tr>`).join('')}</tbody>
    </table></div>`;
}

function render() {
    const list = filteredSorted();
    const wrap = $('#content');
    if (!list.length) {
        wrap.innerHTML = `<div class="empty">
            <div class="empty-mark"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg></div>
            <h3>${state.q ? 'Ничего не найдено' : 'Пока пусто'}</h3>
            <p>${state.q ? 'Попробуйте изменить поисковый запрос или сбросить фильтры.' : 'Создайте первый шаблон, чтобы начать распознавание документов.'}</p>
            ${state.q ? '' : '<a class="btn btn-primary" href="/templates/editor">Создать шаблон</a>'}
        </div>`;
        return;
    }
    wrap.innerHTML = state.view === 'grid' ? renderGrid(list) : renderTable(list);
    wrap.querySelectorAll('[data-action="delete"]').forEach(b => {
        b.addEventListener('click', () => askDelete(b.dataset.id, b.dataset.name));
    });
}


let pendingDelete = null;

function askDelete(id, name) {
    pendingDelete = id;
    const nameEl = document.getElementById('confirmName');
    if (nameEl) nameEl.textContent = name;
    const modal = document.getElementById('confirmModal');
    if (modal) modal.classList.add('open');
}

function closeConfirm() {
    pendingDelete = null;
    const modal = document.getElementById('confirmModal');
    if (modal) modal.classList.remove('open');
}


$('#searchInput').addEventListener('input', e => { state.q = e.target.value; render(); });
$('#sortSelect').addEventListener('change', e => { state.sort = e.target.value; render(); });
$('#viewGrid').addEventListener('click', () => {
    state.view = 'grid';
    $('#viewGrid').classList.add('active');
    $('#viewTable').classList.remove('active');
    render();
});
$('#viewTable').addEventListener('click', () => {
    state.view = 'table';
    $('#viewTable').classList.add('active');
    $('#viewGrid').classList.remove('active');
    render();
});


document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('confirmModal');
    const btnConfirm = document.getElementById('confirmDelete');

    modal?.addEventListener('click', e => { if (e.target === modal) closeConfirm(); });
    modal?.querySelectorAll('button:not(#confirmDelete)').forEach(b =>
        b.addEventListener('click', closeConfirm));
    btnConfirm?.addEventListener('click', async () => {
        if (!pendingDelete) return;
        const id = pendingDelete;
        closeConfirm();
        try {
            await deleteTemplate(id);
            state.all = state.all.filter(t => t.id !== id);
            updateStats();
            render();
            toast('Шаблон удалён', 'success');
        } catch {
            toast('Не удалось удалить', 'error');
        }
    });

    load();
});
