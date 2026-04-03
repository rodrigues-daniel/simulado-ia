// ── API Client Central ──────────────────────────────────────────────────
const API = {
    BASE: '/api',

    async get(path) {
        const res = await fetch(`${this.BASE}${path}`);
        if (!res.ok) throw new Error(`GET ${path} → ${res.status}`);
        return res.json();
    },

    async post(path, body) {
        const res = await fetch(`${this.BASE}${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!res.ok) {
            const err = await res.text();
            throw new Error(`POST ${path} → ${res.status}: ${err}`);
        }
        return res.json();
    },

    async patch(path, params = {}) {
        const qs = new URLSearchParams(params).toString();
        const res = await fetch(`${this.BASE}${path}?${qs}`, { method: 'PATCH' });
        if (!res.ok) throw new Error(`PATCH ${path} → ${res.status}`);
        return res.ok;
    },

    async uploadFile(path, file, extraParams = {}) {
        const form = new FormData();
        form.append('file', file);
        Object.entries(extraParams).forEach(([k, v]) => form.append(k, v));
        const res = await fetch(`${this.BASE}${path}`, { method: 'POST', body: form });
        if (!res.ok) throw new Error(`UPLOAD ${path} → ${res.status}`);
        return res.json();
    }
};

// ── Helpers ─────────────────────────────────────────────────────────────
function showToast(msg, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = msg;
    toast.style.cssText = `
        position:fixed;bottom:24px;right:24px;z-index:9999;
        padding:12px 20px;border-radius:8px;font-size:14px;font-weight:600;
        background:${type === 'success' ? '#16a34a' : type === 'error' ? '#dc2626' : '#2563eb'};
        color:#fff;box-shadow:0 4px 12px rgba(0,0,0,.2);
        animation:slideIn .3s ease;
    `;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
}

async function loadContestsIntoSelect(selectId) {
    try {
        const contests = await API.get('/admin/contests');
        const sel = document.getElementById(selectId);
        if (!sel) return;
        sel.innerHTML = contests.map(c =>
            `<option value="${c.id}" ${c.isDefault ? 'selected' : ''}>
                ${c.name} (${c.year})
             </option>`
        ).join('');
        return contests;
    } catch (e) {
        console.error('Erro ao carregar concursos:', e);
    }
}

// ── Utilitários ──────────────────────────────────────────────────────
function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g,  '&amp;')
        .replace(/</g,  '&lt;')
        .replace(/>/g,  '&gt;')
        .replace(/"/g,  '&quot;')
        .replace(/'/g,  '&#39;');
}