
import { $, escapeHtml } from '../../shared/dom.js';
import { drawAll } from './canvas.js';

export const TYPE_LABELS = { TEXT: 'Текст', NUMERIC: 'Число', DATE: 'Дата', SIGNATURE: 'Подпись' };

export function renderEntries(entries, currentPage, activeIdx, onActiveChange, onSave, onDelete) {
    const list = $('#entriesList');


    const pageItems = entries
        .map((e, globalIdx) => ({ e, globalIdx }))
        .filter(({ e }) => e.pageNumber === currentPage);


    const totalAll = entries.length;
    const totalPage = pageItems.length;
    $('#entryCount').textContent = totalPage;
    const countHint = $('#entryCountHint');
    if (countHint) {
        countHint.textContent = totalAll !== totalPage ? `/ ${totalAll} всего` : '';
    }

    if (pageItems.length === 0) {
        list.innerHTML = `
            <div class="entries-empty">
                <div class="icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="4" y="4" width="16" height="16" rx="2" stroke-dasharray="3 3"/></svg>
                </div>
                <em>Поля появятся здесь</em><br>
                Обведите рамкой нужные области на образе.
            </div>`;
        updateUndoBtn(entries, currentPage);
        updateSaveBtn(entries);
        return;
    }

    list.innerHTML = pageItems.map(({ e, globalIdx }, localIdx) => {
        const cls = e.type.toLowerCase();
        const active = globalIdx === activeIdx;
        return `
        <div class="entry-card ${active ? 'active' : ''}" data-gi="${globalIdx}" data-li="${localIdx}">
            <div class="entry-row">
                <div class="entry-marker ${cls}">${localIdx + 1}</div>
                <input class="entry-name" value="${escapeHtml(e.name)}" data-gi="${globalIdx}">
                <button class="entry-del" data-gi="${globalIdx}" title="Удалить">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
                </button>
            </div>
            <div class="entry-controls">
                <select class="entry-type" data-gi="${globalIdx}">
                    ${Object.keys(TYPE_LABELS).map(k =>
            `<option value="${k}" ${e.type === k ? 'selected' : ''}>${TYPE_LABELS[k]}</option>`
        ).join('')}
                </select>
                <div class="entry-pad-wrap">
                    <span>отступ</span>
                    <input class="entry-pad" type="number" value="${e.padding ?? 5}" min="0" max="40" data-gi="${globalIdx}">
                    <span>px</span>
                </div>
                <div class="entry-coord">${e.box.width}×${e.box.height}</div>
            </div>
        </div>`;
    }).join('');



    list.querySelectorAll('.entry-card').forEach(card => {
        card.addEventListener('click', e => {
            if (e.target.closest('input, select, button')) return;
            const gi = +card.dataset.gi;
            onActiveChange(gi);
            renderEntries(entries, currentPage, gi, onActiveChange, onSave, onDelete);
            drawAll(entries.filter(en => en.pageNumber === currentPage), localActiveIdx(entries, currentPage, gi));
        });
    });

    list.querySelectorAll('.entry-name').forEach(inp => {
        inp.addEventListener('input', e => {
            entries[+e.target.dataset.gi].name = e.target.value;
            drawAll(entries.filter(en => en.pageNumber === currentPage), localActiveIdx(entries, currentPage, activeIdx));
            onSave();
        });
    });

    list.querySelectorAll('.entry-type').forEach(sel => {
        sel.addEventListener('change', e => {
            entries[+e.target.dataset.gi].type = e.target.value;
            renderEntries(entries, currentPage, activeIdx, onActiveChange, onSave, onDelete);
            drawAll(entries.filter(en => en.pageNumber === currentPage), localActiveIdx(entries, currentPage, activeIdx));
            onSave();
        });
    });

    list.querySelectorAll('.entry-pad').forEach(inp => {
        inp.addEventListener('input', e => {
            entries[+e.target.dataset.gi].padding = parseInt(e.target.value || 0, 10);
            drawAll(entries.filter(en => en.pageNumber === currentPage), localActiveIdx(entries, currentPage, activeIdx));
        });
        inp.addEventListener('change', e => {
            entries[+e.target.dataset.gi].padding = parseInt(e.target.value || 0, 10);
            drawAll(entries.filter(en => en.pageNumber === currentPage), localActiveIdx(entries, currentPage, activeIdx));
            onSave();
        });
    });

    list.querySelectorAll('.entry-del').forEach(btn => {
        btn.addEventListener('click', e => { e.stopPropagation(); onDelete(+btn.dataset.gi); });
    });

    updateUndoBtn(entries, currentPage);
    updateSaveBtn(entries);
}

export function localActiveIdx(entries, currentPage, globalIdx) {
    if (globalIdx < 0) return -1;
    return entries
        .filter(e => e.pageNumber === currentPage)
        .findIndex((_, localI) => {
            const globalI = entries.indexOf(
                entries.filter(e => e.pageNumber === currentPage)[localI]
            );
            return globalI === globalIdx;
        });
}

export function updateSaveBtn(entries) {
    const imgEl = $('#preview-img');
    const hasImg = imgEl?.src && !imgEl.src.startsWith('data:,') && imgEl.src !== window.location.href;
    const ok = entries.length > 0 && $('#templateName').value.trim() && hasImg;
    $('#btnSave').disabled = !ok;
}

export function updateUndoBtn(entries, currentPage) {
    const hasOnPage = entries.some(e => e.pageNumber === currentPage);
    const btn = $('#btnUndo');
    if (btn) btn.disabled = !hasOnPage;
}

export function updateCoordReadout(entries, activeIdx) {
    if (activeIdx < 0) return;
    const card = document.querySelector(`.entry-card[data-gi="${activeIdx}"]`);
    const coord = card?.querySelector('.entry-coord');
    if (coord) coord.textContent = `${entries[activeIdx].box.width}×${entries[activeIdx].box.height}`;
}
