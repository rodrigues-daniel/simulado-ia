// ══════════════════════════════════════════════════════════════════════
// ── GESTÃO DE QUESTÕES IA ─────────────────────────────────────────────
// ══════════════════════════════════════════════════════════════════════

let currentReviewId = null;

async function initIAAdmin() {
    await Promise.all([
        loadIAStats(),
        loadIAQuestions(),
        loadContestsIntoSelect('ragQualityContest'),
        loadTopicsForIAFilter()
    ]);

    // Carrega relatório RAG do concurso default
    const contests = await API.get('/admin/contests');
    const def      = contests.find(c => c.isDefault) || contests[0];
    if (def) {
        document.getElementById('ragQualityContest').value = def.id;
        await loadRagQualityReport();
    }
}

// ── Stats ────────────────────────────────────────────────────────────
async function loadIAStats() {
    try {
        const s = await API.get('/ia-admin/stats');
        document.getElementById('iaTotalQ').textContent    = s.total    || 0;
        document.getElementById('iaPendingQ').textContent  = s.pending  || 0;
        document.getElementById('iaApprovedQ').textContent = s.approved || 0;
        document.getElementById('iaRejectedQ').textContent = s.rejected || 0;
    } catch (e) {
        console.error('Erro ao carregar stats IA:', e);
    }
}

async function loadTopicsForIAFilter() {
    try {
        const contests = await API.get('/admin/contests');
        const def      = contests.find(c => c.isDefault) || contests[0];
        if (!def) return;
        const topics = await API.get(`/admin/topics/${def.id}`);
        const sel    = document.getElementById('iaTopicFilter');
        sel.innerHTML = '<option value="">Todos os tópicos</option>' +
            topics.map(t => `<option value="${t.id}">${t.name}</option>`).join('');
    } catch (e) { console.error(e); }
}

// ── Lista de questões ────────────────────────────────────────────────
async function loadIAQuestions() {
    const filter  = document.getElementById('iaFilter').value;
    const topicId = document.getElementById('iaTopicFilter').value;
    const list    = document.getElementById('iaQuestionsList');

    list.innerHTML = '<div class="loading-spinner">Carregando questões...</div>';

    try {
        let questions;

        if (topicId) {
            questions = await API.get(`/ia-admin/questions/topic/${topicId}`);
        } else {
            questions = await API.get('/ia-admin/questions/pending');
        }

        // Filtra localmente por status
        if (filter === 'approved') {
            questions = questions.filter(q => q.iaApproved === true);
        } else if (filter === 'rejected') {
            questions = questions.filter(q => q.iaApproved === false && q.iaReviewed);
        } else {
            questions = questions.filter(q => !q.iaReviewed);
        }

        renderIAQuestions(questions, filter);

    } catch (e) {
        list.innerHTML = `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

async function loadIAQuestionsByTopic() {
    await loadIAQuestions();
}

function renderIAQuestions(questions, filter) {
    const list = document.getElementById('iaQuestionsList');

    if (!questions.length) {
        list.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">
                    ${filter === 'pending' ? '✅' : '📭'}
                </div>
                <div class="empty-state-text">
                    ${filter === 'pending'
                        ? 'Nenhuma questão pendente de revisão.'
                        : 'Nenhuma questão encontrada para este filtro.'}
                </div>
            </div>`;
        return;
    }

    list.innerHTML = questions.map(q => renderIAQuestionCard(q)).join('');
}

function renderIAQuestionCard(q) {
    const statusBadge = !q.iaReviewed
        ? '<span class="ia-badge pending">⏳ Pendente</span>'
        : q.iaApproved
            ? '<span class="ia-badge approved">✅ Aprovada</span>'
            : '<span class="ia-badge rejected">❌ Rejeitada</span>';

    const diffMap  = { FACIL: 'diff-badge-facil', MEDIO: 'diff-badge-medio', DIFICIL: 'diff-badge-dificil' };
    const diffBadge = q.difficulty
        ? `<span class="${diffMap[q.difficulty] || 'diff-badge-medio'}">${q.difficulty}</span>`
        : '';

    const answerBadge = q.correctAnswer
        ? '<span class="answer-badge-certo">CERTO</span>'
        : '<span class="answer-badge-errado">ERRADO</span>';

    const reviewNote = q.reviewNote
        ? `<div class="ia-review-note">📝 ${escapeHtml(q.reviewNote)}</div>`
        : '';

    return `
    <div class="ia-question-card" id="ia-card-${q.id}">
        <div class="ia-card-header">
            <div class="ia-card-meta">
                <span class="ia-topic-label">📚 ${escapeHtml(q.topicName || '')}</span>
                <span class="ia-discipline-label">${escapeHtml(q.discipline || '')}</span>
            </div>
            <div style="display:flex;gap:6px;align-items:center;flex-wrap:wrap">
                ${statusBadge}
                ${diffBadge}
                ${answerBadge}
            </div>
        </div>

        <div class="ia-card-statement">${escapeHtml(q.statement)}</div>

        ${reviewNote}

        <div class="ia-card-actions">
            <button class="btn btn-secondary btn-sm"
                    onclick="openReviewModal(${q.id})">
                🔍 Revisar
            </button>
            ${!q.iaReviewed ? `
            <button class="btn btn-sm" style="background:#16a34a;color:#fff"
                    onclick="quickApprove(${q.id})">
                ✅ Aprovar
            </button>
            <button class="btn btn-sm" style="background:#dc2626;color:#fff"
                    onclick="quickReject(${q.id})">
                ❌ Rejeitar
            </button>` : ''}
            <button class="btn btn-sm" style="background:#fee2e2;color:#991b1b"
                    onclick="deleteIAQuestion(${q.id})">
                🗑️ Excluir
            </button>
        </div>
    </div>`;
}

// ── Aprovação rápida ─────────────────────────────────────────────────
async function quickApprove(id) {
    try {
        await API.post(`/ia-admin/questions/${id}/approve`, {});
        document.getElementById(`ia-card-${id}`)?.remove();
        await loadIAStats();
        showToast('Questão aprovada!', 'success');
    } catch (e) { showToast('Erro ao aprovar', 'error'); }
}

async function quickReject(id) {
    try {
        await API.post(`/ia-admin/questions/${id}/reject`, {});
        document.getElementById(`ia-card-${id}`)?.remove();
        await loadIAStats();
        showToast('Questão rejeitada.', 'success');
    } catch (e) { showToast('Erro ao rejeitar', 'error'); }
}

async function approveAllPending() {
    const cards = document.querySelectorAll('[id^="ia-card-"]');
    if (!cards.length) { showToast('Nenhuma questão pendente', 'error'); return; }
    if (!confirm(`Aprovar todas as ${cards.length} questões pendentes?`)) return;

    let count = 0;
    for (const card of cards) {
        const id = card.id.replace('ia-card-', '');
        try {
            await API.post(`/ia-admin/questions/${id}/approve`, {});
            card.remove();
            count++;
        } catch (_) {}
    }
    await loadIAStats();
    showToast(`${count} questões aprovadas!`, 'success');
}

async function deleteIAQuestion(id) {
    if (!confirm('Excluir esta questão permanentemente?')) return;
    try {
        await fetch(`/api/ia-admin/questions/${id}`, { method: 'DELETE' });
        document.getElementById(`ia-card-${id}`)?.remove();
        await loadIAStats();
        showToast('Questão excluída.', 'success');
    } catch (e) { showToast('Erro ao excluir', 'error'); }
}

// ── Modal de revisão completa ────────────────────────────────────────
async function openReviewModal(questionId) {
    currentReviewId = questionId;
    const modal     = document.getElementById('iaReviewModal');
    const body      = document.getElementById('iaModalBody');

    body.innerHTML = '<div class="loading-spinner">Carregando...</div>';
    modal.classList.add('open');
    document.body.style.overflow = 'hidden';

    try {
        const q = await API.get(`/questions/${questionId}`);
        renderReviewModal(q);
    } catch (e) {
        body.innerHTML = `<p style="color:var(--danger)">Erro ao carregar questão.</p>`;
    }
}

function renderReviewModal(q) {
    const body = document.getElementById('iaModalBody');
    const meta = document.getElementById('iaModalMeta');
    meta.textContent = `ID #${q.id} • Gerada em ${new Date(q.createdAt).toLocaleDateString('pt-BR')}`;

    const answerClass = q.correctAnswer ? 'answer-badge-certo' : 'answer-badge-errado';
    const answerText  = q.correctAnswer ? '✅ CERTO' : '❌ ERRADO';

    body.innerHTML = `
        <div style="margin-bottom:16px">
            <div class="law-header" style="margin-bottom:6px">📋 Enunciado</div>
            <div class="question-statement" style="font-size:14px">${escapeHtml(q.statement)}</div>
        </div>

        <div style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:16px">
            <span class="${answerClass}">${answerText}</span>
            <span class="${({FACIL:'diff-badge-facil',MEDIO:'diff-badge-medio',DIFICIL:'diff-badge-dificil'})[q.difficulty]||'diff-badge-medio'}">${q.difficulty||'MEDIO'}</span>
        </div>

        ${q.lawParagraph ? `
        <div class="law-paragraph-box" style="margin-bottom:14px">
            <div class="law-header">📖 ${escapeHtml(q.lawReference||'Base Legal')}</div>
            <blockquote class="law-quote">${escapeHtml(q.lawParagraph)}</blockquote>
        </div>` : ''}

        ${q.explanation ? `
        <div style="background:var(--bg);padding:12px;border-radius:8px;
                    margin-bottom:14px;font-size:13px;line-height:1.6">
            <strong>💬 Explicação:</strong> ${escapeHtml(q.explanation)}
        </div>` : ''}

        ${q.professorTip ? `
        <div class="professor-tip" style="margin-bottom:14px">
            💡 ${escapeHtml(q.professorTip)}
        </div>` : ''}

        ${(q.trapKeywords||[]).length ? `
        <div style="margin-bottom:16px">
            <strong style="font-size:13px">⚠️ Palavras-armadilha:</strong>
            <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:6px">
                ${q.trapKeywords.map(k => `<span class="trap-mini">${escapeHtml(k)}</span>`).join('')}
            </div>
        </div>` : ''}

        <div style="border-top:1px solid var(--border);padding-top:16px">
            <label style="font-size:13px;font-weight:600;display:block;margin-bottom:6px">
                📝 Nota de Revisão (opcional)
            </label>
            <textarea id="reviewNoteInput" rows="3"
                      style="width:100%;padding:10px;border:1px solid var(--border);
                             border-radius:8px;font-size:13px;resize:vertical"
                      placeholder="Ex: Gabarito correto, mas enunciado confuso. Aprovada com ressalva."></textarea>
        </div>

        <div style="display:flex;gap:10px;margin-top:16px;justify-content:flex-end">
            <button class="btn btn-sm" style="background:#dc2626;color:#fff"
                    onclick="submitReview(false)">
                ❌ Rejeitar
            </button>
            <button class="btn btn-sm" style="background:#16a34a;color:#fff"
                    onclick="submitReview(true)">
                ✅ Aprovar
            </button>
        </div>
    `;
}

async function submitReview(approved) {
    if (!currentReviewId) return;
    const note = document.getElementById('reviewNoteInput')?.value || '';
    const endpoint = approved ? 'approve' : 'reject';

    try {
        await API.post(`/ia-admin/questions/${currentReviewId}/${endpoint}`,
                       { note: note || null });
        closeIAModal();
        await loadIAQuestions();
        await loadIAStats();
        showToast(approved ? 'Questão aprovada!' : 'Questão rejeitada.', 'success');
    } catch (e) {
        showToast('Erro ao salvar revisão', 'error');
    }
}

function closeIAModal() {
    document.getElementById('iaReviewModal').classList.remove('open');
    document.body.style.overflow = '';
    currentReviewId = null;
}

function closeIAModalOutside(e) {
    if (e.target.id === 'iaReviewModal') closeIAModal();
}

// ══════════════════════════════════════════════════════════════════════
// ── ANÁLISE RAG ───────────────────────────────────────────────────────
// ══════════════════════════════════════════════════════════════════════

async function loadRagQualityReport() {
    const contestId = document.getElementById('ragQualityContest').value;
    if (!contestId) return;

    const summary  = document.getElementById('ragGlobalSummary');
    const reports  = document.getElementById('ragTopicReports');
    reports.innerHTML  = '<div class="loading-spinner">Analisando banco vetorial...</div>';
    summary.style.display = 'none';

    try {
        const report = await API.get(`/ia-admin/rag/report/${contestId}`);
        renderRagGlobalSummary(report);
        renderRagTopicReports(report.topicReports);
    } catch (e) {
        reports.innerHTML = `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

function renderRagGlobalSummary(report) {
    const summary = document.getElementById('ragGlobalSummary');
    const cards   = document.getElementById('ragQualityCards');
    const orients = document.getElementById('ragGlobalOrientations');

    const total = report.topicReports?.length || 0;
    const score = Math.round(report.avgScore || 0);

    cards.innerHTML = `
        <div class="rag-qual-card excellent">
            <span class="rag-qual-value">${report.excellent||0}</span>
            <span class="rag-qual-label">✅ Excelente</span>
        </div>
        <div class="rag-qual-card adequate">
            <span class="rag-qual-value">${report.adequate||0}</span>
            <span class="rag-qual-label">🟢 Adequado</span>
        </div>
        <div class="rag-qual-card basic">
            <span class="rag-qual-value">${report.basic||0}</span>
            <span class="rag-qual-label">🟡 Básico</span>
        </div>
        <div class="rag-qual-card insufficient">
            <span class="rag-qual-value">${report.insufficient||0}</span>
            <span class="rag-qual-label">⚠️ Insuficiente</span>
        </div>
        <div class="rag-qual-card no-material">
            <span class="rag-qual-value">${report.noMaterial||0}</span>
            <span class="rag-qual-label">⛔ Sem Material</span>
        </div>
        <div class="rag-qual-card score">
            <span class="rag-qual-value">${score}%</span>
            <span class="rag-qual-label">Score Médio</span>
        </div>`;

    orients.innerHTML = (report.globalOrientations || [])
        .map(o => `<div class="rag-orientation-item">${o}</div>`)
        .join('');

    summary.style.display = 'block';
}

function renderRagTopicReports(reports) {
    const container = document.getElementById('ragTopicReports');
    if (!reports?.length) {
        container.innerHTML = '<p style="color:var(--text-muted)">Nenhum relatório disponível.</p>';
        return;
    }

    // Ordena: piores primeiro (quem mais precisa de atenção)
    const sorted = [...reports].sort((a, b) => a.score - b.score);

    container.innerHTML = `
        <h3 style="margin:20px 0 12px;font-size:15px;color:var(--text)">
            📋 Detalhamento por Tópico
        </h3>
        ${sorted.map(r => renderRagTopicCard(r)).join('')}`;
}

function renderRagTopicCard(r) {
    const levelColors = {
        EXCELENTE:    { bg: '#dcfce7', border: '#86efac', text: '#166534' },
        ADEQUADO:     { bg: '#dbeafe', border: '#93c5fd', text: '#1e40af' },
        BASICO:       { bg: '#fef9c3', border: '#fde047', text: '#854d0e' },
        INSUFICIENTE: { bg: '#fee2e2', border: '#fca5a5', text: '#991b1b' },
        SEM_MATERIAL: { bg: '#f1f5f9', border: '#cbd5e1', text: '#475569' }
    };
    const c = levelColors[r.qualityLevel] || levelColors.BASICO;

    const missingHtml = (r.missingTypes||[]).length
        ? `<div style="margin-top:8px">
               <strong style="font-size:12px">📌 O que adicionar:</strong>
               <ul style="margin:4px 0 0 16px;font-size:12px;color:var(--text-muted)">
                   ${r.missingTypes.map(m => `<li>${m}</li>`).join('')}
               </ul>
           </div>`
        : '';

    return `
    <div style="border:1px solid ${c.border};background:${c.bg};
                border-radius:10px;padding:16px;margin-bottom:10px">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;
                    margin-bottom:10px;flex-wrap:wrap;gap:8px">
            <div>
                <div style="font-weight:700;font-size:14px;color:var(--text)">
                    ${escapeHtml(r.topicName)}
                </div>
                <div style="font-size:12px;color:var(--text-muted);margin-top:2px">
                    ${r.chunks} chunks ingeridos de ${r.recommended} recomendados
                </div>
            </div>
            <div style="display:flex;align-items:center;gap:10px">
                <!-- Barra de score -->
                <div style="width:100px;height:8px;background:#e2e8f0;border-radius:4px;overflow:hidden">
                    <div style="width:${r.score}%;height:100%;
                                background:${r.score>=75?'#16a34a':r.score>=50?'#d97706':'#dc2626'};
                                border-radius:4px;transition:width .5s"></div>
                </div>
                <span style="font-weight:800;font-size:15px;color:${c.text}">${r.score}%</span>
            </div>
        </div>
        <div style="display:flex;flex-direction:column;gap:4px">
            ${(r.orientations||[]).map(o =>
                `<div style="font-size:13px;color:${c.text}">${o}</div>`
            ).join('')}
        </div>
        ${missingHtml}
    </div>`;
}

// ── Geração de questões por tópico no painel IA ─────────────────────────
async function generateForTopic(topicId, topicName) {
    const count = prompt(
        `Quantas questões deseja gerar para:\n"${topicName}"?\n(máx: 20)`,
        '10'
    );
    if (!count || isNaN(count)) return;
    const qty = Math.min(parseInt(count), 20);

    showToast(`Gerando ${qty} questões via IA para "${topicName}"...`, 'success');

    try {
        const result = await API.post(
            `/ia-admin/topics/${topicId}/generate`,
            { count: qty }
        );
        showToast(result.message || `${result.generated} questões geradas!`, 'success');
        await loadIAStats();
        await loadIAQuestions();
    } catch (e) {
        showToast('Erro ao gerar questões. Verifique se o Ollama está ativo.', 'error');
        console.error(e);
    }
}

