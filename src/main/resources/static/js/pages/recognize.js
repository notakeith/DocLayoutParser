
import { $, escapeHtml, toast, prettySize, download, copyText } from '../shared/dom.js';
import { processRecognition } from '../shared/api.js';


const pageData = document.getElementById('pageData');
const templateId = pageData?.dataset.templateId ?? '';
const pageCount = parseInt(pageData?.dataset.pageCount ?? '1', 10);
const isMultiPage = pageCount > 1;


let selectedFiles = [];
let allResults = [];
let activeResultIdx = 0;

const uploadCard = $('#uploadCard');
const fileInput = $('#fileInput');


uploadCard.addEventListener('click', e => {
    if (uploadCard.classList.contains('has-file')) return;
    if (e.target.closest('.btn')) return;
    fileInput.click();
});
$('#btnPickFile').addEventListener('click', e => { e.stopPropagation(); fileInput.click(); });
$('#btnReplace').addEventListener('click', e => { e.stopPropagation(); fileInput.click(); });
$('#btnProcess').addEventListener('click', e => { e.stopPropagation(); processAll(); });

fileInput.addEventListener('change', e => {
    const files = [...e.target.files];
    if (!files.length) return;

    if (isMultiPage) acceptFiles(files.slice(0, 1));
    else acceptFiles(files);
});


['dragenter', 'dragover'].forEach(ev =>
    uploadCard.addEventListener(ev, e => { e.preventDefault(); uploadCard.classList.add('drag-over'); }));
['dragleave', 'drop'].forEach(ev =>
    uploadCard.addEventListener(ev, e => { e.preventDefault(); uploadCard.classList.remove('drag-over'); }));
uploadCard.addEventListener('drop', e => {
    const files = [...e.dataTransfer.files].filter(f =>
        isMultiPage ? f.type === 'application/pdf' : (f.type.startsWith('image/') || f.type === 'application/pdf')
    );
    if (files.length) acceptFiles(isMultiPage ? files.slice(0, 1) : files);
});


function acceptFiles(files) {
    selectedFiles = files;
    renderFileList();

    const first = files[0];
    if (first?.type.startsWith('image/')) {
        const reader = new FileReader();
        reader.onload = ({ target }) => {
            $('#sourceImg').src = target.result;
            $('#sourceMetaName').textContent = first.name;
            $('#sourceMetaSize').textContent = prettySize(first.size);
        };
        reader.readAsDataURL(first);
    } else {
        $('#sourceImg').src = '';
        $('#sourceMetaName').textContent = first?.name ?? '—';
        $('#sourceMetaSize').textContent = first ? prettySize(first.size) : '—';
    }
    $('#uploadEmpty').style.display = 'none';
    $('#uploadFilled').style.display = 'block';
    uploadCard.classList.add('has-file');
}

function renderFileList() {
    $('#fileList').innerHTML = selectedFiles.map((f, i) => `
        <div style="display:flex;align-items:center;gap:10px;padding:8px 12px;background:var(--surface);border-radius:6px;border:1px solid var(--border)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>
            <span style="flex:1;font-size:13px;font-weight:500">${escapeHtml(f.name)}</span>
            <span style="font-size:12px;color:var(--ink-mute)">${prettySize(f.size)}</span>
        </div>`).join('');
}


const STATUSES = [
    'Загрузка образа в память…', 'Коррекция перспективы…',
    'Очистка документа…', 'Извлечение полей…', 'Сверка со словарями…', 'Финальная сборка…'
];
let statusInterval = null, statusIdx = 0;

function startSteps() {
    statusIdx = 0;
    $('#procSteps').innerHTML = STATUSES.map((_, i) =>
        `<div class="proc-step ${i === 0 ? 'active' : ''}"></div>`).join('');
    $('#procStatus').textContent = STATUSES[0];
    statusInterval = setInterval(() => {
        statusIdx = Math.min(statusIdx + 1, STATUSES.length - 1);
        $$('.proc-step').forEach((el, i) => {
            el.classList.toggle('done', i < statusIdx);
            el.classList.toggle('active', i === statusIdx);
        });
        const st = $('#procStatus');
        st.classList.add('fade-out');
        setTimeout(() => { st.textContent = STATUSES[statusIdx]; st.classList.remove('fade-out'); }, 280);
    }, 2200);
}
function stopSteps() { clearInterval(statusInterval); }

function $$(sel) { return [...document.querySelectorAll(sel)]; }

async function fadeOutProcessing() {
    stopSteps();
    const p = $('#processing');
    p.style.transition = 'opacity 400ms ease';
    p.style.opacity = '0';
    await new Promise(r => setTimeout(r, 420));
    p.classList.remove('active');
    p.style.opacity = '';
}

async function processAll() {
    if (!selectedFiles.length) return;


    uploadCard.style.transition = 'opacity 350ms ease, transform 350ms ease';
    uploadCard.style.opacity = '0';
    uploadCard.style.transform = 'translateY(-8px)';
    await new Promise(r => setTimeout(r, 380));
    uploadCard.style.display = 'none';
    $('#processing').classList.add('active');
    startSteps();

    allResults = [];


    if (selectedFiles.length > 1) {
        $('#batchProgress').style.display = 'block';
        $('#batchTotal').textContent = selectedFiles.length;
    }

    let failed = 0;
    for (let i = 0; i < selectedFiles.length; i++) {
        const file = selectedFiles[i];
        if (selectedFiles.length > 1) {
            $('#batchCurrent').textContent = i + 1;
            $('#procStatus').textContent = `Обработка: ${file.name}`;
        }
        try {
            const job = await processRecognition(file, templateId, { pollInterval: 1500 });
            allResults.push({ file, job });
        } catch (err) {
            console.error(`Failed: ${file.name}`, err);
            failed++;
            allResults.push({ file, job: null, error: err.message });
        }
    }

    await fadeOutProcessing();

    if (allResults.every(r => r.job === null)) {
        $('#errorState').classList.add('active');
        $('#errorText').textContent = 'Все файлы завершились с ошибкой.';
        return;
    }

    if (failed > 0) toast(`${failed} файл(ов) не удалось обработать`, 'error');

    activeResultIdx = allResults.findIndex(r => r.job !== null);
    displayResults();
}


const TYPE_LABEL = { TEXT: 'текст', NUMERIC: 'число', DATE: 'дата', SIGNATURE: 'подпись' };

function extractFields(job) { return Array.isArray(job?.results) ? job.results : []; }

function displayResults() {
    const { file, job } = allResults[activeResultIdx];
    const fields = job ? extractFields(job) : [];


    const totalFields = allResults.reduce((s, r) => s + extractFields(r.job).length, 0);
    $('#resCount').textContent = totalFields;


    const batchInfo = $('#batchInfo');
    if (allResults.length > 1) {
        const done = allResults.filter(r => r.job).length;
        batchInfo.textContent = ` · ${done} из ${allResults.length} файлов`;
    } else {
        batchInfo.textContent = '';
    }


    $('#confSummary').innerHTML = `
        <div><b>${totalFields}</b><span>Распознано полей</span></div>
        ${allResults.length > 1 ? `<div><b>${allResults.filter(r => r.job).length}</b><span>Файлов обработано</span></div>` : ''}`;


    const tabsEl = $('#batchTabs');
    if (allResults.length > 1) {
        tabsEl.style.display = 'flex';
        tabsEl.innerHTML = allResults.map((r, i) => `
            <button class="btn btn-sm ${i === activeResultIdx ? 'btn-primary' : ''}" data-idx="${i}">
                ${escapeHtml(r.file.name)}
                ${r.job ? '' : ' ⚠️'}
            </button>`).join('');
        tabsEl.querySelectorAll('[data-idx]').forEach(b =>
            b.addEventListener('click', () => { activeResultIdx = +b.dataset.idx; displayResults(); }));
    } else {
        tabsEl.style.display = 'none';
    }


    if (file.type.startsWith('image/')) {
        const reader = new FileReader();
        reader.onload = ({ target }) => { $('#sourceImg').src = target.result; };
        reader.readAsDataURL(file);
    } else {
        $('#sourceImg').src = '';
    }
    $('#sourceMetaName').textContent = file.name;
    $('#sourceMetaSize').textContent = prettySize(file.size);


    const list = $('#resultList');
    list.innerHTML = '';

    if (!job) {
        list.innerHTML = `<div class="result-card show" style="color:var(--danger)">
            Ошибка: ${escapeHtml(allResults[activeResultIdx].error || 'неизвестная ошибка')}
        </div>`;
        $('#results').classList.add('active');
        return;
    }

    if (!fields.length) {
        list.innerHTML = `<div class="result-card show">
            <div style="text-align:center;color:var(--ink-mute);font-style:italic;padding:16px">
                Поля не найдены
            </div>
        </div>`;
        $('#results').classList.add('active');
        return;
    }

    fields.forEach((field, idx) => {
        const card = document.createElement('div');
        card.className = 'result-card';

        const name = field.fieldName ?? `Поле ${idx + 1}`;
        const type = (field.fieldType ?? 'TEXT').toUpperCase();
        const textVal = field.textValue ?? null;
        const imgVal = field.imageValue ?? null;

        let contentHtml = '';
        if (type === 'SIGNATURE') {
            contentHtml = imgVal
                ? `<div class="signature-box"><img src="data:image/jpeg;base64,${imgVal}" alt="Подпись" style="max-height:80px"></div>`
                : `<div class="signature-placeholder">
                       <span style="font-size:20px">✍️</span>
                       <span style="font-style:italic;color:var(--ink-mute)">подпись зафиксирована</span>
                   </div>`;
        } else {
            if (textVal == null || textVal === '') {
                contentHtml = `<div class="res-value empty">не распознано</div>`;
            } else {

                const safe = escapeHtml(String(textVal)).replace(/\n/g, '<br>');
                contentHtml = `<div class="res-value" contenteditable="true" spellcheck="false">${safe}</div>`;
            }
        }

        card.innerHTML = `
            <div class="res-meta">
                <div class="res-name">${escapeHtml(name)}</div>
                <div class="res-type-row">
                    <span class="type-dot ${type}"></span>
                    <span class="res-type">${TYPE_LABEL[type] ?? type.toLowerCase()}</span>
                </div>
            </div>
            <div class="res-content">${contentHtml}</div>
            <div class="res-actions">
                <button class="res-action-btn" data-act="copy" title="Скопировать">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                </button>
            </div>`;

        list.appendChild(card);
        setTimeout(() => card.classList.add('show'), idx * 80);

        card.querySelector('[data-act="copy"]').addEventListener('click', () => {
            const v = card.querySelector('.res-value');
            copyText(v ? v.innerText.trim() : String(textVal || ''));
        });
        const v = card.querySelector('.res-value[contenteditable="true"]');
        if (v) v.addEventListener('input', () => { field.textValue = v.innerText; });
    });

    $('#results').classList.add('active');
    $('#results').scrollIntoView({ behavior: 'smooth', block: 'start' });
}


$('#btnCopyAll').addEventListener('click', () => {
    const { job } = allResults[activeResultIdx] ?? {};
    if (!job) return;
    const text = extractFields(job)
        .filter(f => (f.fieldType ?? '').toUpperCase() !== 'SIGNATURE')
        .map(f => `${f.fieldName ?? ''}\t${f.textValue ?? ''}`)
        .join('\n');
    copyText(text);
});

$('#btnExportJson').addEventListener('click', () => {
    const data = allResults.length === 1
        ? allResults[0].job
        : allResults.map(r => ({ file: r.file.name, job: r.job }));
    download('recognition.json', JSON.stringify(data, null, 2), 'application/json');
});

$('#btnExportCsv').addEventListener('click', () => {
    const rows = [['Файл', 'Поле', 'Тип', 'Значение']];
    for (const { file, job } of allResults) {
        if (!job) continue;
        for (const f of extractFields(job)) {
            rows.push([
                file.name,
                f.fieldName ?? '',
                f.fieldType ?? '',
                (f.fieldType ?? '').toUpperCase() === 'SIGNATURE' ? '(подпись)' : (f.textValue ?? '')
            ]);
        }
    }
    const csv = '\ufeff' + rows.map(r =>
        r.map(c => '"' + String(c).replace(/"/g, '""') + '"').join(',')
    ).join('\n');
    download('recognition.csv', csv, 'text/csv');
});

$('#btnAgain').addEventListener('click', reset);
$('#btnAgainErr')?.addEventListener('click', reset);

function reset() {
    selectedFiles = []; allResults = []; activeResultIdx = 0;
    $('#results').classList.remove('active');
    $('#errorState').classList.remove('active');
    $('#uploadEmpty').style.display = 'block';
    $('#uploadFilled').style.display = 'none';
    $('#fileList').innerHTML = '';
    uploadCard.classList.remove('has-file');
    Object.assign(uploadCard.style, { display: '', opacity: '', transform: '' });
    fileInput.value = '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
}
