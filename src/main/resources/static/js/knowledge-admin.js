// ══════════════════════════════════════════════════════════════════════
// ── BASE DE CONHECIMENTO — PIPELINE RAG ──────────────────────────────
// ══════════════════════════════════════════════════════════════════════

async function initKnowledgeAdmin() {
    await Promise.all([
        loadKnowledgeStats(),
        loadContestsIntoSelect('kContestId')
    ]);

    // Carrega tópicos quando concurso muda
    document.getElementById('kContestId')
        ?.addEventListener('change', loadKnowledgeTopics);
}

// ── Stats ────────────────────────────────────────────────────────────
async function loadKnowledgeStats() {
    try {
        const stats = await API.get('/knowledge/stats');
        renderKnowledgeStats(stats);
    } catch (e) {
        console.warn('Stats indisponíveis:', e.message);
    }
}

function renderKnowledgeStats(stats) {
    const el = document.getElementById('knowledgeStats');
    if (!el) return;
    el.innerHTML = `
        <div class="tv-summary-card">
            <span class="tv-summary-value">${stats.totalChunks || 0}</span>
            <span class="tv-summary-label">Chunks na Base</span>
        </div>
        <div class="tv-summary-card">
            <span class="tv-summary-value">${stats.cacheEntries || 0}</span>
            <span class="tv-summary-label">Cache Semântico</span>
        </div>
        <div class="tv-summary-card">
            <span class="tv-summary-value" style="color:var(--success)">
                ${calcCacheHitRate(stats.cacheTopHits)}%
            </span>
            <span class="tv-summary-label">Taxa de Cache Hit</span>
        </div>`;
}

function calcCacheHitRate(topHits) {
    if (!topHits?.length) return 0;
    const totalHits = topHits.reduce((s, h) => s + (h.hits || 0), 0);
    return totalHits > 0 ? Math.min(99, Math.round(totalHits / topHits.length)) : 0;
}

// ── Abas internas ────────────────────────────────────────────────────
function switchInnerTab(tabId) {
    document.querySelectorAll('.inner-tab-content')
        .forEach(c => c.style.display = 'none');
    document.querySelectorAll('.inner-tab')
        .forEach(t => t.classList.remove('active'));

    const content = document.getElementById(tabId);
    if (content) content.style.display = 'block';

    const btn = event?.target;
    if (btn) btn.classList.add('active');

    if (tabId === 'kt-cache') loadCacheStats();
    if (tabId === 'kt-index') initQuestionIndexing();
}

// ── Adicionar conhecimento ───────────────────────────────────────────
async function addKnowledge() {
    const conteudo = document.getElementById('kConteudo')?.value?.trim();
    const materia = document.getElementById('kMateria')?.value?.trim();
    const contestId = document.getElementById('kContestId')?.value;
    const topicoId = document.getElementById('kTopicoId')?.value;
    const fonte = document.getElementById('kFonte')?.value?.trim();
    const resultEl = document.getElementById('kAddResult');

    if (!conteudo) {
        showToast('Preencha o conteúdo', 'error'); return;
    }

    resultEl.className = 'result-box';
    resultEl.textContent = '⏳ Gerando embedding e indexando...';

    try {
        const result = await API.post('/knowledge', {
            conteudo,
            materia: materia || null,
            contestId: contestId ? parseInt(contestId) : null,
            topicoId: topicoId ? parseInt(topicoId) : null,
            fonte: fonte || null
        });

        resultEl.className = 'result-box success';
        resultEl.textContent =
            `✅ Conhecimento indexado! ID: ${result.id}`;

        // Limpa campos
        document.getElementById('kConteudo').value = '';
        document.getElementById('kFonte').value = '';
        await loadKnowledgeStats();

    } catch (e) {
        resultEl.className = 'result-box error';
        resultEl.textContent = `❌ Erro: ${e.message}`;
    }
}

async function loadKnowledgeTopics() {
    const contestId = document.getElementById('kContestId')?.value;
    const sel = document.getElementById('kTopicoId');
    if (!sel) return;

    if (!contestId) {
        sel.innerHTML = '<option value="">Selecione...</option>';
        return;
    }
    try {
        const topics = await API.get(`/admin/topics/${contestId}`);
        sel.innerHTML = '<option value="">Selecione o tópico...</option>' +
            topics.map(t =>
                `<option value="${t.id}">${t.name}</option>`).join('');
    } catch (e) { console.warn(e); }
}

// ── Busca semântica ──────────────────────────────────────────────────
async function searchKnowledge() {
    const query = document.getElementById('kSearchQuery')?.value?.trim();
    const materia = document.getElementById('kSearchMateria')?.value?.trim();
    const topK = document.getElementById('kSearchTopK')?.value || 5;

    if (!query) { showToast('Digite uma consulta', 'error'); return; }

    const container = document.getElementById('kSearchResults');
    container.innerHTML =
        '<div class="loading-spinner">Buscando...</div>';

    try {
        const url = `/knowledge/search?query=${encodeURIComponent(query)}` +
            `&topK=${topK}` +
            (materia ? `&materia=${encodeURIComponent(materia)}` : '');
        const results = await API.get(url);
        renderSearchResults(results);
    } catch (e) {
        container.innerHTML =
            `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

function renderSearchResults(results) {
    const container = document.getElementById('kSearchResults');

    if (!results?.length) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">🔍</div>
                <div class="empty-state-text">
                    Nenhum resultado. Adicione mais conhecimento na base.
                </div>
            </div>`;
        return;
    }

    container.innerHTML = results.map((r, i) => {
        const simPct = Math.round((r.similarity || 0) * 100);
        const simColor = simPct >= 80 ? 'var(--success)'
            : simPct >= 60 ? 'var(--warning)' : 'var(--danger)';
        return `
        <div class="knowledge-result-card">
            <div class="kr-header">
                <div class="kr-rank">#${i + 1}</div>
                <div>
                    <div class="kr-fonte">
                        ${escapeHtml(r.fonte || 'Sem fonte')}
                    </div>
                    <div class="kr-materia">
                        ${escapeHtml(r.materia || 'Matéria não definida')}
                    </div>
                </div>
                <div class="kr-similarity" style="color:${simColor}">
                    ${simPct}%
                    <div style="font-size:10px;font-weight:400;
                                color:var(--text-muted)">similar</div>
                </div>
            </div>
            <div class="kr-conteudo">${escapeHtml(r.conteudo)}</div>
            <div style="text-align:right;margin-top:8px">
                <button class="btn btn-sm"
                        style="background:#fee2e2;color:#991b1b;font-size:11px"
                        onclick="deleteKnowledge(${r.id})">
                    🗑️ Remover
                </button>
            </div>
        </div>`;
    }).join('');
}

async function deleteKnowledge(id) {
    if (!confirm('Remover este conhecimento da base?')) return;
    try {
        await fetch(`/api/knowledge/${id}`, { method: 'DELETE' });
        showToast('Removido!', 'success');
        await searchKnowledge();
        await loadKnowledgeStats();
    } catch (e) {
        showToast('Erro ao remover', 'error');
    }
}

// ── Testar pipeline ──────────────────────────────────────────────────
async function testPipeline() {
    const pergunta = document.getElementById('kTestPergunta')?.value?.trim();
    const materia = document.getElementById('kTestMateria')?.value?.trim();
    const resultEl = document.getElementById('kTestResult');

    if (!pergunta) { showToast('Digite uma pergunta', 'error'); return; }

    resultEl.style.display = 'block';
    resultEl.innerHTML = `
        <div class="pipeline-loading">
            <div class="processing-spinner"></div>
            <div>
                <div style="font-weight:600">Executando pipeline...</div>
                <div style="font-size:12px;color:var(--text-muted)">
                    Cache → RAG → Ollama
                </div>
            </div>
        </div>`;

    const start = Date.now();

    try {
        const result = await API.post('/knowledge/test-pipeline', {
            pergunta, materia: materia || null, topicoId: null
        });

        const elapsed = Date.now() - start;
        renderPipelineResult(result, elapsed);
        await loadKnowledgeStats();

    } catch (e) {
        resultEl.innerHTML =
            `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

function renderPipelineResult(result, elapsed) {
    const el = document.getElementById('kTestResult');

    const sourceConfig = {
        CACHE_SEMANTICO: { label: '⚡ Cache Semântico', color: '#16a34a', bg: '#dcfce7' },
        RAG_OLLAMA: { label: '🧠 RAG + Ollama', color: '#1e40af', bg: '#dbeafe' },
        OLLAMA_ONLY: { label: '🤖 Ollama (sem RAG)', color: '#854d0e', bg: '#fef9c3' },
        FALLBACK: { label: '⚠️ Fallback', color: '#991b1b', bg: '#fee2e2' }
    };
    const src = sourceConfig[result.source] || sourceConfig.OLLAMA_ONLY;

    el.innerHTML = `
        <div class="pipeline-result-box">
            <!-- Header com métricas -->
            <div class="pipeline-result-header">
                <span class="pipeline-source-badge"
                      style="background:${src.bg};color:${src.color}">
                    ${src.label}
                </span>
                <div class="pipeline-metrics">
                    <span>⏱️ ${result.elapsedMs || elapsed}ms</span>
                    ${result.ragChunksUsed > 0
            ? `<span>📚 ${result.ragChunksUsed} chunks RAG</span>` : ''}
                    ${result.cacheDistance > 0
            ? `<span>📏 dist: ${result.cacheDistance.toFixed(4)}</span>` : ''}
                </div>
            </div>

            <!-- Contexto RAG usado -->
            ${result.contextoUsado ? `
            <details style="margin-bottom:12px">
                <summary style="cursor:pointer;font-size:12px;font-weight:700;
                                color:var(--text-muted);padding:6px 0">
                    📄 Contexto RAG utilizado
                </summary>
                <div style="margin-top:8px;padding:12px;background:var(--bg);
                            border-radius:8px;font-size:12px;font-family:monospace;
                            max-height:150px;overflow-y:auto;white-space:pre-wrap">
                    ${escapeHtml(result.contextoUsado)}
                </div>
            </details>` : ''}

            <!-- Resposta -->
            <div style="font-size:13px;font-weight:700;
                        margin-bottom:8px;color:var(--text)">
                💬 Resposta
            </div>
            <div style="background:var(--surface2);padding:16px;border-radius:8px;
                        font-size:14px;line-height:1.7;white-space:pre-wrap">
                ${escapeHtml(result.resposta)}
            </div>
        </div>`;
}

// ── Cache semântico ──────────────────────────────────────────────────
async function loadCacheStats() {
    try {
        const stats = await API.get('/knowledge/stats');
        renderCacheList(stats.cacheTopHits || []);
        await loadKnowledgeStats();
    } catch (e) {
        console.warn('Cache stats:', e.message);
    }
}

function renderCacheList(topHits) {
    const list = document.getElementById('kCacheList');

    if (!topHits?.length) {
        list.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">⚡</div>
                <div class="empty-state-text">
                    Cache vazio. As respostas da IA serão salvas automaticamente.
                </div>
            </div>`;
        return;
    }

    list.innerHTML = topHits.map(h => `
        <div class="knowledge-result-card">
            <div class="kr-header">
                <div class="kr-rank" style="background:#fef9c3;color:#854d0e">
                    ${h.hits}x
                </div>
                <div style="flex:1;min-width:0">
                    <div style="font-size:13px;font-weight:600;
                                overflow:hidden;text-overflow:ellipsis;
                                white-space:nowrap">
                        ${escapeHtml(h.pergunta?.substring(0, 100) || '')}
                        ${(h.pergunta?.length || 0) > 100 ? '...' : ''}
                    </div>
                    <div style="font-size:11px;color:var(--text-muted)">
                        Criado: ${h.createdAt
            ? new Date(h.createdAt).toLocaleDateString('pt-BR')
            : '--'}
                        ${h.lastHitAt ? ` • Último hit: ${new Date(h.lastHitAt).toLocaleDateString('pt-BR')
            }` : ''}
                    </div>
                </div>
                <button class="btn btn-sm"
                        style="background:#fee2e2;color:#991b1b;font-size:11px"
                        onclick="deleteCache(${h.id})">
                    🗑️
                </button>
            </div>
        </div>
    `).join('');
}

async function deleteCache(id) {
    if (!confirm('Remover este item do cache?')) return;
    try {
        await fetch(`/api/knowledge/cache/${id}`, { method: 'DELETE' });
        showToast('Cache removido!', 'success');
        await loadCacheStats();
    } catch (e) {
        showToast('Erro', 'error');
    }
}

// ── Carga em massa ───────────────────────────────────────────────────
async function bulkAddKnowledge() {
    const textarea = document.getElementById('kBulkPayload');
    const resultEl = document.getElementById('kBulkResult');

    if (!textarea?.value?.trim()) {
        showToast('Cole o JSON antes de importar', 'error'); return;
    }

    resultEl.className = 'result-box';
    resultEl.textContent = '⏳ Indexando conhecimentos...';

    try {
        const payload = JSON.parse(textarea.value);
        const result = await API.post('/knowledge/bulk', payload);
        resultEl.className = 'result-box success';
        resultEl.textContent =
            `✅ ${result.saved}/${result.total} conhecimentos indexados!`;
        textarea.value = '';
        await loadKnowledgeStats();
    } catch (e) {
        resultEl.className = 'result-box error';
        resultEl.textContent = `❌ ${e.message.includes('JSON')
            ? 'JSON inválido' : e.message}`;
    }
}

function loadKnowledgeExample() {
    document.getElementById('kBulkPayload').value = JSON.stringify([
        {
            "conteudo": "Art. 37. A administração pública direta e indireta de qualquer dos Poderes da União, dos Estados, do Distrito Federal e dos Municípios obedecerá aos princípios de legalidade, impessoalidade, moralidade, publicidade e eficiência.",
            "materia": "Direito Constitucional",
            "fonte": "CF/1988, Art. 37, caput"
        },
        {
            "conteudo": "O princípio da legalidade, para a Administração Pública, significa que o administrador só pode fazer o que a lei expressamente autoriza, diferentemente do particular, que pode fazer tudo que a lei não proíbe.",
            "materia": "Direito Administrativo",
            "fonte": "Doutrina - Di Pietro"
        },
        {
            "conteudo": "Art. 41 da CF/88: São estáveis após três anos de efetivo exercício os servidores nomeados para cargo de provimento efetivo em virtude de concurso público.",
            "materia": "Direito Administrativo",
            "fonte": "CF/1988, Art. 41"
        }
    ], null, 2);
}


// ══════════════════════════════════════════════════════════════════════
// ── INDEXAÇÃO DE QUESTÕES NO PIPELINE RAG ─────────────────────────────
// ══════════════════════════════════════════════════════════════════════

let _indexContestId = null;
let _indexingActive = false;

// ── Inicializa aba de indexação ──────────────────────────────────────
async function initQuestionIndexing() {
    await loadContestsIntoSelect('idxContestSelect');
    const contests = await API.get('/admin/contests').catch(() => []);
    const def = contests.find(c => c.isDefault) || contests[0];
    if (def) {
        document.getElementById('idxContestSelect').value = def.id;
        _indexContestId = def.id;
        await loadIndexStatus();
    }
}

// ── Carrega status de indexação ──────────────────────────────────────
async function loadIndexStatus() {
    const contestId = document.getElementById('idxContestSelect')?.value;
    if (!contestId) return;
    _indexContestId = contestId;

    const container = document.getElementById('idxStatusContent');
    container.innerHTML =
        '<div class="loading-spinner">Carregando status...</div>';

    try {
        const data = await API.get(
            `/knowledge/questions/status/${contestId}`);
        renderIndexSummary(data.summary);
        renderIndexTable(data.questions);

        const pending = (data.questions || [])
            .filter(q => q.indexStatus !== 'INDEXED').length;
        const el = document.getElementById('idxPendingCount');
        if (el) el.textContent = pending;

    } catch (e) {
        container.innerHTML =
            `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

// ── Renderiza resumo ─────────────────────────────────────────────────
function renderIndexSummary(summary) {
    const el = document.getElementById('idxSummary');
    if (!el || !summary) return;

    const pctIndexed = summary.total > 0
        ? Math.round(summary.indexed / summary.total * 100) : 0;

    el.innerHTML = `
        <div class="idx-summary-grid">
            <div class="idx-summary-card total">
                <span class="idx-summary-value">${summary.total}</span>
                <span class="idx-summary-label">Total de Questões</span>
            </div>
            <div class="idx-summary-card indexed">
                <span class="idx-summary-value">${summary.indexed}</span>
                <span class="idx-summary-label">✅ Indexadas</span>
            </div>
            <div class="idx-summary-card pending">
                <span class="idx-summary-value">${summary.notIndexed}</span>
                <span class="idx-summary-label">⏳ Não Indexadas</span>
            </div>
            <div class="idx-summary-card outdated">
                <span class="idx-summary-value">${summary.outdated}</span>
                <span class="idx-summary-label">🔄 Desatualizadas</span>
            </div>
        </div>

        <!-- Barra de progresso geral -->
        <div style="margin-top:14px">
            <div style="display:flex;justify-content:space-between;
                        font-size:12px;margin-bottom:4px">
                <span style="font-weight:700;color:var(--text)">
                    Cobertura do Pipeline RAG
                </span>
                <span style="font-weight:800;color:var(--primary)">
                    ${pctIndexed}%
                </span>
            </div>
            <div style="height:10px;background:var(--border);
                        border-radius:6px;overflow:hidden">
                <div style="height:100%;width:${pctIndexed}%;
                            background:${pctIndexed >= 80
            ? 'var(--success)' : pctIndexed >= 50
                ? 'var(--warning)' : 'var(--danger)'};
                            border-radius:6px;transition:width .5s">
                </div>
            </div>
            ${(summary.notIndexed + summary.outdated) > 0 ? `
            <div style="margin-top:10px;font-size:13px;
                        color:var(--warning);font-weight:600">
                ⚠️ ${summary.notIndexed + summary.outdated} questão(ões)
                ainda não disponíveis para a IA.
            </div>` : `
            <div style="margin-top:10px;font-size:13px;
                        color:var(--success);font-weight:600">
                ✅ Todas as questões estão disponíveis no pipeline RAG.
            </div>`}
        </div>`;
}

// ── Renderiza tabela de questões com status ──────────────────────────
function renderIndexTable(questions) {
    const container = document.getElementById('idxStatusContent');

    if (!questions?.length) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📭</div>
                <div class="empty-state-text">
                    Nenhuma questão encontrada para este concurso.
                </div>
            </div>`;
        return;
    }

    // Agrupa por status para exibição ordenada
    const byStatus = {
        NOT_INDEXED: questions.filter(q => q.indexStatus === 'NOT_INDEXED'),
        OUTDATED: questions.filter(q => q.indexStatus === 'OUTDATED'),
        INDEXED: questions.filter(q => q.indexStatus === 'INDEXED'),
    };

    container.innerHTML = `
        <!-- Filtros rápidos -->
        <div class="idx-filter-row">
            <button class="idx-filter-btn active"
                    onclick="filterIdxTable('ALL', this)">
                Todas (${questions.length})
            </button>
            <button class="idx-filter-btn"
                    onclick="filterIdxTable('NOT_INDEXED', this)">
                ⏳ Não indexadas (${byStatus.NOT_INDEXED.length})
            </button>
            <button class="idx-filter-btn"
                    onclick="filterIdxTable('OUTDATED', this)">
                🔄 Desatualizadas (${byStatus.OUTDATED.length})
            </button>
            <button class="idx-filter-btn"
                    onclick="filterIdxTable('INDEXED', this)">
                ✅ Indexadas (${byStatus.INDEXED.length})
            </button>
        </div>

        <!-- Tabela -->
        <table class="idx-table" id="idxTable">
            <thead>
                <tr>
                    <th style="width:50px">#</th>
                    <th>Enunciado</th>
                    <th style="width:80px;text-align:center">Gabarito</th>
                    <th style="width:80px;text-align:center">Origem</th>
                    <th style="width:110px;text-align:center">Status</th>
                    <th style="width:130px;text-align:center">Indexado em</th>
                    <th style="width:100px;text-align:center">Ações</th>
                </tr>
            </thead>
            <tbody id="idxTableBody">
                ${questions.map(q => renderIdxRow(q)).join('')}
            </tbody>
        </table>`;
}

function renderIdxRow(q) {
    const statusConfig = {
        INDEXED: { cls: 'status-indexed', label: '✅ Indexada' },
        NOT_INDEXED: { cls: 'status-not-indexed', label: '⏳ Pendente' },
        OUTDATED: { cls: 'status-outdated', label: '🔄 Desatualiz.' },
    };
    const sc = statusConfig[q.indexStatus] || statusConfig.NOT_INDEXED;

    const indexedAt = q.indexedAt
        ? new Date(q.indexedAt).toLocaleDateString('pt-BR')
        : '—';

    const canSend = q.indexStatus !== 'INDEXED';

    return `
    <tr class="idx-row" data-status="${q.indexStatus}">
        <td style="font-size:12px;color:var(--text-muted);text-align:center">
            ${q.questionId}
        </td>
        <td>
            <div class="idx-statement" title="${escapeHtml(q.statement)}">
                ${escapeHtml(q.statement?.substring(0, 90) || '')}
                ${(q.statement?.length || 0) > 90 ? '...' : ''}
            </div>
        </td>
        <td style="text-align:center">
            <span class="${q.correctAnswer
            ? 'answer-badge-certo' : 'answer-badge-errado'}">
                ${q.correctAnswer ? 'CERTO' : 'ERRADO'}
            </span>
        </td>
        <td style="text-align:center;font-size:11px">
            ${q.source === 'IA-GERADA'
            ? '<span style="color:#5b21b6;font-weight:700">🤖 IA</span>'
            : '<span style="color:#1e40af;font-weight:700">📝 Manual</span>'}
        </td>
        <td style="text-align:center">
            <span class="idx-status-badge ${sc.cls}">${sc.label}</span>
        </td>
        <td style="text-align:center;font-size:12px;color:var(--text-muted)">
            ${indexedAt}
            ${q.chunksCreated ? `<br><span style="font-size:10px">
                ${q.chunksCreated} chunk(s)</span>` : ''}
        </td>
        <td style="text-align:center">
            <div style="display:flex;gap:4px;justify-content:center;flex-wrap:wrap">
                ${canSend ? `
                <button class="btn btn-sm"
                        style="background:var(--primary-light);color:#fff;
                               font-size:11px;padding:4px 8px"
                        onclick="indexOneQuestion(${q.questionId}, this)"
                        title="Enviar para o pipeline RAG">
                    📤 Enviar
                </button>` : `
                <button class="btn btn-sm"
                        style="background:var(--border);color:var(--text-muted);
                               font-size:11px;padding:4px 8px"
                        disabled
                        title="Já indexada — sem modificações">
                    ✅ Enviada
                </button>`}
                ${q.indexStatus === 'INDEXED' ? `
                <button class="btn btn-sm"
                        style="background:#fff7ed;color:#c2410c;
                               border:1px solid #fed7aa;
                               font-size:10px;padding:3px 6px"
                        onclick="resetIndex(${q.questionId})"
                        title="Remover do índice (permite re-envio)">
                    ↩
                </button>` : ''}
            </div>
        </td>
    </tr>`;
}

// ── Filtro da tabela por status ───────────────────────────────────────
function filterIdxTable(status, btn) {
    document.querySelectorAll('.idx-filter-btn')
        .forEach(b => b.classList.remove('active'));
    btn.classList.add('active');

    document.querySelectorAll('.idx-row').forEach(row => {
        row.style.display =
            status === 'ALL' || row.dataset.status === status
                ? '' : 'none';
    });
}

// ── Indexa todas as questões pendentes ────────────────────────────────
async function indexAllPending() {
    if (!_indexContestId) return;
    if (_indexingActive) {
        showToast('Indexação já em andamento...', 'error');
        return;
    }

    const pendingEl = document.getElementById('idxPendingCount');
    const count = parseInt(pendingEl?.textContent || '0');

    if (count === 0) {
        showToast('Nenhuma questão pendente de indexação.', 'error');
        return;
    }

    if (!confirm(`Enviar ${count} questão(ões) para o pipeline RAG?\n\n` +
        `Questões já indexadas e sem modificações serão ignoradas.`)) {
        return;
    }

    _indexingActive = true;
    const btn = document.getElementById('btnIndexAll');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML =
            '<span class="mini-spinner"></span> Indexando...';
    }

    showIndexProgress(true);

    try {
        const result = await API.post(
            `/knowledge/questions/index/${_indexContestId}`, {});
        renderIndexResult(result);
        await loadIndexStatus();
        showToast(
            `✅ ${result.indexed} indexadas, ` +
            `${result.skipped} ignoradas, ${result.failed} falhas.`,
            'success'
        );
    } catch (e) {
        showToast('Erro na indexação: ' + e.message, 'error');
    } finally {
        _indexingActive = false;
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '🚀 Enviar Pendentes para IA';
        }
        showIndexProgress(false);
    }
}

// ── Indexa uma questão específica ────────────────────────────────────
async function indexOneQuestion(questionId, btn) {
    btn.disabled = true;
    btn.innerHTML = '<span class="mini-spinner"></span>';

    try {
        const result = await API.post(
            `/knowledge/questions/index/question/${questionId}`, {});

        if (result.status === 'INDEXED') {
            showToast('Questão indexada com sucesso!', 'success');
            // Atualiza a linha da tabela sem recarregar tudo
            await loadIndexStatus();
        } else if (result.status === 'SKIPPED') {
            showToast('Questão não modificada — sem necessidade de re-indexar.',
                'success');
            btn.disabled = false;
            btn.innerHTML = '✅ Enviada';
            btn.disabled = true;
            btn.style.background = 'var(--border)';
            btn.style.color = 'var(--text-muted)';
        } else {
            showToast('Erro: ' + (result.error || 'Falha desconhecida'), 'error');
            btn.disabled = false;
            btn.innerHTML = '📤 Enviar';
        }
    } catch (e) {
        showToast('Erro: ' + e.message, 'error');
        btn.disabled = false;
        btn.innerHTML = '📤 Enviar';
    }
}

// ── Remove indexação (permite re-envio) ───────────────────────────────
async function resetIndex(questionId) {
    if (!confirm('Remover esta questão do índice RAG?\n\n' +
        'Isso permitirá re-indexá-la na próxima operação.')) return;

    try {
        await fetch(`/api/knowledge/questions/index/question/${questionId}`,
            { method: 'DELETE' });
        showToast('Índice removido. A questão pode ser re-enviada.', 'success');
        await loadIndexStatus();
    } catch (e) {
        showToast('Erro: ' + e.message, 'error');
    }
}

// ── Indicadores visuais ───────────────────────────────────────────────
function showIndexProgress(active) {
    const el = document.getElementById('idxProgressBar');
    if (!el) return;
    el.style.display = active ? 'block' : 'none';
}

function renderIndexResult(result) {
    const el = document.getElementById('idxResultBox');
    if (!el) return;

    const hasErrors = result.failed > 0;
    el.className = `result-box ${hasErrors ? '' : 'success'}`;
    el.style.display = 'block';

    el.innerHTML = `
        <div style="font-weight:700;margin-bottom:8px">
            📊 Resultado da Indexação
        </div>
        <div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:10px">
            <span>📦 Total: <strong>${result.totalProcessed}</strong></span>
            <span style="color:var(--success)">
                ✅ Indexadas: <strong>${result.indexed}</strong>
            </span>
            <span style="color:var(--text-muted)">
                ⏭ Ignoradas: <strong>${result.skipped}</strong>
            </span>
            ${result.failed > 0 ? `
            <span style="color:var(--danger)">
                ❌ Falhas: <strong>${result.failed}</strong>
            </span>` : ''}
        </div>
        ${result.details?.length ? `
        <details style="margin-top:6px">
            <summary style="cursor:pointer;font-size:12px;
                            color:var(--text-muted)">
                Ver detalhes (${result.details.length})
            </summary>
            <div style="margin-top:8px;max-height:150px;overflow-y:auto;
                        font-size:12px;font-family:monospace;
                        background:var(--bg);padding:8px;border-radius:6px">
                ${result.details.map(d =>
        `<div>${escapeHtml(d)}</div>`
    ).join('')}
            </div>
        </details>` : ''}`;
}