
export const $ = (s, p = document) => p.querySelector(s);
export const $$ = (s, p = document) => [...p.querySelectorAll(s)];

export function escapeHtml(s) {
    return (s == null ? '' : String(s)).replace(
        /[&<>"']/g,
        m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m])
    );
}

export function toast(msg, kind = '') {
    const host = document.getElementById('toasts');
    if (!host) return;
    const t = document.createElement('div');
    t.className = 'toast ' + kind;
    t.textContent = msg;
    host.appendChild(t);
    setTimeout(() => {
        t.style.animation = 'toastOut 240ms forwards';
        setTimeout(() => t.remove(), 260);
    }, 2400);
}

export function prettySize(b) {
    if (b < 1024) return b + ' B';
    if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
    return (b / 1048576).toFixed(1) + ' MB';
}

export function download(name, content, mime) {
    const url = URL.createObjectURL(new Blob([content], { type: mime }));
    Object.assign(document.createElement('a'), { href: url, download: name }).click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function copyText(txt) {
    navigator.clipboard.writeText(txt).then(
        () => toast('Скопировано', 'success'),
        () => toast('Не удалось скопировать', 'error')
    );
}
