// ══════════════════════════════════════════════════════════════════════
// ── RAG UPLOAD MODULE — sem recursão ─────────────────────────────────
// ══════════════════════════════════════════════════════════════════════

const ALLOWED_EXTENSIONS = ['pdf', 'txt', 'csv'];
const MAX_FILES = 5;
const MAX_SIZE_MB = 50;

let selectedFiles = [];
let queueInterval = null;
let _cleaningPreviewActive = false; // controle para evitar loops

// ── Inicialização ────────────────────────────────────────────────────
async function initRagTab() {
    await loadRagConfig();
    await loadContestsIntoSelect('ragContestId');
    await loadRagDocuments();
    await loadQueueStatus();
    startQueuePolling();

    // Listener no input de chunk size
    const input = document.getElementById('chunkSizeKb');
    if (input) input.addEventListener('input', updateTokenEstimate);
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
        console.warn('[RAG] Config indisponível:', e.message);
    }
}

async function saveRagConfig() {
    const kb = parseInt(document.getElementById('chunkSizeKb')?.value);
    if (!kb || kb < 10) { showToast('Tamanho mínimo: 10 KB', 'error'); return; }
    try {
        await API.put('/rag/config', { chunkSizeKb: kb });
        showToast('Configuração salva!', 'success');
    } catch (e) {
        showToast('Erro ao salvar configuração', 'error');
    }
}

function updateTokenEstimate() {
    const kb = parseInt(document.getElementById('chunkSizeKb')?.value) || 50;
    const tokens = kb * 250;
    const el = document.getElementById('tokenEstimate');
    if (el) el.textContent = `~${tokens.toLocaleString('pt-BR')} tokens/chunk`;
}

// ── Drag & Drop ──────────────────────────────────────────────────────
function onDragOver(event) {
    event.preventDefault();
    document.getElementById('dropZone')?.classList.add('drag-over');
}

function onDragLeave() {
    document.getElementById('dropZone')?.classList.remove('drag-over');
}

function onDrop(event) {
    event.preventDefault();
    document.getElementById('dropZone')?.classList.remove('drag-over');
    const files = Array.from(event.dataTransfer.files);
    handleFileSelection(files);   // ← nome diferente para evitar colisão
}

function onFilesSelected(event) {
    const files = Array.from(event.target.files);
    handleFileSelection(files);   // ← nome diferente
}

// ── FUNÇÃO CENTRAL DE SELEÇÃO — sem recursão ─────────────────────────
// Renomeada para handleFileSelection. NÃO chama a si mesma.
function handleFileSelection(files) {
    if (!files || !files.length) return;

    // Limita a MAX_FILES
    const limited = files.slice(0, MAX_FILES);

    const validated = limited.map(file => ({
        file,
        valid: isValidFile(file),
        reason: getInvalidReason(file)
    }));

    selectedFiles = validated.filter(f => f.valid).map(f => f.file);

    renderFilesPreview(validated);
    updateUploadButton();

    // Preview de limpeza apenas para o primeiro arquivo válido
    // Protegido por flag para evitar chamadas duplicadas
    if (!_cleaningPreviewActive && selectedFiles.length > 0) {
        _cleaningPreviewActive = true;
        previewCleaning(selectedFiles[0]).finally(() => {
            _cleaningPreviewActive = false;
        });
    }
}

// ── Validação ────────────────────────────────────────────────────────
function isValidFile(file) {
    const ext = getExt(file.name);
    const sizeMb = file.size / 1024 / 1024;
    return ALLOWED_EXTENSIONS.includes(ext) && sizeMb <= MAX_SIZE_MB;
}

function getInvalidReason(file) {
    const ext = getExt(file.name);
    const sizeMb = file.size / 1024 / 1024;
    if (!ALLOWED_EXTENSIONS.includes(ext))
        return `Formato .${ext} não suportado`;
    if (sizeMb > MAX_SIZE_MB)
        return `Arquivo muito grande (${sizeMb.toFixed(1)} MB)`;
    return null;
}

function getExt(name) {
    if (!name || !name.includes('.')) return '';
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
}

function getFileIcon(name) {
    const ext = getExt(name || '');
    return ext === 'pdf' ? '📄' : ext === 'csv' ? '📊' : '📝';
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

// ── Preview de arquivos ──────────────────────────────────────────────
function renderFilesPreview(validated) {
    const preview = document.getElementById('filesPreview');
    const list = document.getElementById('filesList');
    const count = document.getElementById('filesCount');

    if (!validated.length) {
        if (preview) preview.style.display = 'none';
        return;
    }

    if (preview) preview.style.display = 'block';
    if (count) {
        const valid = validated.filter(f => f.valid).length;
        const invalid = validated.length - valid;
        count.textContent = `${valid} arquivo(s) válido(s)` +
            (invalid > 0 ? ` • ${invalid} inválido(s)` : '');
    }

    if (list) {
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
}

function clearFiles() {
    selectedFiles = [];
    const input = document.getElementById('ragFiles');
    if (input) input.value = '';
    const preview = document.getElementById('filesPreview');
    if (preview) preview.style.display = 'none';
    const cleanBox = document.getElementById('cleaningPreviewBox');
    if (cleanBox) cleanBox.style.display = 'none';
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

    const btn = document.getElementById('btnUpload');
    const resultEl = document.getElementById('uploadResult');
    const topicId = document.getElementById('ragTopicId')?.value;
    const contestId = document.getElementById('ragContestId')?.value;
    const chunkKb = parseInt(document.getElementById('chunkSizeKb')?.value) || 50;

    if (btn) { btn.disabled = true; btn.textContent = '⏳ Enviando...'; }
    if (resultEl) {
        resultEl.className = 'result-box';
        resultEl.textContent = `📤 Enviando ${selectedFiles.length} arquivo(s)...`;
    }

    try {
        const form = new FormData();
        selectedFiles.forEach(f => form.append('files', f));
        if (topicId) form.append('topicId', topicId);
        if (contestId) form.append('contestId', contestId);
        form.append('chunkSizeKb', chunkKb);

        const res = await fetch('/api/rag/upload', {
            method: 'POST',
            body: form,
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || `HTTP ${res.status}`);
        }

        const data = await res.json();

        if (resultEl) {
            resultEl.className = 'result-box success';
            resultEl.innerHTML =
                `✅ <strong>${data.queued} arquivo(s)</strong> enfileirado(s).<br>
                 <span style="font-size:12px;color:var(--text-muted)">
                 Processamento em background. Acompanhe na fila abaixo.</span>`;
        }

        clearFiles();
        await loadRagDocuments();
        await loadQueueStatus();
        startQueuePolling();

    } catch (e) {
        if (resultEl) {
            resultEl.className = 'result-box error';
            resultEl.textContent = `❌ Erro: ${e.message}`;
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = '🚀 Iniciar Upload e Processamento';
        }
    }
}

// ── Ingestão por texto ───────────────────────────────────────────────
async function ingestText() {
    const textarea = document.getElementById('ragTextPayload');
    const resultEl = document.getElementById('ragTextResult');

    if (!textarea?.value?.trim()) {
        showToast('Cole o JSON antes de ingerir', 'error');
        return;
    }

    if (resultEl) {
        resultEl.className = 'result-box';
        resultEl.textContent = '⏳ Processando...';
    }

    try {
        const payload = JSON.parse(textarea.value);
        const result = await API.post('/rag/ingest-text', payload);

        if (resultEl) {
            resultEl.className = 'result-box success';
            resultEl.textContent =
                `✅ "${result.name}" ingerido! ${result.totalChunks} chunks criados.`;
        }
        await loadRagDocuments();
        await loadQueueStatus();

    } catch (e) {
        if (resultEl) {
            resultEl.className = 'result-box error';
            resultEl.textContent = `❌ ${e.message.includes('JSON')
                ? 'JSON inválido. Verifique a sintaxe.'
                : e.message
                }`;
        }
    }
}

// ── Fila de processamento ────────────────────────────────────────────
async function loadQueueStatus() {
    try {
        const [items, stats] = await Promise.all([
            API.get('/rag/queue'),
            API.get('/rag/queue/stats'),
        ]);
        renderQueueStats(stats);
        renderQueueList(items);
    } catch (e) {
        console.warn('[RAG] Fila indisponível:', e.message);
    }
}

function renderQueueStats(stats) {
    const el = document.getElementById('queueStats');
    if (!el) return;
    const parts = [
        stats.pending ? `<span class="queue-stat-pill q-pending">⏳ ${stats.pending}</span>` : '',
        stats.processing ? `<span class="queue-stat-pill q-processing">⚙️ ${stats.processing}</span>` : '',
        stats.completed ? `<span class="queue-stat-pill q-completed">✅ ${stats.completed}</span>` : '',
        stats.error ? `<span class="queue-stat-pill q-error">❌ ${stats.error}</span>` : '',
    ].filter(Boolean);
    el.innerHTML = parts.join('') ||
        '<span style="color:var(--text-muted);font-size:12px">Fila vazia</span>';
}

function renderQueueList(items) {
    const list = document.getElementById('queueList');
    if (!list) return;

    if (!items?.length) {
        list.innerHTML =
            '<p style="color:var(--text-muted);font-size:13px">Nenhum item na fila.</p>';
        return;
    }

    list.innerHTML = items.map(item => {
        const pct = item.status === 'COMPLETED' ? 100
            : item.status === 'PROCESSING' ? 60
                : item.status === 'ERROR' ? 100 : 0;
        const color = item.status === 'COMPLETED' ? 'var(--success)'
            : item.status === 'ERROR' ? 'var(--danger)'
                : item.status === 'PROCESSING' ? 'var(--primary-light)'
                    : 'var(--border)';
        const emoji = {
            PENDING: '⏳', PROCESSING: '⚙️',
            COMPLETED: '✅', ERROR: '❌',
        }[item.status] || '❓';

        return `
        <div class="queue-item">
            <span style="font-size:16px">${emoji}</span>
            <div>
                <div class="queue-item-name"
                     title="${escapeHtml(item.fileName)}">
                    ${escapeHtml(item.fileName)}
                </div>
                ${item.errorMessage ? `
                <div style="font-size:11px;color:var(--danger);margin-top:2px">
                    ${escapeHtml(item.errorMessage)}
                </div>` : ''}
            </div>
            <div>
                <div class="queue-item-progress">
                    <div class="queue-item-progress-fill"
                         style="width:${pct}%;background:${color}"></div>
                </div>
                <div style="font-size:10px;color:var(--text-muted);
                            margin-top:2px;text-align:right">
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

function startQueuePolling() {
    if (queueInterval) clearInterval(queueInterval);
    queueInterval = setInterval(async () => {
        try {
            const stats = await API.get('/rag/queue/stats');
            renderQueueStats(stats);
            if (stats.processing > 0 || stats.pending > 0) {
                const items = await API.get('/rag/queue');
                renderQueueList(items);
                await loadRagDocuments();
            } else {
                clearInterval(queueInterval);
                queueInterval = null;
            }
        } catch (_) { }
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
                        <span class="badge ${d.status === 'COMPLETED' ? 'badge-success' :
                d.status === 'ERROR' ? 'badge-danger' :
                    d.status === 'PROCESSING' ? 'badge-info' :
                        'badge-warning'}">
                            ${{
                COMPLETED: '✅ Concluído',
                ERROR: '❌ Erro',
                PROCESSING: '⚙️ Processando',
                QUEUED: '⏳ Na fila',
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

// ── Tópicos por concurso ─────────────────────────────────────────────
async function loadRagTopics() {
    const contestId = document.getElementById('ragContestId')?.value;
    const sel = document.getElementById('ragTopicId');
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
        console.warn('[RAG] Tópicos:', e.message);
    }
}

// ── Preview de limpeza ───────────────────────────────────────────────
let currentCleaningResult = null;

async function previewCleaning(file) {
    if (!file) return;
    const previewBox = document.getElementById('cleaningPreviewBox');
    if (!previewBox) return;

    previewBox.style.display = 'block';
    previewBox.innerHTML = `
        <div class="cleaning-loading">
            <div class="processing-spinner"></div>
            <span>Analisando estratégia de limpeza...</span>
        </div>`;

    try {
        const form = new FormData();
        form.append('file', file);
        const res = await fetch('/api/rag/preview-cleaning', {
            method: 'POST',
            body: form,
        });
        if (!res.ok) {
            previewBox.style.display = 'none';
            return;
        }
        const result = await res.json();
        currentCleaningResult = result;
        renderCleaningPreview(result, file.name);
    } catch (e) {
        previewBox.style.display = 'none';
    }
}

function renderCleaningPreview(result, fileName) {
    const box = document.getElementById('cleaningPreviewBox');
    if (!box) return;

    const confidencePct = Math.round((result.confidenceScore || 0) * 100);
    const confColor = confidencePct >= 80 ? 'var(--success)'
        : confidencePct >= 65 ? 'var(--warning)'
            : 'var(--danger)';
    const strategyInfo = getStrategyInfo(result.strategyUsed);

    box.innerHTML = `
        <div class="cleaning-preview-header">
            <div>
                <div style="font-weight:700;font-size:14px">
                    🧹 Limpeza — ${escapeHtml(fileName)}
                </div>
                <div style="font-size:12px;color:var(--text-muted);margin-top:2px">
                    ${strategyInfo.label}
                </div>
            </div>
            <div class="cleaning-confidence">
                <div class="cleaning-conf-value" style="color:${confColor}">
                    ${confidencePct}%
                </div>
                <div class="cleaning-conf-label">Confiança</div>
            </div>
        </div>

        <div class="cleaning-strategy-badge"
             style="background:${strategyInfo.bg};color:${strategyInfo.color}">
            ${strategyInfo.icon} ${strategyInfo.label}
        </div>

        ${result.issuesFound?.length ? `
        <div class="cleaning-issues">
            ${result.issuesFound.map(issue => `
                <div class="cleaning-issue-item">
                    <span class="cleaning-issue-type">
                        ${getIssueEmoji(issue.type)} ${formatIssueType(issue.type)}
                    </span>
                    <span class="cleaning-issue-desc">
                        ${escapeHtml(issue.description)}
                    </span>
                    <span class="cleaning-issue-count">${issue.occurrences}x</span>
                </div>`).join('')}
        </div>` : ''}

        <div class="cleaning-stats-row">
            <div class="cleaning-stat">
                <span class="cleaning-stat-value">
                    ${formatBytes(result.stats?.originalChars || 0)}
                </span>
                <span class="cleaning-stat-label">Original</span>
            </div>
            <div style="color:var(--text-muted);font-size:18px">→</div>
            <div class="cleaning-stat">
                <span class="cleaning-stat-value" style="color:var(--success)">
                    ${formatBytes(result.stats?.cleanedChars || 0)}
                </span>
                <span class="cleaning-stat-label">Após limpeza</span>
            </div>
            <div class="cleaning-stat">
                <span class="cleaning-stat-value" style="color:var(--warning)">
                    -${(result.stats?.reductionPct || 0).toFixed(1)}%
                </span>
                <span class="cleaning-stat-label">Redução</span>
            </div>
        </div>

        <div style="display:flex;align-items:center;gap:10px;
                    margin-top:10px;flex-wrap:wrap">
            <label style="font-size:12px;font-weight:700;color:var(--text-muted)">
                Trocar estratégia:
            </label>
            <select id="strategyOverride" class="strategy-select"
                    onchange="applyStrategyOverride()">
                <option value="">-- Detectada automaticamente --</option>
                <option value="PDF_LEGAL">📜 PDF Jurídico</option>
                <option value="PDF_ACADEMIC">🎓 PDF Acadêmico</option>
                <option value="CSV_STRUCTURED">📊 CSV Estruturado</option>
                <option value="TXT_QUESTIONS">❓ Banco de Questões</option>
                <option value="TXT_PLAIN">📝 Texto Plano</option>
                <option value="GENERIC">🔧 Genérico</option>
            </select>
        </div>

        ${result.requiresManualReview ? `
        <div class="cleaning-manual-warning">
            ⚠️ <strong>Confiança baixa (${confidencePct}%).</strong>
            O editor manual será aberto após o upload.
        </div>` : `
        <div class="cleaning-auto-ok">
            ✅ Limpeza automática aprovada.
        </div>`}`;
}

async function applyStrategyOverride() {
    const strategy = document.getElementById('strategyOverride')?.value;
    if (!strategy || !currentCleaningResult) return;
    const box = document.getElementById('cleaningPreviewBox');
    if (box) box.innerHTML =
        '<div class="cleaning-loading"><div class="processing-spinner"></div>' +
        '<span>Aplicando estratégia...</span></div>';
    try {
        const result = await API.post('/rag/apply-cleaning', {
            content: currentCleaningResult.originalText,
            fileName: 'preview',
            strategy,
        });
        currentCleaningResult = result;
        renderCleaningPreview(result, 'preview');
    } catch (e) {
        showToast('Erro ao aplicar estratégia', 'error');
    }
}

// ── Editor manual ────────────────────────────────────────────────────
let currentCleaningDocId = null;

function openManualEditor(documentId, fileName) {
    currentCleaningDocId = documentId;
    API.get(`/rag/documents/${documentId}/cleaning`).then(data => {
        renderManualEditorModal(data, fileName);
    }).catch(() => {
        showToast('Erro ao carregar conteúdo para edição', 'error');
    });
}

function renderManualEditorModal(cleaningData, fileName) {
    const modal = document.getElementById('cleaningEditorModal');
    const body = document.getElementById('cleaningEditorBody');
    const title = document.getElementById('cleaningEditorTitle');

    if (title) title.textContent = `✏️ Revisão Manual — ${escapeHtml(fileName || '')}`;

    const confidencePct = Math.round((cleaningData.confidenceScore || 0) * 100);
    const stratInfo = getStrategyInfo(cleaningData.strategyUsed);

    if (body) body.innerHTML = `
        <div class="editor-warning-bar">
            ⚠️ Confiança: <strong>${confidencePct}%</strong> —
            Estratégia: <strong>${stratInfo.label}</strong>
        </div>
        <div class="editor-toolbar">
            <button class="btn btn-secondary btn-sm" onclick="editorReset()">
                ↩ Resetar
            </button>
            <button class="btn btn-secondary btn-sm"
                    onclick="editorRemoveBlankLines()">
                🗑️ Remover linhas em branco
            </button>
            <button class="btn btn-secondary btn-sm"
                    onclick="editorNormalizeSpaces()">
                ⎵ Normalizar espaços
            </button>
            <span id="editorCharCount"
                  style="font-size:12px;color:var(--text-muted);margin-left:auto">
                ${(cleaningData.cleanedContent || '').length} chars
            </span>
        </div>
        <textarea id="cleaningEditor" class="cleaning-editor-textarea"
                  oninput="updateEditorCharCount()">
${escapeHtml(cleaningData.cleanedContent || '')}</textarea>
        <div class="editor-actions">
            <button class="btn btn-secondary" onclick="closeCleaningEditor()">
                Cancelar
            </button>
            <button class="btn btn-primary" onclick="approveCleaningAndProcess()">
                ✅ Aprovar e Vetorizar
            </button>
        </div>`;

    if (body) body.dataset.original = cleaningData.cleanedContent || '';
    if (modal) modal.classList.add('open');
    document.body.style.overflow = 'hidden';
}

function editorReset() {
    const ta = document.getElementById('cleaningEditor');
    const original = document.getElementById('cleaningEditorBody')?.dataset.original;
    if (ta) ta.value = original || '';
    updateEditorCharCount();
}

function editorRemoveBlankLines() {
    const ta = document.getElementById('cleaningEditor');
    if (ta) { ta.value = ta.value.replace(/\n{3,}/g, '\n\n'); updateEditorCharCount(); }
}

function editorNormalizeSpaces() {
    const ta = document.getElementById('cleaningEditor');
    if (ta) {
        ta.value = ta.value.replace(/[ \t]+/g, ' ').replace(/^ +/gm, '');
        updateEditorCharCount();
    }
}

function updateEditorCharCount() {
    const ta = document.getElementById('cleaningEditor');
    const count = document.getElementById('editorCharCount');
    if (ta && count) count.textContent = `${ta.value.length} chars`;
}

async function approveCleaningAndProcess() {
    const ta = document.getElementById('cleaningEditor');
    if (!ta || !currentCleaningDocId) return;
    const chunkKb = parseInt(document.getElementById('chunkSizeKb')?.value) || 50;
    try {
        await API.post(
            `/rag/documents/${currentCleaningDocId}/approve-cleaning`,
            { editedContent: ta.value, chunkSizeKb: chunkKb, topicId: null }
        );
        closeCleaningEditor();
        showToast('Conteúdo aprovado! Vetorização iniciada.', 'success');
        await loadRagDocuments();
        await loadQueueStatus();
        startQueuePolling();
    } catch (e) {
        showToast('Erro: ' + e.message, 'error');
    }
}

function closeCleaningEditor() {
    document.getElementById('cleaningEditorModal')?.classList.remove('open');
    document.body.style.overflow = '';
    currentCleaningDocId = null;
}

function closeCleaningEditorOutside(e) {
    if (e.target.id === 'cleaningEditorModal') closeCleaningEditor();
}

// ── Helpers ──────────────────────────────────────────────────────────
function getStrategyInfo(strategy) {
    const map = {
        PDF_LEGAL: {
            label: 'PDF Jurídico/Legal', icon: '📜',
            bg: '#eff6ff', color: '#1e40af', desc: ''
        },
        PDF_ACADEMIC: {
            label: 'PDF Acadêmico', icon: '🎓',
            bg: '#f0fdf4', color: '#166534', desc: ''
        },
        CSV_STRUCTURED: {
            label: 'CSV Estruturado', icon: '📊',
            bg: '#fef9c3', color: '#854d0e', desc: ''
        },
        TXT_QUESTIONS: {
            label: 'Banco de Questões', icon: '❓',
            bg: '#ede9fe', color: '#5b21b6', desc: ''
        },
        TXT_PLAIN: {
            label: 'Texto Plano', icon: '📝',
            bg: '#f1f5f9', color: '#475569', desc: ''
        },
        GENERIC: {
            label: 'Limpeza Genérica', icon: '🔧',
            bg: '#f1f5f9', color: '#475569', desc: ''
        },
    };
    return map[strategy] || map.GENERIC;
}

function getIssueEmoji(type) {
    const map = {
        CONTROL_CHARS: '🔣', PDF_PAGE_ARTIFACTS: '📄',
        BIBLIOGRAPHY_REMOVED: '📚', INLINE_GABARITO: '🎯',
        DUPLICATE_LINES: '🔁', EMPTY_ROWS: '⬜', STOP_WORDS: '🚫',
    };
    return map[type] || '⚠️';
}

function formatIssueType(type) {
    return type.replace(/_/g, ' ').toLowerCase()
        .replace(/^\w/, c => c.toUpperCase());
}

function formatBytes(chars) {
    if (chars < 1000) return chars + ' chars';
    if (chars < 100000) return (chars / 1000).toFixed(1) + 'K chars';
    return (chars / 1000000).toFixed(2) + 'M chars';
}