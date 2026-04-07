// ── Admin Panel ─────────────────────────────────────────────────────────
const EXAMPLES = {
    contests: JSON.stringify([{
        name: "TCU - Técnico Federal de Controle Externo",
        organ: "Tribunal de Contas da União",
        role: "Técnico Administrativo",
        year: 2025,
        level: "MEDIO",
        isDefault: true
    }], null, 2),

    topics: JSON.stringify([{
        contestId: 1,
        name: "Princípios da Administração Pública (LIMPE)",
        discipline: "Direito Administrativo",
        lawReference: "CF/1988, Art. 37",
        incidenceRate: 0.92
    }, {
        contestId: 1,
        name: "Atos Administrativos",
        discipline: "Direito Administrativo",
        lawReference: "Lei 9.784/1999",
        incidenceRate: 0.78
    }], null, 2),

    questions: JSON.stringify([{
        topicId: 1,
        contestId: 1,
        statement: "De acordo com o art. 37 da CF/88, a administração pública obedecerá exclusivamente ao princípio da legalidade, sendo os demais princípios derivados deste.",
        correctAnswer: false,
        lawParagraph: "Art. 37. A administração pública direta e indireta de qualquer dos Poderes da União, dos Estados, do Distrito Federal e dos Municípios obedecerá aos princípios de legalidade, impessoalidade, moralidade, publicidade e eficiência.",
        lawReference: "CF/1988, Art. 37, caput",
        explanation: "ERRADO. O artigo 37 elenca cinco princípios expressos (LIMPE), não apenas o da legalidade.",
        professorTip: "A palavra 'exclusivamente' é a armadilha clássica. O LIMPE tem 5 princípios autônomos.",
        trapKeywords: ["exclusivamente"],
        year: 2023,
        source: "TCU 2023 - Adaptada",
        difficulty: "FACIL"
    }], null, 2)
};

// ── CORREÇÃO: sem chamada automática que quebrava a página ──────────────
document.addEventListener('DOMContentLoaded', () => {
    // Não chama loadRagDocuments() aqui pois a aba RAG está oculta
    // e uma falha de rede quebrava todo o DOMContentLoaded
    switchTabById('contests');
});

// ── Tabs ────────────────────────────────────────────────────────────────
function switchTab(tabName) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');

    const clickedTab = event?.target;
    if (clickedTab) clickedTab.classList.add('active');

    const section = document.getElementById(`tab-${tabName}`);
    if (section) section.style.display = 'block';

    // Inicializa cada aba na primeira abertura
  if (tabName === 'rag')          initRagTab();        // ← substitui loadRagDocuments()
  if (tabName === 'topics-view')  initTopicsView();
  if (tabName === 'ia-questions') initIAAdmin();
  if (tabName === 'rag-knowledge') initKnowledgeAdmin();
  if (tabName === 'exam-gen') initExamGenerator();

}

function switchTabById(tabName) {
    document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');
    const section = document.getElementById(`tab-${tabName}`);
    if (section) section.style.display = 'block';

    // Marca primeira tab como ativa visualmente
    const firstTab = document.querySelector('.tab');
    if (firstTab) {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        firstTab.classList.add('active');
    }
}

// ── Exemplos ─────────────────────────────────────────────────────────────
function loadExample(type) {
    const el = document.getElementById(`${type}Payload`);
    if (el) el.value = EXAMPLES[type] || '';
}

// ── Bulk Loads ───────────────────────────────────────────────────────────
async function loadContests() {
    await bulkLoad('contests', '/admin/contests/bulk', 'contestsResult');
}

async function loadTopics() {
    await bulkLoad('topics', '/admin/topics/bulk', 'topicsResult');
}

async function loadQuestions() {
    await bulkLoad('questions', '/admin/questions/bulk', 'questionsResult');
}

async function bulkLoad(payloadId, endpoint, resultId) {
    const textarea = document.getElementById(`${payloadId}Payload`);
    const resultEl = document.getElementById(resultId);

    if (!textarea?.value?.trim()) {
        resultEl.className = 'result-box error';
        resultEl.textContent = '❌ Cole o JSON antes de importar.';
        return;
    }

    try {
        const payload = JSON.parse(textarea.value);
        resultEl.className = 'result-box';
        resultEl.textContent = '⏳ Importando...';

        const result = await API.post(endpoint, payload);

        resultEl.className = 'result-box success';
        resultEl.textContent = `✅ ${result.length} registro(s) importado(s) com sucesso!`;
        showToast(`${result.length} registros importados!`, 'success');
    } catch (e) {
        resultEl.className = 'result-box error';
        if (e.message.includes('JSON')) {
            resultEl.textContent = `❌ JSON inválido. Verifique a sintaxe.`;
        } else {
            resultEl.textContent = `❌ Erro: ${e.message}`;
        }
        showToast('Erro na importação', 'error');
    }
}

// ── RAG ──────────────────────────────────────────────────────────────────
async function uploadPdf() {
    const fileInput = document.getElementById('pdfFile');
    const topicId = document.getElementById('ragTopicId').value;
    const contestId = document.getElementById('ragContestId').value;
    const resultEl = document.getElementById('ragResult');

    if (!fileInput?.files[0]) {
        showToast('Selecione um arquivo PDF', 'error');
        return;
    }

    resultEl.className = 'result-box';
    resultEl.textContent = '⏳ Enviando e processando PDF... pode levar alguns segundos.';

    try {
        const params = {};
        if (topicId) params.topicId = topicId;
        if (contestId) params.contestId = contestId;

        const result = await API.uploadFile('/rag/upload', fileInput.files[0], params);

        resultEl.className = 'result-box success';
        resultEl.textContent = `✅ "${result.name}" ingerido com sucesso! ${result.totalChunks} chunks criados.`;
        loadRagDocuments();
    } catch (e) {
        resultEl.className = 'result-box error';
        resultEl.textContent = `❌ Erro: ${e.message}`;
    }
}

async function ingestText() {
    const textarea = document.getElementById('ragTextPayload');
    const resultEl = document.getElementById('ragResult');

    if (!textarea?.value?.trim()) {
        showToast('Cole o JSON antes de ingerir', 'error');
        return;
    }

    try {
        const payload = JSON.parse(textarea.value);
        resultEl.className = 'result-box';
        resultEl.textContent = '⏳ Ingerindo texto...';

        const result = await API.post('/rag/ingest-text', payload);

        resultEl.className = 'result-box success';
        resultEl.textContent = `✅ "${result.name}" ingerido! ${result.totalChunks} chunks criados.`;
        loadRagDocuments();
    } catch (e) {
        resultEl.className = 'result-box error';
        resultEl.textContent = `❌ Erro: ${e.message}`;
    }
}

async function loadRagDocuments() {
    const container = document.getElementById('ragDocumentsList');
    if (!container) return; // aba pode estar oculta

    try {
        const docs = await API.get('/rag/documents');

        if (!docs?.length) {
            container.innerHTML = '<p style="color:var(--text-muted)">Nenhum documento ingerido ainda.</p>';
            return;
        }

        container.innerHTML = `
        <table style="width:100%;border-collapse:collapse;font-size:13px;margin-top:8px">
            <thead>
                <tr style="background:var(--bg)">
                    <th style="padding:10px 8px;text-align:left;border-bottom:2px solid var(--border)">Nome</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Tipo</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Chunks</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Status</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Data</th>
                </tr>
            </thead>
            <tbody>
                ${docs.map(d => `
                <tr style="border-bottom:1px solid var(--border)">
                    <td style="padding:10px 8px;font-weight:500">${d.name}</td>
                    <td style="padding:10px 8px;text-align:center">
                        <span class="badge badge-info">${d.sourceType}</span>
                    </td>
                    <td style="padding:10px 8px;text-align:center">${d.totalChunks}</td>
                    <td style="padding:10px 8px;text-align:center">
                        <span class="badge ${d.status === 'COMPLETED' ? 'badge-success' : 'badge-warning'}">
                            ${d.status}
                        </span>
                    </td>
                    <td style="padding:10px 8px;text-align:center;color:var(--text-muted)">
                        ${d.createdAt ? new Date(d.createdAt).toLocaleDateString('pt-BR') : '--'}
                    </td>
                </tr>`).join('')}
            </tbody>
        </table>`;
    } catch (e) {
        container.innerHTML = '<p style="color:var(--text-muted)">Erro ao carregar documentos.</p>';
        console.error('Erro RAG docs:', e);
    }
}


// ══════════════════════════════════════════════════════════════════════
// ── VISÃO DE TÓPICOS POR DISCIPLINA ───────────────────────────────────
// ══════════════════════════════════════════════════════════════════════

let topicsViewData    = [];   // todos os tópicos carregados
let questionsCache    = {};   // cache: topicId → lista de questões

// Inicializa quando a aba é aberta
async function initTopicsView() {
    await loadContestsIntoSelect('topicsViewContest');
    const contests = await API.get('/admin/contests');
    const def      = contests.find(c => c.isDefault) || contests[0];
    if (def) {
        document.getElementById('topicsViewContest').value = def.id;
        await loadTopicsView();
    }
}

async function loadTopicsView() {
    const contestId = document.getElementById('topicsViewContest').value;
    if (!contestId) return;

    const content = document.getElementById('topicsViewContent');
    content.innerHTML = '<div class="loading-spinner">Carregando tópicos...</div>';
    document.getElementById('topicsViewSummary').style.display = 'none';

    try {
        // Carrega tópicos
        const topics = await API.get(`/admin/topics/${contestId}`);
        topicsViewData = topics;

        // Carrega quantidade de questões para cada tópico em paralelo
        await loadQuestionCountsForTopics(topics);

        // Popula filtro de disciplinas
        populateDisciplineFilter(topics);

        // Renderiza
        renderTopicsView(topics);
        renderSummary(topics, contestId);

    } catch (e) {
        content.innerHTML = `<p style="color:var(--danger)">Erro ao carregar tópicos: ${e.message}</p>`;
        console.error(e);
    }
}

async function loadQuestionCountsForTopics(topics) {
    // Busca em paralelo, máximo 5 simultâneos para não sobrecarregar
    const batchSize = 5;
    for (let i = 0; i < topics.length; i += batchSize) {
        const batch = topics.slice(i, i + batchSize);
        await Promise.allSettled(
            batch.map(t => loadQuestionCountForTopic(t.id))
        );
    }
}

async function loadQuestionCountForTopic(topicId) {
    if (questionsCache[topicId] !== undefined) return;
    try {
        const questions = await API.get(`/questions/topic/${topicId}`);
        questionsCache[topicId] = questions;
    } catch (_) {
        questionsCache[topicId] = [];
    }
}

function populateDisciplineFilter(topics) {
    const disciplines = [...new Set(topics.map(t => t.discipline).filter(Boolean))].sort();
    const sel = document.getElementById('disciplineFilter');
    sel.innerHTML = '<option value="">Todas as disciplinas</option>' +
        disciplines.map(d => `<option value="${d}">${d}</option>`).join('');
}

function filterByDiscipline() {
    const discipline = document.getElementById('disciplineFilter').value;
    const filtered   = discipline
        ? topicsViewData.filter(t => t.discipline === discipline)
        : topicsViewData;
    renderTopicsView(filtered);
}

function renderTopicsView(topics) {
    const sortOrder = document.getElementById('sortOrder')?.value || 'incidence';
    const content   = document.getElementById('topicsViewContent');

    if (!topics.length) {
        content.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📭</div>
                <div class="empty-state-text">Nenhum tópico encontrado para este filtro.</div>
            </div>`;
        return;
    }

    // Agrupa por disciplina
    const grouped = {};
    topics.forEach(t => {
        const disc = t.discipline || 'Sem Disciplina';
        if (!grouped[disc]) grouped[disc] = [];
        grouped[disc].push(t);
    });

    // Ordena tópicos dentro de cada grupo
    Object.keys(grouped).forEach(disc => {
        grouped[disc].sort((a, b) => {
            if (sortOrder === 'incidence') {
                return parseFloat(b.incidenceRate || 0) - parseFloat(a.incidenceRate || 0);
            }
            if (sortOrder === 'name') {
                return a.name.localeCompare(b.name);
            }
            if (sortOrder === 'questions') {
                const qa = (questionsCache[a.id] || []).length;
                const qb = (questionsCache[b.id] || []).length;
                return qb - qa;
            }
            return 0;
        });
    });

    // Ordena disciplinas por número de tópicos (maior primeiro)
    const sortedDiscs = Object.keys(grouped).sort(
        (a, b) => grouped[b].length - grouped[a].length
    );

    content.innerHTML = sortedDiscs.map(disc =>
        renderDisciplineGroup(disc, grouped[disc])
    ).join('');
}

function renderDisciplineGroup(discipline, topics) {
    const totalQuestions = topics.reduce(
        (sum, t) => sum + (questionsCache[t.id] || []).length, 0
    );
    const avgIncidence = topics.length
        ? (topics.reduce((s, t) => s + parseFloat(t.incidenceRate || 0), 0)
           / topics.length * 100).toFixed(0)
        : 0;

    const groupId = `disc-${discipline.replace(/\s+/g, '-').replace(/[^a-zA-Z0-9-]/g, '')}`;

    return `
    <div class="discipline-group">
        <div class="discipline-header" onclick="toggleDiscipline('${groupId}')">
            <div class="discipline-header-left">
                <span style="font-size:18px">📚</span>
                <span class="discipline-name">${discipline}</span>
            </div>
            <div style="display:flex;align-items:center;gap:16px">
                <div class="discipline-stats">
                    <span>📋 ${topics.length} tópicos</span>
                    <span>❓ ${totalQuestions} questões</span>
                    <span>📈 ${avgIncidence}% incidência média</span>
                </div>
                <span class="discipline-toggle" id="toggle-${groupId}">▼</span>
            </div>
        </div>

        <div class="discipline-body" id="${groupId}">
            <!-- Cabeçalho das colunas -->
            <div class="topic-row topic-row-header">
                <span>Tópico</span>
                <span style="text-align:center">Questões</span>
                <span style="text-align:center">Incidência</span>
                <span style="text-align:center">Base Legal</span>
                <span style="text-align:center">Status</span>
            </div>

            ${topics.map(t => renderTopicRow(t)).join('')}
        </div>
    </div>`;
}

function renderTopicRow(topic) {
    const questions    = questionsCache[topic.id] || [];
    const qCount       = questions.length;
    const rate         = parseFloat(topic.incidenceRate || 0);
    const ratePct      = (rate * 100).toFixed(0);
    const incClass     = rate >= 0.80 ? 'high' : rate >= 0.70 ? 'med' : 'low';
    const statusBadge  = topic.isHidden
        ? '<span class="status-hidden">👁️ Oculto</span>'
        : '<span class="status-priority">✅ Ativo</span>';

    const lawRef = topic.lawReference
        ? `<span title="${topic.lawReference}" style="max-width:110px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;display:block">
               ${topic.lawReference}
           </span>`
        : '<span style="color:var(--text-muted)">—</span>';

    return `
    <div class="topic-row">
        <div class="topic-row-name">
            <strong>${topic.name}</strong>
        </div>

        <div style="text-align:center">
            <div class="questions-count">
                ${qCount}
                <span>questões</span>
            </div>
           ${qCount > 0 ? `
           <div style="display:flex;gap:4px;flex-direction:column;align-items:center">
               <button class="btn btn-secondary btn-sm"
                       style="font-size:11px;padding:3px 8px"
                       onclick="openQuestionsModal(${topic.id}, '${escapeHtml(topic.name)}',
                                '${escapeHtml(topic.discipline||'')}', ${ratePct})">
                   🔍 Ver
               </button>
               <button class="btn btn-sm"
                       style="font-size:11px;padding:3px 8px;background:#7c3aed;color:#fff"
                       onclick="generateForTopic(${topic.id}, '${escapeHtml(topic.name)}')">
                   🤖 Gerar IA
               </button>
           </div>` : `
           <button class="btn btn-sm"
                   style="font-size:11px;padding:3px 8px;background:#7c3aed;color:#fff"
                   onclick="generateForTopic(${topic.id}, '${escapeHtml(topic.name)}')">
               🤖 Gerar IA
           </button>`}
        </div>

        <div class="incidence-cell" style="text-align:center">
            <span class="incidence-value incidence-${incClass}">${ratePct}%</span>
            <div class="incidence-mini-bar">
                <div class="incidence-mini-fill fill-${incClass}"
                     style="width:${ratePct}%"></div>
            </div>
        </div>

        <div style="font-size:12px;color:var(--text-muted)">
            ${lawRef}
        </div>

        <div style="text-align:center">
            ${statusBadge}
        </div>
    </div>`;
}

function renderSummary(topics, contestId) {
    const totalQ   = Object.values(questionsCache)
                           .reduce((s, arr) => s + arr.length, 0);
    const discs    = new Set(topics.map(t => t.discipline).filter(Boolean)).size;
    const avgInc   = topics.length
        ? (topics.reduce((s, t) => s + parseFloat(t.incidenceRate || 0), 0)
           / topics.length * 100).toFixed(1) + '%'
        : '--';

    document.getElementById('tvTotalTopics').textContent     = topics.length;
    document.getElementById('tvTotalQuestions').textContent  = totalQ;
    document.getElementById('tvTotalDisciplines').textContent = discs;
    document.getElementById('tvAvgIncidence').textContent    = avgInc;
    document.getElementById('topicsViewSummary').style.display = 'grid';
}

function toggleDiscipline(groupId) {
    const body   = document.getElementById(groupId);
    const toggle = document.getElementById(`toggle-${groupId}`);
    if (!body) return;
    const isOpen = body.style.display !== 'none';
    body.style.display = isOpen ? 'none' : 'flex';
    body.style.flexDirection = 'column';
    if (toggle) toggle.classList.toggle('collapsed', isOpen);
}

// ══════════════════════════════════════════════════════════════════════
// ── MODAL DE QUESTÕES POR TÓPICO ──────────────────────────────────────
// ══════════════════════════════════════════════════════════════════════

async function openQuestionsModal(topicId, topicName, discipline, incidencePct) {
    const modal    = document.getElementById('questionsModal');
    const body     = document.getElementById('modalBody');
    const title    = document.getElementById('modalTopicName');
    const meta     = document.getElementById('modalTopicMeta');

    title.textContent = topicName;
    meta.textContent  = `${discipline} • Incidência: ${incidencePct}%`;
    body.innerHTML    = '<div class="loading-spinner">Carregando questões...</div>';
    modal.classList.add('open');
    document.body.style.overflow = 'hidden';

    try {
        // Usa cache se disponível
        const questions = questionsCache[topicId]
            || await API.get(`/questions/topic/${topicId}`);
        questionsCache[topicId] = questions;
        renderQuestionsModal(questions, incidencePct);
    } catch (e) {
        body.innerHTML = `<p style="color:var(--danger)">Erro ao carregar questões.</p>`;
    }
}

function renderQuestionsModal(questions, topicIncidencePct) {
    const body = document.getElementById('modalBody');

    if (!questions.length) {
        body.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📭</div>
                <div class="empty-state-text">
                    Nenhuma questão cadastrada para este tópico.<br>
                    <a href="#" onclick="switchTab('questions')"
                       style="color:var(--primary-light)">
                       Importe questões na aba Questões
                    </a>
                </div>
            </div>`;
        return;
    }

    // Estatísticas rápidas do modal
    const certas   = questions.filter(q => q.correctAnswer === true).length;
    const erradas  = questions.filter(q => q.correctAnswer === false).length;
    const faceis   = questions.filter(q => q.difficulty === 'FACIL').length;
    const medios   = questions.filter(q => q.difficulty === 'MEDIO').length;
    const dificeis = questions.filter(q => q.difficulty === 'DIFICIL').length;
    const comTrap  = questions.filter(q => q.trapKeywords?.length > 0).length;

    body.innerHTML = `
        <!-- Resumo do tópico -->
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));
                    gap:10px;margin-bottom:20px">
            <div class="tv-summary-card">
                <span class="tv-summary-value">${questions.length}</span>
                <span class="tv-summary-label">Total</span>
            </div>
            <div class="tv-summary-card">
                <span class="tv-summary-value" style="color:var(--success)">${certas}</span>
                <span class="tv-summary-label">Gabarito CERTO</span>
            </div>
            <div class="tv-summary-card">
                <span class="tv-summary-value" style="color:var(--danger)">${erradas}</span>
                <span class="tv-summary-label">Gabarito ERRADO</span>
            </div>
            <div class="tv-summary-card">
                <span class="tv-summary-value">${topicIncidencePct}%</span>
                <span class="tv-summary-label">Incidência</span>
            </div>
            <div class="tv-summary-card">
                <span class="tv-summary-value" style="color:var(--warning)">${comTrap}</span>
                <span class="tv-summary-label">Com Armadilha</span>
            </div>
        </div>

        <!-- Distribuição dificuldade -->
        <div style="display:flex;gap:8px;margin-bottom:20px;flex-wrap:wrap">
            <span style="font-size:12px;font-weight:600;color:var(--text-muted);
                         align-self:center">Dificuldade:</span>
            <span class="diff-badge-facil">✅ Fácil: ${faceis}</span>
            <span class="diff-badge-medio">⚠️ Médio: ${medios}</span>
            <span class="diff-badge-dificil">🔴 Difícil: ${dificeis}</span>
        </div>

        <!-- Lista de questões -->
        ${questions.map((q, i) => renderQuestionDetailCard(q, i)).join('')}
    `;
}

function renderQuestionDetailCard(q, index) {
    const answerBadge = q.correctAnswer
        ? '<span class="answer-badge-certo">✅ CERTO</span>'
        : '<span class="answer-badge-errado">❌ ERRADO</span>';

    const diffMap = {
        FACIL:   'diff-badge-facil',
        MEDIO:   'diff-badge-medio',
        DIFICIL: 'diff-badge-dificil'
    };
    const diffBadge = q.difficulty
        ? `<span class="${diffMap[q.difficulty] || 'diff-badge-medio'}">${q.difficulty}</span>`
        : '';

    const traps = (q.trapKeywords || []).length
        ? `<span>⚠️ Armadilhas: ${q.trapKeywords.map(k =>
              `<span class="trap-mini">${k}</span>`).join(' ')}</span>`
        : '';

    const source = q.source
        ? `<span>📌 ${q.source}${q.year ? ` (${q.year})` : ''}</span>`
        : '';

    return `
    <div class="q-detail-card">
        <div class="q-detail-header">
            <div class="q-detail-statement">
                <span style="font-size:11px;font-weight:700;color:var(--text-muted);
                             margin-right:6px">#${index + 1}</span>
                ${escapeHtml(q.statement)}
            </div>
            <div class="q-detail-badges">
                ${answerBadge}
                ${diffBadge}
            </div>
        </div>
        ${q.explanation ? `
        <div style="font-size:13px;color:var(--text-muted);line-height:1.5;
                    padding:10px;background:var(--bg);border-radius:6px;margin-bottom:8px">
            💬 ${escapeHtml(q.explanation)}
        </div>` : ''}
        <div class="q-detail-meta">
            ${source}
            ${traps}
            ${q.lawReference
                ? `<span>📖 ${escapeHtml(q.lawReference)}</span>`
                : ''}
        </div>
    </div>`;
}

function closeModal() {
    document.getElementById('questionsModal').classList.remove('open');
    document.body.style.overflow = '';
}

function closeModalOutside(event) {
    if (event.target.id === 'questionsModal') closeModal();
}


// Fecha modal com ESC
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeModal();
});