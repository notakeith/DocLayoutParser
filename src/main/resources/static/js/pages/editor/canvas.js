
import { $ } from '../../shared/dom.js';

export const TYPE_COLORS = {
    TEXT: '#1E2A32',
    NUMERIC: '#B07A2A',
    DATE: '#6B5DBA',
    SIGNATURE: '#4A5D4E'
};

const HANDLE_R = 6;
const CURSOR_FOR_HANDLE = {
    nw: 'nwse-resize', n: 'ns-resize', ne: 'nesw-resize', e: 'ew-resize',
    se: 'nwse-resize', s: 'ns-resize', sw: 'nesw-resize', w: 'ew-resize'
};

export const canvas = $('#drawing-canvas');
export const ctx = canvas.getContext('2d');
export const img = $('#preview-img');

export let scale = 1;
export let zoom = 1;

export function setScale(v) { scale = v; }
export function getScale() { return scale; }
export function setZoom(v) { zoom = v; }

export function getPos(e) {
    const r = canvas.getBoundingClientRect();
    return {
        x: (e.clientX - r.left) * (canvas.width / r.width),
        y: (e.clientY - r.top) * (canvas.height / r.height)
    };
}

export function rectOf(entry) {
    const b = entry.box;
    return { x: b.x / scale, y: b.y / scale, w: b.width / scale, h: b.height / scale };
}

export function hitHandle(px, py, entry) {
    const r = rectOf(entry);
    const pts = [
        ['nw', r.x, r.y],
        ['n', r.x + r.w / 2, r.y],
        ['ne', r.x + r.w, r.y],
        ['e', r.x + r.w, r.y + r.h / 2],
        ['se', r.x + r.w, r.y + r.h],
        ['s', r.x + r.w / 2, r.y + r.h],
        ['sw', r.x, r.y + r.h],
        ['w', r.x, r.y + r.h / 2]
    ];
    for (const [name, hx, hy] of pts) {
        if (Math.abs(px - hx) <= HANDLE_R && Math.abs(py - hy) <= HANDLE_R) return name;
    }
    return null;
}

export function hitTest(x, y, entries) {
    for (let i = entries.length - 1; i >= 0; i--) {
        const r = rectOf(entries[i]);
        if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) return i;
    }
    return -1;
}

export function cursorForHandle(h) { return CURSOR_FOR_HANDLE[h]; }

export function drawAll(entries, activeIdx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    entries.forEach((entry, i) => {
        const b = entry.box;
        const c = TYPE_COLORS[entry.type] || TYPE_COLORS.TEXT;
        const { x, y, w, h } = rectOf(entry);
        const active = (i === activeIdx);

        const padDisp = (entry.padding || 0) / scale;
        if (padDisp > 0.5) {
            ctx.save();
            ctx.strokeStyle = c; ctx.globalAlpha = 0.45; ctx.lineWidth = 1;
            ctx.setLineDash([3, 3]);
            ctx.strokeRect(x - padDisp, y - padDisp, w + padDisp * 2, h + padDisp * 2);
            ctx.globalAlpha = 0.06; ctx.fillStyle = c;
            ctx.fillRect(x - padDisp, y - padDisp, w + padDisp * 2, padDisp);
            ctx.fillRect(x - padDisp, y + h, w + padDisp * 2, padDisp);
            ctx.fillRect(x - padDisp, y, padDisp, h);
            ctx.fillRect(x + w, y, padDisp, h);
            ctx.restore();
        }

        if (active) { ctx.fillStyle = c + '1F'; ctx.fillRect(x, y, w, h); }
        ctx.strokeStyle = c;
        ctx.lineWidth = active ? 2.5 : 2;
        ctx.setLineDash([]);
        ctx.strokeRect(x, y, w, h);


        ctx.font = '600 11px Inter, sans-serif';
        const label = (entry.name || '').toUpperCase();
        const tw = ctx.measureText(label).width + 14;
        const tagYBase = y - 18 - (padDisp > 0.5 ? padDisp : 0);
        const tagY = Math.max(0, tagYBase);
        ctx.fillStyle = c;
        ctx.fillRect(x, tagY, tw, 18);
        ctx.fillStyle = '#F4F2EF';
        ctx.fillText(label, x + 7, tagY + 13);


        if (active) {
            const pts = [
                [x, y], [x + w / 2, y],
                [x + w, y], [x + w, y + h / 2],
                [x + w, y + h], [x + w / 2, y + h],
                [x, y + h], [x, y + h / 2]
            ];
            pts.forEach(([hx, hy]) => {
                ctx.fillStyle = '#FFFFFF';
                ctx.strokeStyle = c;
                ctx.lineWidth = 1.5;
                ctx.beginPath();
                ctx.rect(hx - 4, hy - 4, 8, 8);
                ctx.fill(); ctx.stroke();
            });
        }
    });
}

export function drawSelecting(startX, startY, px, py) {
    drawAll([], -1);
    ctx.strokeStyle = TYPE_COLORS.TEXT;
    ctx.lineWidth = 2;
    ctx.setLineDash([6, 4]);
    ctx.strokeRect(startX, startY, px - startX, py - startY);
    ctx.setLineDash([]);
    const w = Math.abs(px - startX) * scale;
    const h = Math.abs(py - startY) * scale;
    const label = Math.round(w) + ' × ' + Math.round(h);
    ctx.font = '11px ui-monospace, monospace';
    const tw = ctx.measureText(label).width + 10;
    ctx.fillStyle = 'rgba(30,42,50,0.85)';
    ctx.fillRect(px + 6, py + 6, tw, 18);
    ctx.fillStyle = '#F4F2EF';
    ctx.fillText(label, px + 11, py + 19);
}

export function applyZoom(z) {
    const container = $('#canvas-container');
    container.style.transform = `scale(${z})`;
    $('#zoomDisplay').textContent = Math.round(z * 100) + '%';
}
