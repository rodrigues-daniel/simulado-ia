// ── API Client Central ──────────────────────────────────────────────────
const API = {
    BASE: '/api',

    async get(path) {
        const res = await fetch(`${this.BASE}${path}`);
        if (!res.ok) {
            const err = await res.text().catch(() => '');
            console.error(`[API] GET ${path} falhou ${res.status}:`, err);
            throw new Error(`${res.status}: ${err || 'Erro desconhecido'}`);
        }
        return res.json();
    },

    async post(path, body) {
        const res = await fetch(`${this.BASE}${path}`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify(body)
        });
        if (!res.ok) {
            let errorDetail = '';
            try {
                const ct = res.headers.get('content-type') || '';
                errorDetail = ct.includes('application/json')
                    ? JSON.stringify(await res.json())
                    : await res.text();
            } catch (_) {}
            console.error(`[API] POST ${path} falhou ${res.status}:`, errorDetail);
            throw new Error(`${res.status}: ${errorDetail || 'Erro desconhecido'}`);
        }
        return res.json();
    },

    async put(path, body) {
        const res = await fetch(`${this.BASE}${path}`, {
            method:  'PUT',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify(body)
        });
        if (!res.ok) {
            const err = await res.text().catch(() => '');
            throw new Error(`${res.status}: ${err}`);
        }
        return res.json();
    },

    async patch(path, params = {}) {
        const qs  = new URLSearchParams(params).toString();
        const url = qs ? `${this.BASE}${path}?${qs}` : `${this.BASE}${path}`;
        const res = await fetch(url, { method: 'PATCH' });
        if (!res.ok) throw new Error(`PATCH ${path} → ${res.status}`);
        return res.ok;
    },

    async del(path) {
        const res = await fetch(`${this.BASE}${path}`, { method: 'DELETE' });
        if (!res.ok) throw new Error(`DELETE ${path} → ${res.status}`);
        return res.ok;
    },

    async uploadFile(path, file, extraParams = {}) {
        const form = new FormData();
        form.append('file', file);
        Object.entries(extraParams).forEach(([k, v]) => form.append(k, v));
        const res = await fetch(`${this.BASE}${path}`, {
            method: 'POST',
            body:   form
        });
        if (!res.ok) throw new Error(`UPLOAD ${path} → ${res.status}`);
        return res.json();
    }
};

// ── Helpers globais — disponíveis em todas as páginas ───────────────────

function showToast(msg, type = 'success') {
    const existing = document.querySelectorAll('.toast');
    existing.forEach(t => t.remove());

    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = msg;
    toast.style.cssText = `
        position: fixed;
        bottom: 24px;
        right: 24px;
        z-index: 9999;
        padding: 12px 20px;
        border-radius: 8px;
        font-size: 14px;
        font-weight: 600;
        color: #fff;
        box-shadow: 0 4px 12px rgba(0,0,0,.2);
        max-width: 360px;
        line-height: 1.4;
        background: ${
            type === 'success' ? '#16a34a' :
            type === 'error'   ? '#dc2626' : '#2563eb'
        };
    `;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
}

async function loadContestsIntoSelect(selectId) {
    try {
        const contests = await API.get('/admin/contests');
        const sel = document.getElementById(selectId);
        if (!sel) return contests;

        sel.innerHTML = contests.map(c =>
            `<option value="${c.id}" ${c.isDefault ? 'selected' : ''}>
                ${c.name} (${c.year})
             </option>`
        ).join('');

        return contests;
    } catch (e) {
        console.error('Erro ao carregar concursos:', e);
        return [];
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g,  '&amp;')
        .replace(/</g,  '&lt;')
        .replace(/>/g,  '&gt;')
        .replace(/"/g,  '&quot;')
        .replace(/'/g,  '&#39;');
}

function show(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'block';
}

function hide(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
}