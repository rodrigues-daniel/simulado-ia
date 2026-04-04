// ══════════════════════════════════════════════════════════════════════
// ── RAG UPLOAD MODULE ─────────────────────────────────────════════════
// ══════════════════════════════════════════════════════════════════════

const ALLOWED_EXTENSIONS = ['pdf', 'txt', 'csv'];
const MAX_FILES          = 5;
const MAX_SIZE_MB        = 50;

let selectedFiles = [];
let queueInterval = null;

// ── Inicialização ────────────────────────────────────────────────────
async function initRagTab() {
    await loadRagConfig();
    await loadContestsIntoSelect('ragContestId');
    await loadRagDocuments();
    await loadQueueStatus();

    // Atualiza fila a cada 5s se houver itens processando
    startQueuePolling();
}

async function loadRagConfig() {
    try {
        const cfg = await API.get('/rag/config');
        const input = document.getElementById('chunkSizeKb');
        if (input) {
            input.value = cfg.chunkSizeKb || 50;
            updateTokenEstimate();
        }
    } catch (e) {
        console.warn('Config RAG não carregada:', e.message);
    }
}

async function saveRagConfig() {
    const kb = parseInt(document.getElementById('chunkSizeKb').value);
    if (!kb || kb < 10) {
        showToast('Tamanho mínimo: 10 KB', 'error'); return;
    }
    try {
        await API.put('/rag/config', { chunkSizeKb: kb });
        showToast('Configuração salva como padrão!', 'success');
    } catch (e) {
        showToast('Erro ao salvar configuração', 'error');
    }
}

function updateTokenEstimate() {
    const kb     = parseInt(document.getElementById('chunkSizeKb')?.value) || 50;
    const tokens = kb * 250;
    const el     = document.getElementById('tokenEstimate');
    if (el) el.textContent = `~${tokens.toLocaleString('pt-BR')} tokens/chunk`;
}

// ── Drag & Drop ──────────────────────────────────────────────────────
function onDragOver(event) {
    event.preventDefault();
    document.getElementById('dropZone').classList.add('drag-over');
}

function onDragLeave(event) {
    document.getElementById('dropZone').classList.remove('drag-over');
}

function onDrop(event) {
    event.preventDefault();
    document.getElementById('dropZone').classList.remove('drag-over');
    const files = Array.from(event.dataTransfer.files);
    processFileSelection(files);
}

function onFilesSelected(event) {
    const files = Array.from(event.target.files);
    processFileSelection(files);
}

function processFileSelection(files) {
    // Valida e limita a 5 arquivos
    const validated = files.slice(0, MAX_FILES).map(file => ({
        file,
        valid:  isValidFile(file),
        reason: getInvalidReason(file)
    }));

    selectedFiles = validated.filter(f => f.valid).map(f => f.file);

    renderFilesPreview(validated);
    updateUploadButton();
}

function isValidFile(file) {
    const ext = getExt(file.name);
    const sizeMb = file.size / 1024 / 1024;
    return ALLOWED_EXTENSIONS.includes(ext) && sizeMb <= MAX_SIZE_MB;
}

function getInvalidReason(file) {
    const ext    = getExt(file.name);
    const sizeMb = file.size / 1024 / 1024;
    if (!ALLOWED_EXTENSIONS.includes(ext))
        return `Formato .${ext} não suportado`;
    if (sizeMb > MAX_SIZE_MB)
        return `Arquivo muito grande (${sizeMb.toFixed(1)}MB)`;
    return null;
}

function getExt(name) {
    if (!name || !name.includes('.')) return '';
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
}

function getFileIcon(name) {
    const ext = getExt(name);
    return ext === 'pdf' ? '📄' : ext === 'csv' ? '📊' : '📝';
}

function formatSize(bytes) {
    if (bytes < 1024)       return bytes + ' B';
    if (bytes < 1024*1024)  return (bytes/1024).toFixed(1) + ' KB';
    return (bytes/1024/1024).toFixed(1) + ' MB';
}

function renderFilesPreview(validated) {
    const preview = document.getElementById('filesPreview');
    const list    = document.getElementById('filesList');
    const count   = document.getElementById('filesCount');

    if (!validated.length) {
        preview.style.display = 'none';
        return;
    }

    preview.style.display = 'block';
    count.textContent =
        `${selectedFiles.length} arquivo(s) válido(s)` +
        (validated.length > selectedFiles.length
            ? ` • ${validated.length - selectedFiles.length} inválido(s)` : '');

    list.innerHTML = validated.map(({ file, valid, reason }) => `
        <div class="file-item">
            <span class="file-icon">${getFileIcon(file.name)}</span>
            <div class="file-info">
                <div class="file-name" title="${escapeHtml(file.name)}">
                    ${escapeHtml(file.name)}
                </div>
                <div class="file-meta">
                    ${formatSize(file.size)} •
                    ${getExt(file.name).toUpperCase()}
                    ${reason ? ` • ⚠️ ${reason}` : ''}
                </div>
            </div>
            <span class="file-status ${valid ? 'valid' : 'invalid'}">
                ${valid ? '✅ Válido' : '❌ Inválido'}
            </span>
        </div>
    `).join('');
}

function clearFiles() {
    selectedFiles = [];
    document.getElementById('ragFiles').value = '';
    document.getElementById('filesPreview').style.display = 'none';
    updateUploadButton();
}

function updateUploadButton() {
    const btn = document.getElementById('btnUpload');
    if (btn) btn.disabled = selectedFiles.length === 0;
}

// ── Upload ───────────────────────────────────────────────────────────
async function uploadFiles() {
    if (!selectedFiles.length) {
        showToast('Selecione ao menos um arquivo', 'error');
        return;
    }

    const btn      = document.getElementById('btnUpload');
    const resultEl = document.getElementById('uploadResult');
    const topicId  = document.getElementById('ragTopicId')?.value;
    const contestId= document.getElementById('ragContestId')?.value;
    const chunkKb  = parseInt(document.getElementById('chunkSizeKb')?.value) || 50;

    btn.disabled    = true;
    btn.textContent = '⏳ Enviando...';
    resultEl.className   = 'result-box';
    resultEl.textContent = `📤 Enviando ${selectedFiles.length} arquivo(s)...`;

    try {
        const form = new FormData();
        selectedFiles.forEach(f => form.append('files', f));
        if (topicId)   form.append('topicId',    topicId);
        if (contestId) form.append('contestId',  contestId);
        form.append('chunkSizeKb', chunkKb);

        const res = await fetch('/api/rag/upload', {
            method: 'POST', body: form
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.error || `HTTP ${res.status}`);
        }

        const data = await res.json();

        resultEl.className   = 'result-box success';
        resultEl.innerHTML   =
            `✅ <strong>${data.queued} arquivo(s)</strong> enfileirado(s).<br>` +
            `<span style="font-size:12px;color:var(--text-muted)">` +
            `Processamento em background. Acompanhe na fila abaixo.</span>`;

        clearFiles();
        await loadRagDocuments();
        await loadQueueStatus();
        startQueuePolling();

    } catch (e) {
        resultEl.className   = 'result-box error';
        resultEl.textContent = `❌ Erro: ${e.message}`;
    } finally {
        btn.disabled    = false;
        btn.textContent = '🚀 Iniciar Upload e Processamento';
    }
}

// ── Ingestão por texto ───────────────────────────────────────────────
async function ingestText() {
    const textarea = document.getElementById('ragTextPayload');
    const resultEl = document.getElementById('ragTextResult');

    if (!textarea?.value?.trim()) {
        showToast('Cole o JSON antes de ingerir', 'error'); return;
    }

    resultEl.className   = 'result-box';
    resultEl.textContent = '⏳ Processando...';

    try {
        const payload = JSON.parse(textarea.value);
        const result  = await API.post('/rag/ingest-text', payload);
        resultEl.className   = 'result-box success';
        resultEl.textContent =
            `✅ "${result.name}" ingerido! ${result.totalChunks} chunks criados.`;
        await loadRagDocuments();
        await loadQueueStatus();
    } catch (e) {
        resultEl.className   = 'result-box error';
        resultEl.textContent = `❌ ${e.message.includes('JSON')
            ? 'JSON inválido. Verifique a sintaxe.' : e.message}`;
    }
}

// ── Fila de processamento ────────────────────────────────────────────
async function loadQueueStatus() {
    try {
        const [items, stats] = await Promise.all([
            API.get('/rag/queue'),
            API.get('/rag/queue/stats')
        ]);
        renderQueueStats(stats);
        renderQueueList(items);
    } catch (e) {
        console.warn('Erro ao carregar fila:', e.message);
    }
}

function renderQueueStats(stats) {
    const el = document.getElementById('queueStats');
    if (!el) return;
    el.innerHTML = [
        stats.pending    ? `<span class="queue-stat-pill q-pending">⏳ ${stats.pending} aguardando</span>` : '',
        stats.processing ? `<span class="queue-stat-pill q-processing">⚙️ ${stats.processing} processando</span>` : '',
        stats.completed  ? `<span class="queue-stat-pill q-completed">✅ ${stats.completed} concluídos</span>` : '',
        stats.error      ? `<span class="queue-stat-pill q-error">❌ ${stats.error} com erro</span>` : ''
    ].filter(Boolean).join('') || '<span style="color:var(--text-muted);font-size:12px">Fila vazia</span>';
}

function renderQueueList(items) {
    const list = document.getElementById('queueList');
    if (!items?.length) {
        list.innerHTML =
            '<p style="color:var(--text-muted);font-size:13px">Nenhum item na fila.</p>';
        return;
    }

    list.innerHTML = items.map(item => {
        const pct = item.status === 'COMPLETED' ? 100
                  : item.status === 'PROCESSING' ? 60
                  : item.status === 'ERROR'       ? 100 : 0;

        const fillColor = item.status === 'COMPLETED' ? 'var(--success)'
                        : item.status === 'ERROR'      ? 'var(--danger)'
                        : item.status === 'PROCESSING' ? 'var(--primary-light)'
                        : 'var(--border)';

        const statusEmoji = {
            PENDING:    '⏳', PROCESSING: '⚙️',
            COMPLETED:  '✅', ERROR:      '❌'
        }[item.status] || '❓';

        return `
        <div class="queue-item">
            <span style="font-size:16px">${statusEmoji}</span>
            <div>
                <div class="queue-item-name" title="${escapeHtml(item.fileName)}">
                    ${escapeHtml(item.fileName)}
                </div>
                ${item.errorMessage
                    ? `<div style="font-size:11px;color:var(--danger);margin-top:2px">
                           ${escapeHtml(item.errorMessage)}
                       </div>` : ''}
            </div>
            <div>
                <div class="queue-item-progress">
                    <div class="queue-item-progress-fill"
                         style="width:${pct}%;background:${fillColor}"></div>
                </div>
                <div style="font-size:10px;color:var(--text-muted);margin-top:2px;text-align:right">
                    ${item.status}
                </div>
            </div>
            <div style="font-size:11px;color:var(--text-muted);text-align:right">
                ${item.createdAt
                    ? new Date(item.createdAt).toLocaleTimeString('pt-BR')
                    : '--'}
            </div>
        </div>`;
    }).join('');
}

// Polling automático enquanto houver itens processando
function startQueuePolling() {
    if (queueInterval) clearInterval(queueInterval);
    queueInterval = setInterval(async () => {
        const stats = await API.get('/rag/queue/stats').catch(() => null);
        if (!stats) return;
        renderQueueStats(stats);
        if (stats.processing > 0 || stats.pending > 0) {
            const items = await API.get('/rag/queue').catch(() => []);
            renderQueueList(items);
            await loadRagDocuments();
        } else {
            clearInterval(queueInterval);
            queueInterval = null;
        }
    }, 4000);
}

// ── Documentos ingeridos ─────────────────────────────────────────────
async function loadRagDocuments() {
    const container = document.getElementById('ragDocumentsList');
    if (!container) return;

    try {
        const docs = await API.get('/rag/documents');

        if (!docs?.length) {
            container.innerHTML =
                '<p style="color:var(--text-muted)">Nenhum documento ingerido ainda.</p>';
            return;
        }

        container.innerHTML = `
        <table style="width:100%;border-collapse:collapse;font-size:13px;margin-top:8px">
            <thead>
                <tr style="background:var(--bg)">
                    <th style="padding:10px 8px;text-align:left;
                               border-bottom:2px solid var(--border)">Nome</th>
                    <th style="padding:10px 8px;text-align:center;
                               border-bottom:2px solid var(--border)">Tipo</th>
                    <th style="padding:10px 8px;text-align:center;
                               border-bottom:2px solid var(--border)">Chunks</th>
                    <th style="padding:10px 8px;text-align:center;
                               border-bottom:2px solid var(--border)">Status</th>
                    <th style="padding:10px 8px;text-align:center;
                               border-bottom:2px solid var(--border)">Data</th>
                </tr>
            </thead>
            <tbody>
                ${docs.map(d => `
                <tr style="border-bottom:1px solid var(--border)">
                    <td style="padding:10px 8px;font-weight:500;max-width:280px;
                               overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
                        title="${escapeHtml(d.name)}">
                        ${getFileIcon(d.name)} ${escapeHtml(d.name)}
                    </td>
                    <td style="padding:10px 8px;text-align:center">
                        <span class="badge badge-info">${d.sourceType}</span>
                    </td>
                    <td style="padding:10px 8px;text-align:center;font-weight:700">
                        ${d.totalChunks}
                    </td>
                    <td style="padding:10px 8px;text-align:center">
                        <span class="badge ${
                            d.status === 'COMPLETED' ? 'badge-success' :
                            d.status === 'ERROR'     ? 'badge-danger'  :
                            d.status === 'PROCESSING'? 'badge-info'    :
                            'badge-warning'}">
                            ${{
                                COMPLETED:  '✅ Concluído',
                                ERROR:      '❌ Erro',
                                PROCESSING: '⚙️ Processando',
                                QUEUED:     '⏳ Na fila'
                            }[d.status] || d.status}
                        </span>
                    </td>
                    <td style="padding:10px 8px;text-align:center;
                               color:var(--text-muted)">
                        ${d.createdAt
                            ? new Date(d.createdAt).toLocaleDateString('pt-BR')
                            : '--'}
                    </td>
                </tr>`).join('')}
            </tbody>
        </table>`;
    } catch (e) {
        container.innerHTML =
            '<p style="color:var(--danger)">Erro ao carregar documentos.</p>';
    }
}

// ── Tópicos por concurso para o select do RAG ────────────────────────
async function loadRagTopics() {
    const contestId = document.getElementById('ragContestId')?.value;
    const sel       = document.getElementById('ragTopicId');
    if (!sel) return;

    if (!contestId) {
        sel.innerHTML = '<option value="">Selecione...</option>';
        return;
    }
    try {
        const topics = await API.get(`/admin/topics/${contestId}`);
        sel.innerHTML = '<option value="">Selecione o tópico...</option>' +
            topics.map(t =>
                `<option value="${t.id}">${t.name}</option>`
            ).join('');
    } catch (e) {
        console.warn('Erro ao carregar tópicos RAG:', e.message);
    }
}