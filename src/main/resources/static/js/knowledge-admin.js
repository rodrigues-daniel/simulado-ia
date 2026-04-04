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
}

// ── Adicionar conhecimento ───────────────────────────────────────────
async function addKnowledge() {
    const conteudo  = document.getElementById('kConteudo')?.value?.trim();
    const materia   = document.getElementById('kMateria')?.value?.trim();
    const contestId = document.getElementById('kContestId')?.value;
    const topicoId  = document.getElementById('kTopicoId')?.value;
    const fonte     = document.getElementById('kFonte')?.value?.trim();
    const resultEl  = document.getElementById('kAddResult');

    if (!conteudo) {
        showToast('Preencha o conteúdo', 'error'); return;
    }

    resultEl.className   = 'result-box';
    resultEl.textContent = '⏳ Gerando embedding e indexando...';

    try {
        const result = await API.post('/knowledge', {
            conteudo,
            materia:   materia   || null,
            contestId: contestId ? parseInt(contestId) : null,
            topicoId:  topicoId  ? parseInt(topicoId)  : null,
            fonte:     fonte     || null
        });

        resultEl.className   = 'result-box success';
        resultEl.textContent =
            `✅ Conhecimento indexado! ID: ${result.id}`;

        // Limpa campos
        document.getElementById('kConteudo').value = '';
        document.getElementById('kFonte').value    = '';
        await loadKnowledgeStats();

    } catch (e) {
        resultEl.className   = 'result-box error';
        resultEl.textContent = `❌ Erro: ${e.message}`;
    }
}

async function loadKnowledgeTopics() {
    const contestId = document.getElementById('kContestId')?.value;
    const sel       = document.getElementById('kTopicoId');
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
    const query   = document.getElementById('kSearchQuery')?.value?.trim();
    const materia = document.getElementById('kSearchMateria')?.value?.trim();
    const topK    = document.getElementById('kSearchTopK')?.value || 5;

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
        const simPct  = Math.round((r.similarity || 0) * 100);
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
    const materia  = document.getElementById('kTestMateria')?.value?.trim();
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
        CACHE_SEMANTICO: { label: '⚡ Cache Semântico',  color: '#16a34a', bg: '#dcfce7' },
        RAG_OLLAMA:      { label: '🧠 RAG + Ollama',     color: '#1e40af', bg: '#dbeafe' },
        OLLAMA_ONLY:     { label: '🤖 Ollama (sem RAG)', color: '#854d0e', bg: '#fef9c3' },
        FALLBACK:        { label: '⚠️ Fallback',         color: '#991b1b', bg: '#fee2e2' }
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
                        ${h.lastHitAt ? ` • Último hit: ${
                            new Date(h.lastHitAt).toLocaleDateString('pt-BR')
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

    resultEl.className   = 'result-box';
    resultEl.textContent = '⏳ Indexando conhecimentos...';

    try {
        const payload = JSON.parse(textarea.value);
        const result  = await API.post('/knowledge/bulk', payload);
        resultEl.className   = 'result-box success';
        resultEl.textContent =
            `✅ ${result.saved}/${result.total} conhecimentos indexados!`;
        textarea.value = '';
        await loadKnowledgeStats();
    } catch (e) {
        resultEl.className   = 'result-box error';
        resultEl.textContent = `❌ ${e.message.includes('JSON')
            ? 'JSON inválido' : e.message}`;
    }
}

function loadKnowledgeExample() {
    document.getElementById('kBulkPayload').value = JSON.stringify([
        {
            "conteudo": "Art. 37. A administração pública direta e indireta de qualquer dos Poderes da União, dos Estados, do Distrito Federal e dos Municípios obedecerá aos princípios de legalidade, impessoalidade, moralidade, publicidade e eficiência.",
            "materia":  "Direito Constitucional",
            "fonte":    "CF/1988, Art. 37, caput"
        },
        {
            "conteudo": "O princípio da legalidade, para a Administração Pública, significa que o administrador só pode fazer o que a lei expressamente autoriza, diferentemente do particular, que pode fazer tudo que a lei não proíbe.",
            "materia":  "Direito Administrativo",
            "fonte":    "Doutrina - Di Pietro"
        },
        {
            "conteudo": "Art. 41 da CF/88: São estáveis após três anos de efetivo exercício os servidores nomeados para cargo de provimento efetivo em virtude de concurso público.",
            "materia":  "Direito Administrativo",
            "fonte":    "CF/1988, Art. 41"
        }
    ], null, 2);
}