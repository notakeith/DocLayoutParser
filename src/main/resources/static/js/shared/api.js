

async function ok(r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r;
}

export const fetchTemplates = () =>
    fetch('/api/templates').then(ok).then(r => r.json());

export const deleteTemplate = id =>
    fetch(`/api/templates/${id}`, { method: 'DELETE' }).then(ok);

export const saveTemplate = payload =>
    fetch('/api/templates', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).then(ok).then(r => r.json());

export async function processRecognition(file, templateId, { onProgress, pollInterval = 1500, timeout = 120_000 } = {}) {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('templateId', templateId);

    const { jobId } = await fetch('/api/recognition/submit', { method: 'POST', body: fd })
        .then(ok).then(r => r.json());

    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        await new Promise(r => setTimeout(r, pollInterval));
        const job = await fetch(`/api/jobs/${jobId}`).then(ok).then(r => r.json());
        onProgress?.(job);
        if (job.status === 'DONE') return job;
        if (job.status === 'FAILED') throw new Error(job.errorMessage || 'Recognition failed');
    }
    throw new Error('Timeout: recognition took too long');
}
