
import { $, toast, prettySize } from '../../shared/dom.js';
import {
    canvas, img,
    setScale, setZoom, zoom,
    getPos, hitHandle, hitTest, cursorForHandle,
    drawAll, applyZoom,
    getScale
} from './canvas.js';
import {
    renderEntries, localActiveIdx,
    updateSaveBtn, updateUndoBtn, updateCoordReadout
} from './entries.js';


let entries = [];
let activeIdx = -1;
let pendingBox = null;

let mode = null, resizeHandle = null, dragOrigin = null;
let startX, startY;
let saveTimer = null;


let pages = [];
let currentPage = 0;
let uploadedFile = null;
let savedTemplateId = null;


const pageEntries = () => entries.filter(e => e.pageNumber === currentPage);

function localIdx() {
    return localActiveIdx(entries, currentPage, activeIdx);
}

function redraw() {
    drawAll(pageEntries(), localIdx());
}

function rerender(newGlobalIdx = activeIdx) {
    activeIdx = newGlobalIdx;
    renderEntries(entries, currentPage, activeIdx,
        gi => { activeIdx = gi; },
        saveToLocal,
        deleteEntry
    );
}

function deleteEntry(globalIdx) {
    entries.splice(globalIdx, 1);
    if (activeIdx === globalIdx) activeIdx = -1;
    else if (activeIdx > globalIdx) activeIdx--;
    rerender(); redraw(); saveToLocal();
}


function saveToLocal() {
    localStorage.setItem('draft_name', $('#templateName').value);
    localStorage.setItem('draft_entries', JSON.stringify(entries));
    if (pages.length === 1 && pages[0]?.src?.startsWith('data:')) {
        localStorage.setItem('draft_img', pages[0].src);
    }
    flagDirty();
}

function flagDirty() {
    $('#saveState').className = 'save-state dirty';
    $('#saveStateText').textContent = 'Сохранение черновика…';
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
        $('#saveState').className = 'save-state saved';
        $('#saveStateText').textContent = 'Черновик сохранён';
    }, 600);
}

window.addEventListener('load', () => {
    const dName = localStorage.getItem('draft_name');
    const dEntries = localStorage.getItem('draft_entries');
    const dImg = localStorage.getItem('draft_img');
    if (dName) $('#templateName').value = dName;
    if (dEntries) { try { entries = JSON.parse(dEntries); } catch { } rerender(); }
    if (dImg) loadSingleImage(dImg, 'черновик', 0);
    updateSaveBtn(entries);
});


function buildSlider() {
    const slider = $('#pageSlider');
    if (!slider) return;

    if (pages.length <= 1) {
        slider.style.display = 'none';

        $('#btnPagePrev').style.display = 'none';
        $('#btnPageNext').style.display = 'none';
        $('#pageLabel').style.display = 'none';
        return;
    }
    slider.style.display = 'flex';
    $('#btnPagePrev').style.display = '';
    $('#btnPageNext').style.display = '';
    $('#pageLabel').style.display = '';

    slider.innerHTML = pages.map((p, i) => `
        <button class="page-thumb-btn ${i === currentPage ? 'active' : ''}" data-pi="${i}" title="Страница ${i + 1}">
            <img src="${p.src || ''}" alt="" style="background:var(--paper)">
            <span>${i + 1}</span>
        </button>`).join('');

    slider.querySelectorAll('[data-pi]').forEach(btn =>
        btn.addEventListener('click', () => switchPage(+btn.dataset.pi)));

    $('#pageLabel').textContent = `${currentPage + 1} / ${pages.length}`;
}

async function switchPage(idx) {
    if (idx === currentPage || idx < 0 || idx >= pages.length) return;
    currentPage = idx;
    activeIdx = -1;

    const p = pages[idx];

    if (p.src) loadImageSrc(p.src);

    buildSlider();
    rerender();
    redraw();
}

function goPagePrev() { switchPage(currentPage - 1); }
function goPageNext() { switchPage(currentPage + 1); }


const dz = $('#dropzone');
const imageLoader = $('#imageLoader');

['dragenter', 'dragover'].forEach(ev =>
    dz.addEventListener(ev, e => { e.preventDefault(); dz.classList.add('drag-over'); }));
['dragleave', 'drop'].forEach(ev =>
    dz.addEventListener(ev, e => { e.preventDefault(); dz.classList.remove('drag-over'); }));
dz.addEventListener('drop', e => { const f = e.dataTransfer.files[0]; if (f) loadFile(f); });
dz.addEventListener('click', e => {
    if (dz.classList.contains('loaded') || e.target.closest('button')) return;
    imageLoader.click();
});
imageLoader.addEventListener('change', e => { if (e.target.files[0]) loadFile(e.target.files[0]); });
$('#dzReplace').addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); imageLoader.click(); });
$('#dzRemove').addEventListener('click', e => {
    e.preventDefault(); e.stopPropagation();
    if (entries.length && !confirm('Убрать файл? Разметка сохранится, но потеряет привязку.')) return;
    clearImage();
});

function loadFile(file) {
    uploadedFile = file;
    const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
    if (isPdf) loadPdfViaServer(file);
    else {
        const reader = new FileReader();
        reader.onload = e => loadSingleImage(e.target.result, file.name, file.size);
        reader.readAsDataURL(file);
    }
}

function loadSingleImage(src, name, size) {

    savedTemplateId = null;
    uploadedFile = null;
    pages = [{ pageNumber: 0, src, width: 0, height: 0 }];
    currentPage = 0;
    setDzLoaded(name, size, src);
    loadImageSrc(src);
    buildSlider();
    updateSaveBtn(entries);
}

async function loadPdfViaServer(file) {
    setDzUploading();
    try {
        const fd = new FormData();
        fd.append('dto', new Blob([JSON.stringify({
            name: $('#templateName').value.trim() || file.name,
            baseWidth: 0, baseHeight: 0, entries: []
        })], { type: 'application/json' }));
        fd.append('referenceFile', file);

        const tpl = await fetch('/api/templates/with-reference', { method: 'POST', body: fd })
            .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); });
        savedTemplateId = tpl.id;

        const pagesData = await fetch(`/api/templates/${tpl.id}/pages`).then(r => r.json());
        pages = pagesData.map(p => ({
            pageNumber: p.pageNumber,
            pageId: p.id,
            src: `/api/templates/${tpl.id}/pages/${p.id}/image`,
            width: p.width ?? 0,
            height: p.height ?? 0,
        }));
        currentPage = 0;

        loadImageSrc(pages[0].src);
        setDzLoaded(file.name, file.size, pages[0].src);
        buildSlider();
        updateSaveBtn(entries);
    } catch (err) {
        setDzEmpty();
        toast('Не удалось загрузить PDF: ' + (err.message || 'ошибка сервера'), 'error');
    }
}

function setDzUploading() {
    $('#dzEmpty').style.display = 'none';
    $('#dzLoaded').style.display = 'none';
    $('#dzUploading').style.display = 'flex';
}
function setDzEmpty() {
    $('#dzEmpty').style.display = '';
    $('#dzLoaded').style.display = 'none';
    $('#dzUploading').style.display = 'none';
    dz.classList.remove('loaded');
}
function setDzLoaded(name, size, thumbSrc) {
    $('#dzEmpty').style.display = 'none';
    $('#dzUploading').style.display = 'none';
    $('#dzLoaded').style.display = 'flex';
    dz.classList.add('loaded');
    $('#dzThumb').src = thumbSrc || '';
    $('#dzName').textContent = name;
    $('#dzSize').textContent = size
        ? `${prettySize(size)}${pages.length > 1 ? ' · ' + pages.length + ' стр.' : ''}`
        : pages.length > 1 ? pages.length + ' страниц' : '';
}

function clearImage() {
    pages = []; currentPage = 0; uploadedFile = null; savedTemplateId = null;
    img.removeAttribute('src');
    $('#canvas-container').style.display = 'none';
    $('#wsEmpty').style.display = 'grid';
    $('#wsToolbar').style.display = 'none';
    $('#pageSlider').style.display = 'none';
    setDzEmpty();
    imageLoader.value = '';
    localStorage.removeItem('draft_img');
    flagDirty();
    updateSaveBtn(entries);
}

function loadImageSrc(src) {
    img.onload = () => {
        const maxW = 900;
        const displayW = Math.min(img.naturalWidth, maxW);
        img.style.width = displayW + 'px';
        img.style.height = 'auto';
        $('#canvas-container').style.display = 'inline-block';
        $('#wsEmpty').style.display = 'none';
        $('#wsToolbar').style.display = 'flex';

        if (pages[currentPage]) {
            pages[currentPage].width = img.naturalWidth;
            pages[currentPage].height = img.naturalHeight;
        }

        requestAnimationFrame(() => {
            canvas.width = img.clientWidth || displayW;
            canvas.height = img.clientHeight || Math.round(displayW * img.naturalHeight / img.naturalWidth);
            setScale(img.naturalWidth / (img.clientWidth || displayW));
            redraw();
        });
    };
    img.src = src;
}




canvas.addEventListener('mousedown', e => {
    if (!img.src) return;
    const p = getPos(e);
    const pe = pageEntries();


    if (activeIdx >= 0 && entries[activeIdx]?.pageNumber === currentPage) {
        const h = hitHandle(p.x, p.y, entries[activeIdx]);
        if (h) { mode = 'resize'; resizeHandle = h; dragOrigin = { mx: p.x, my: p.y, box: { ...entries[activeIdx].box } }; return; }
    }


    const hitLocal = hitTest(p.x, p.y, pe);
    if (hitLocal >= 0) {

        activeIdx = entries.indexOf(pe[hitLocal]);
        mode = 'move';
        dragOrigin = { mx: p.x, my: p.y, box: { ...entries[activeIdx].box } };
        rerender(); redraw(); return;
    }


    activeIdx = -1; mode = 'create';
    startX = p.x; startY = p.y;
    rerender(); redraw();
});

canvas.addEventListener('mousemove', e => {
    const p = getPos(e);
    const pe = pageEntries();
    const sc = getScale();

    if (!mode) {
        let cur = 'crosshair';
        if (activeIdx >= 0 && entries[activeIdx]?.pageNumber === currentPage) {
            const h = hitHandle(p.x, p.y, entries[activeIdx]);
            if (h) { cur = cursorForHandle(h); }
            else if (hitTest(p.x, p.y, pe) >= 0) cur = 'move';
        } else if (hitTest(p.x, p.y, pe) >= 0) cur = 'pointer';
        canvas.style.cursor = cur;
        return;
    }

    if (mode === 'create') {
        drawAll(pe, localIdx());
        const ctx2 = canvas.getContext('2d');
        ctx2.strokeStyle = '#1E2A32'; ctx2.lineWidth = 2; ctx2.setLineDash([6, 4]);
        ctx2.strokeRect(startX, startY, p.x - startX, p.y - startY);
        ctx2.setLineDash([]);
        const w = Math.abs(p.x - startX) * sc, h = Math.abs(p.y - startY) * sc;
        const label = `${Math.round(w)} × ${Math.round(h)}`;
        ctx2.font = '11px ui-monospace,monospace';
        const tw = ctx2.measureText(label).width + 10;
        ctx2.fillStyle = 'rgba(30,42,50,0.85)'; ctx2.fillRect(p.x + 6, p.y + 6, tw, 18);
        ctx2.fillStyle = '#F4F2EF'; ctx2.fillText(label, p.x + 11, p.y + 19);
        return;
    }

    if (activeIdx >= 0 && (mode === 'move' || mode === 'resize')) {
        const entry = entries[activeIdx];
        const dx = (p.x - dragOrigin.mx) * sc;
        const dy = (p.y - dragOrigin.my) * sc;
        const orig = dragOrigin.box;
        const W = img.naturalWidth, H = img.naturalHeight, MIN = 8;

        if (mode === 'move') {
            entry.box.x = Math.max(0, Math.min(W - orig.width, Math.round(orig.x + dx)));
            entry.box.y = Math.max(0, Math.min(H - orig.height, Math.round(orig.y + dy)));
        } else {
            let x = orig.x, y = orig.y, w = orig.width, h = orig.height;
            if (resizeHandle.includes('e')) w = Math.max(MIN, orig.width + dx);
            if (resizeHandle.includes('s')) h = Math.max(MIN, orig.height + dy);
            if (resizeHandle.includes('w')) { x = orig.x + dx; w = orig.width - dx; if (w < MIN) { x = orig.x + orig.width - MIN; w = MIN; } }
            if (resizeHandle.includes('n')) { y = orig.y + dy; h = orig.height - dy; if (h < MIN) { y = orig.y + orig.height - MIN; h = MIN; } }
            x = Math.max(0, Math.min(W - MIN, x)); y = Math.max(0, Math.min(H - MIN, y));
            w = Math.min(w, W - x); h = Math.min(h, H - y);
            entry.box = { x: Math.round(x), y: Math.round(y), width: Math.round(w), height: Math.round(h), pageNumber: currentPage };
        }
        redraw(); updateCoordReadout(entries, activeIdx);
    }
});

canvas.addEventListener('mouseup', e => {
    if (!mode) return;
    const p = getPos(e);
    const sc = getScale();

    if (mode === 'create') {
        const w = Math.abs(p.x - startX), h = Math.abs(p.y - startY);
        if (w >= 6 && h >= 6) {
            pendingBox = {
                x: Math.round(Math.min(startX, p.x) * sc),
                y: Math.round(Math.min(startY, p.y) * sc),
                width: Math.round(w * sc), height: Math.round(h * sc),
                pageNumber: currentPage
            };
            openNameModal();
        }
    }
    if (mode === 'move' || mode === 'resize') { rerender(); saveToLocal(); }
    mode = null; resizeHandle = null; dragOrigin = null;
    redraw();
});

canvas.addEventListener('dblclick', e => {
    const hit = hitTest(getPos(e), pageEntries());

    const p = getPos(e);
    const hitLocal = hitTest(p.x, p.y, pageEntries());
    if (hitLocal < 0) return;
    const entry = pageEntries()[hitLocal];
    const newName = prompt('Имя поля:', entry.name);
    if (newName?.trim()) { entry.name = newName.trim(); rerender(); redraw(); saveToLocal(); }
});


window.addEventListener('keydown', e => {
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) return;

    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {

        const pe = pageEntries();
        if (!pe.length) return;
        const lastGlobal = entries.lastIndexOf(pe.at(-1));
        if (lastGlobal >= 0) {
            entries.splice(lastGlobal, 1);
            if (activeIdx >= lastGlobal) activeIdx = Math.max(-1, activeIdx - 1);
            rerender(); redraw(); saveToLocal();
        }
        return;
    }
    if (e.key === 'Escape') { activeIdx = -1; closeNameModal(); rerender(); redraw(); }
    if (e.key === 'ArrowLeft' && !e.shiftKey) goPagePrev();
    if (e.key === 'ArrowRight' && !e.shiftKey) goPageNext();
});


$('#btnUndo').addEventListener('click', () => {
    const pe = pageEntries();
    if (!pe.length) return;
    const lastGlobal = entries.lastIndexOf(pe.at(-1));
    if (lastGlobal >= 0) {
        entries.splice(lastGlobal, 1);
        if (activeIdx >= lastGlobal) activeIdx = Math.max(-1, activeIdx - 1);
        rerender(); redraw(); saveToLocal();
    }
});
$('#btnClear').addEventListener('click', () => {
    const pe = pageEntries();
    if (!pe.length) return;
    entries = entries.filter(e => e.pageNumber !== currentPage);
    activeIdx = -1;
    rerender(); redraw(); saveToLocal();
    toast('Поля страницы очищены', '');
});
$('#btnZoomIn').addEventListener('click', () => { setZoom(Math.min(2, zoom + 0.1)); applyZoom(zoom); });
$('#btnZoomOut').addEventListener('click', () => { setZoom(Math.max(0.4, zoom - 0.1)); applyZoom(zoom); });
$('#btnZoomFit').addEventListener('click', () => { setZoom(1); applyZoom(1); });
$('#btnPagePrev')?.addEventListener('click', goPagePrev);
$('#btnPageNext')?.addEventListener('click', goPageNext);


function openNameModal() {
    $('#nameInput').value = '';
    document.querySelector('input[name="ftype"][value="TEXT"]').checked = true;
    $('#nameModal').classList.add('open');
    setTimeout(() => $('#nameInput').focus(), 30);
}
function closeNameModal() { $('#nameModal').classList.remove('open'); pendingBox = null; redraw(); }

$('#nameCancel').addEventListener('click', closeNameModal);
$('#nameModal').addEventListener('click', e => { if (e.target === e.currentTarget) closeNameModal(); });
$('#nameForm').addEventListener('submit', e => {
    e.preventDefault();
    const name = $('#nameInput').value.trim();
    if (!name) return;
    const type = document.querySelector('input[name="ftype"]:checked').value;
    entries.push({ name, type, padding: 5, pageNumber: currentPage, box: pendingBox });
    pendingBox = null;
    $('#nameModal').classList.remove('open');
    rerender(); redraw(); saveToLocal();
});


$('#templateName').addEventListener('input', () => { saveToLocal(); updateSaveBtn(entries); });

$('#btnSave').addEventListener('click', async () => {
    const name = $('#templateName').value.trim();
    if (!name || !entries.length) return;

    const btn = $('#btnSave');
    const orig = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="animation:spin 1s linear infinite"><circle cx="12" cy="12" r="10" stroke-opacity="0.25"/><path d="M22 12a10 10 0 0 0-10-10"/></svg> Сохраняем…';

    try {
        const baseWidth = pages[0]?.width || img.naturalWidth || 0;
        const baseHeight = pages[0]?.height || img.naturalHeight || 0;
        const dto = { name, baseWidth, baseHeight, entries };

        if (savedTemplateId) {

            const r = await fetch(`/api/templates/${savedTemplateId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, entries })
            });
            if (!r.ok) throw new Error('HTTP ' + r.status);
        } else if (uploadedFile) {

            const fd = new FormData();
            fd.append('dto', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
            fd.append('referenceFile', uploadedFile);
            const r = await fetch('/api/templates/with-reference', { method: 'POST', body: fd });
            if (!r.ok) throw new Error('HTTP ' + r.status);
            const created = await r.json();
            savedTemplateId = created.id;
        } else {

            const r = await fetch('/api/templates', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(dto)
            });
            if (!r.ok) throw new Error('HTTP ' + r.status);
        }

        toast('Шаблон сохранён', 'success');
        localStorage.removeItem('draft_name');
        localStorage.removeItem('draft_entries');
        localStorage.removeItem('draft_img');

        savedAndDone = true;
        setTimeout(() => { window.location.href = '/templates'; }, 700);
    } catch {
        btn.disabled = false;
        btn.innerHTML = orig;
        toast('Не удалось сохранить', 'error');
    }
});


let savedAndDone = false;
window.addEventListener('beforeunload', () => {
    if (savedTemplateId && !savedAndDone) {

        navigator.sendBeacon(`/api/templates/${savedTemplateId}/discard`);
    }
});


window.addEventListener('resize', () => {
    if (!img.src) return;
    canvas.width = img.clientWidth;
    canvas.height = img.clientHeight;
    setScale(img.naturalWidth / img.clientWidth);
    redraw();
});
