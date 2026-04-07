// ══════════════════════════════════════════════════════════════════════
// ── GERADOR DE PROVAS ─────────────────────────────════════════════════
// ══════════════════════════════════════════════════════════════════════

let _disciplines = [];   // config atual do template em edição
let _genPolling  = null; // interval de polling do status

// ── Inicializa ───────────────────────────────────────────────────────
async function initExamGenerator() {
    await loadContestsIntoSelect('egContestFilter');
    await loadTemplates();
    await loadGeneratedExams();
}

// ── Templates ────────────────────────────────────────────────────────
async function loadTemplates() {
    const contestId = document.getElementById('egContestFilter')?.value;
    const list      = document.getElementById('egTemplateList');
    if (!list) return;

    list.innerHTML = '<div class="loading-spinner">Carregando...</div>';

    try {
        const url = contestId
            ? `/exam-generator/templates?contestId=${contestId}`
            : '/exam-generator/templates';
        const templates = await API.get(url);

        if (!templates.length) {
            list.innerHTML = `
                <div class="empty-state">
                    <div class="empty-state-icon">📋</div>
                    <div class="empty-state-text">
                        Nenhum template criado ainda.
                        Crie um template para gerar provas.
                    </div>
                </div>`;
            return;
        }

        list.innerHTML = templates.map(t => renderTemplateCard(t)).join('');
    } catch (e) {
        list.innerHTML =
            `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

function renderTemplateCard(t) {
    const discs = (t.disciplineConfig || [])
            .map(d => d.discipline).join(', ') || 'Geral';
    const dist  = t.difficultyDist || {};

    return `
    <div class="eg-template-card" id="tpl-${t.id}">
        <div class="eg-template-header">
            <div>
                <div class="eg-template-name">${escapeHtml(t.name)}</div>
                <div class="eg-template-meta">
                    📚 ${discs} •
                    📋 ${t.totalQuestions} questões •
                    ⏱ ${t.timeLimitMin} min
                </div>
            </div>
            <div style="display:flex;gap:6px;flex-shrink:0">
                <button class="btn btn-primary btn-sm"
                        onclick="generateExam(${t.id})">
                    🚀 Gerar Prova
                </button>
                <button class="btn btn-secondary btn-sm"
                        onclick="editTemplate(${t.id})">
                    ✏️
                </button>
                <button class="btn btn-sm"
                        style="background:#fee2e2;color:#991b1b"
                        onclick="deleteTemplate(${t.id})">
                    🗑️
                </button>
            </div>
        </div>

        ${t.description ? `
        <div style="font-size:13px;color:var(--text-muted);margin:8px 0">
            ${escapeHtml(t.description)}
        </div>` : ''}

        <div class="eg-diff-bar">
            <div class="eg-diff-seg facil"
                 style="width:${dist.facil || 30}%"
                 title="Fácil: ${dist.facil || 30}%">
                F ${dist.facil || 30}%
            </div>
            <div class="eg-diff-seg medio"
                 style="width:${dist.medio || 50}%"
                 title="Médio: ${dist.medio || 50}%">
                M ${dist.medio || 50}%
            </div>
            <div class="eg-diff-seg dificil"
                 style="width:${dist.dificil || 20}%"
                 title="Difícil: ${dist.dificil || 20}%">
                D ${dist.dificil || 20}%
            </div>
        </div>

        ${(t.disciplineConfig || []).length ? `
        <div style="margin-top:8px;display:flex;flex-wrap:wrap;gap:6px">
            ${t.disciplineConfig.map(d => `
                <span style="font-size:11px;padding:3px 8px;
                             background:var(--bg);border:1px solid var(--border);
                             border-radius:20px">
                    📚 ${escapeHtml(d.discipline)}
                    (${d.questionCount || '?'}q)
                </span>`).join('')}
        </div>` : ''}
    </div>`;
}

// ── Criar / Editar Template ──────────────────────────────────────────
function openNewTemplate() {
    _disciplines = [];
    const modal  = document.getElementById('egTemplateModal');
    const body   = document.getElementById('egTemplateBody');
    if (!modal || !body) return;

    document.getElementById('egModalTitle').textContent = '➕ Novo Template';
    body.dataset.editId = '';

    renderTemplateForm();
    modal.classList.add('open');
    document.body.style.overflow = 'hidden';
}

async function editTemplate(templateId) {
    try {
        const t     = await API.get(`/exam-generator/templates/${templateId}`);
        _disciplines = t.disciplineConfig || [];
        const modal  = document.getElementById('egTemplateModal');
        document.getElementById('egModalTitle').textContent = '✏️ Editar Template';
        document.getElementById('egTemplateBody').dataset.editId =
                String(templateId);

        renderTemplateForm(t);
        modal.classList.add('open');
        document.body.style.overflow = 'hidden';
    } catch (e) {
        showToast('Erro ao carregar template: ' + e.message, 'error');
    }
}

function renderTemplateForm(t) {
    const body = document.getElementById('egTemplateBody');
    if (!body) return;

    body.innerHTML = `
        <!-- Dados gerais -->
        <div class="eg-form-section">
            <h4>📋 Dados Gerais</h4>
            <div class="form-group">
                <label>Nome do Template *</label>
                <input type="text" id="egTplName"
                       value="${escapeHtml(t?.name || '')}"
                       placeholder="Ex: TCU 2024 — Técnico Administrativo">
            </div>
            <div class="form-group">
                <label>Descrição</label>
                <textarea id="egTplDesc" rows="2"
                          style="width:100%;padding:8px;border:1px solid var(--border);
                                 border-radius:8px;font-size:13px;resize:vertical">
${escapeHtml(t?.description || '')}</textarea>
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px">
                <div class="form-group">
                    <label>Concurso</label>
                    <select id="egTplContest"></select>
                </div>
                <div class="form-group">
                    <label>Total de Questões</label>
                    <input type="number" id="egTplTotal"
                           value="${t?.totalQuestions || 20}"
                           min="5" max="120">
                </div>
                <div class="form-group">
                    <label>Tempo Limite (min)</label>
                    <input type="number" id="egTplTime"
                           value="${t?.timeLimitMin || 90}"
                           min="10" max="300">
                </div>
            </div>
        </div>

        <!-- Distribuição de dificuldade -->
        <div class="eg-form-section">
            <h4>📊 Distribuição de Dificuldade</h4>
            <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px">
                <div class="form-group">
                    <label>Fácil (%)</label>
                    <input type="number" id="egDiffFacil"
                           value="${t?.difficultyDist?.facil ?? 30}"
                           min="0" max="100"
                           oninput="validateDiffDist()">
                </div>
                <div class="form-group">
                    <label>Médio (%)</label>
                    <input type="number" id="egDiffMedio"
                           value="${t?.difficultyDist?.medio ?? 50}"
                           min="0" max="100"
                           oninput="validateDiffDist()">
                </div>
                <div class="form-group">
                    <label>Difícil (%)</label>
                    <input type="number" id="egDiffDificil"
                           value="${t?.difficultyDist?.dificil ?? 20}"
                           min="0" max="100"
                           oninput="validateDiffDist()">
                </div>
            </div>
            <div id="egDiffWarning" style="display:none;color:var(--danger);
                 font-size:12px;margin-top:4px">
                ⚠️ A soma deve ser 100%.
            </div>
        </div>

        <!-- Disciplinas e conteúdo -->
        <div class="eg-form-section">
            <div style="display:flex;justify-content:space-between;
                        align-items:center;margin-bottom:10px">
                <h4>📚 Disciplinas e Conteúdos</h4>
                <button class="btn btn-secondary btn-sm"
                        onclick="addDiscipline()">
                    ➕ Adicionar Disciplina
                </button>
            </div>
            <div id="egDisciplineList">
                ${_disciplines.map((d, i) =>
                    renderDisciplineRow(d, i)).join('')}
                ${!_disciplines.length ? `
                <div style="text-align:center;padding:20px;
                            color:var(--text-muted);font-size:13px">
                    Sem disciplinas — a IA usará o contexto geral.
                </div>` : ''}
            </div>
        </div>

        <!-- Instruções especiais para IA -->
        <div class="eg-form-section">
            <h4>🤖 Instruções Especiais para a IA (opcional)</h4>
            <textarea id="egTplStyle" rows="3"
                      style="width:100%;padding:8px;border:1px solid var(--border);
                             border-radius:8px;font-size:13px;resize:vertical"
                      placeholder="Ex: Foque em questões sobre controle externo e TCU. Evite questões sobre contratos.">
${escapeHtml(t?.styleNotes || '')}</textarea>
        </div>

        <div style="display:flex;gap:10px;justify-content:flex-end;
                    margin-top:16px">
            <button class="btn btn-secondary" onclick="closeTemplateModal()">
                Cancelar
            </button>
            <button class="btn btn-primary" onclick="saveTemplate()">
                💾 Salvar Template
            </button>
        </div>`;

    // Popula select de concurso
    loadContestsIntoSelect('egTplContest').then(() => {
        if (t?.contestId) {
            const sel = document.getElementById('egTplContest');
            if (sel) sel.value = t.contestId;
        }
    });
}

function renderDisciplineRow(d, index) {
    return `
    <div class="eg-disc-row" id="disc-row-${index}">
        <div style="display:grid;grid-template-columns:2fr 1fr 60px;
                    gap:8px;align-items:start">
            <div class="form-group" style="margin:0">
                <label style="font-size:11px">Disciplina</label>
                <input type="text" id="disc-name-${index}"
                       value="${escapeHtml(d.discipline || '')}"
                       placeholder="Ex: Direito Administrativo"
                       list="discSuggestions">
                <datalist id="discSuggestions">
                    <option value="Direito Administrativo">
                    <option value="Direito Constitucional">
                    <option value="Direito Financeiro">
                    <option value="Controle Externo">
                    <option value="Administração Pública">
                    <option value="Contabilidade Pública">
                    <option value="Auditoria Governamental">
                    <option value="Língua Portuguesa">
                    <option value="Raciocínio Lógico">
                </datalist>
            </div>
            <div class="form-group" style="margin:0">
                <label style="font-size:11px">Nº de questões</label>
                <input type="number" id="disc-count-${index}"
                       value="${d.questionCount || 5}"
                       min="1" max="50">
            </div>
            <button class="btn btn-sm"
                    style="background:#fee2e2;color:#991b1b;margin-top:20px"
                    onclick="removeDiscipline(${index})">🗑️</button>
        </div>
        <div class="form-group" style="margin-top:6px">
            <label style="font-size:11px">Base Legal</label>
            <input type="text" id="disc-law-${index}"
                   value="${escapeHtml(d.lawReference || '')}"
                   placeholder="Ex: Lei 8.112/90, CF/88 Art. 37">
        </div>
        <div class="form-group" style="margin-top:6px">
            <label style="font-size:11px">Tópicos específicos
                (separados por vírgula)</label>
            <input type="text" id="disc-topics-${index}"
                   value="${escapeHtml((d.topics || []).join(', '))}"
                   placeholder="Ex: Princípios LIMPE, Atos administrativos">
        </div>
        <div class="form-group" style="margin-top:6px">
            <label style="font-size:11px">Contexto adicional (opcional)</label>
            <textarea id="disc-ctx-${index}" rows="2"
                      style="width:100%;padding:6px;border:1px solid var(--border);
                             border-radius:6px;font-size:12px;resize:vertical"
                      placeholder="Instruções específicas para esta disciplina"
                      >${escapeHtml(d.extraContext || '')}</textarea>
        </div>
    </div>`;
}

function addDiscipline() {
    _disciplines.push({
        discipline: '', lawReference: '', topics: [],
        questionCount: 5, extraContext: ''
    });
    const list = document.getElementById('egDisciplineList');
    const idx  = _disciplines.length - 1;
    const empty = list.querySelector('div[style*="text-align:center"]');
    if (empty) empty.remove();
    list.insertAdjacentHTML('beforeend',
            renderDisciplineRow(_disciplines[idx], idx));
}

function removeDiscipline(index) {
    _disciplines.splice(index, 1);
    const row = document.getElementById(`disc-row-${index}`);
    if (row) row.remove();
    // Renumera os índices restantes
    document.querySelectorAll('.eg-disc-row').forEach((row, i) => {
        row.id = `disc-row-${i}`;
    });
}

function validateDiffDist() {
    const f  = parseInt(document.getElementById('egDiffFacil')?.value   || 0);
    const m  = parseInt(document.getElementById('egDiffMedio')?.value   || 0);
    const d  = parseInt(document.getElementById('egDiffDificil')?.value || 0);
    const ok = f + m + d === 100;
    const w  = document.getElementById('egDiffWarning');
    if (w) w.style.display = ok ? 'none' : 'block';
    return ok;
}

function collectDisciplines() {
    const result = [];
    document.querySelectorAll('.eg-disc-row').forEach((row, i) => {
        const name   = document.getElementById(`disc-name-${i}`)?.value?.trim();
        const count  = parseInt(document.getElementById(`disc-count-${i}`)?.value || 5);
        const law    = document.getElementById(`disc-law-${i}`)?.value?.trim();
        const topics = document.getElementById(`disc-topics-${i}`)?.value
                .split(',').map(t => t.trim()).filter(Boolean);
        const ctx    = document.getElementById(`disc-ctx-${i}`)?.value?.trim();
        if (name) {
            result.push({
                discipline:   name,
                questionCount: count,
                lawReference:  law || null,
                topics:        topics,
                extraContext:  ctx || null,
            });
        }
    });
    return result;
}

async function saveTemplate() {
    if (!validateDiffDist()) {
        showToast('A soma das dificuldades deve ser 100%', 'error'); return;
    }

    const name = document.getElementById('egTplName')?.value?.trim();
    if (!name) { showToast('Informe o nome do template', 'error'); return; }

    const disciplines = collectDisciplines();
    const totalQ      = parseInt(document.getElementById('egTplTotal')?.value || 20);

    // Valida soma de questões por disciplina
    if (disciplines.length > 0) {
        const sumQ = disciplines.reduce((s, d) => s + (d.questionCount || 0), 0);
        if (sumQ !== totalQ) {
            const ok = confirm(
                `A soma das questões por disciplina (${sumQ}) ` +
                `difere do total (${totalQ}). Continuar assim?`
            );
            if (!ok) return;
        }
    }

    const payload = {
        name,
        description:      document.getElementById('egTplDesc')?.value?.trim() || null,
        contestId:        parseInt(document.getElementById('egTplContest')?.value) || null,
        totalQuestions:   totalQ,
        timeLimitMin:     parseInt(document.getElementById('egTplTime')?.value || 90),
        disciplineConfig: disciplines,
        difficultyDist: {
            facil:   parseInt(document.getElementById('egDiffFacil')?.value   || 30),
            medio:   parseInt(document.getElementById('egDiffMedio')?.value   || 50),
            dificil: parseInt(document.getElementById('egDiffDificil')?.value || 20),
        },
        styleNotes: document.getElementById('egTplStyle')?.value?.trim() || null,
        isActive:   true,
    };

    const editId = document.getElementById('egTemplateBody')?.dataset?.editId;

    try {
        if (editId) {
            // Update — usa POST pois não há PUT implementado
            await API.post(`/exam-generator/templates`, payload);
        } else {
            await API.post('/exam-generator/templates', payload);
        }
        closeTemplateModal();
        showToast('Template salvo!', 'success');
        await loadTemplates();
    } catch (e) {
        showToast('Erro ao salvar: ' + e.message, 'error');
    }
}

function closeTemplateModal() {
    document.getElementById('egTemplateModal')?.classList.remove('open');
    document.body.style.overflow = '';
}

async function deleteTemplate(id) {
    if (!confirm('Desativar este template?')) return;
    try {
        await fetch(`/api/exam-generator/templates/${id}`,
                    { method: 'DELETE' });
        showToast('Template desativado.', 'success');
        document.getElementById(`tpl-${id}`)?.remove();
    } catch (e) {
        showToast('Erro: ' + e.message, 'error');
    }
}

// ── Geração ──────────────────────────────────────────────────────────
async function generateExam(templateId) {
    const btn = event?.target;
    if (btn) { btn.disabled = true; btn.textContent = '⏳ Gerando...'; }

    try {
        const result = await API.post(
            `/exam-generator/templates/${templateId}/generate`, {});

        showToast('Prova sendo gerada! Acompanhe na lista abaixo.', 'success');
        await loadGeneratedExams();
        startPollingExam(result.examId);

    } catch (e) {
        showToast('Erro ao iniciar geração: ' + e.message, 'error');
    } finally {
        if (btn) {
            btn.disabled    = false;
            btn.textContent = '🚀 Gerar Prova';
        }
    }
}

async function loadGeneratedExams() {
    const list = document.getElementById('egExamList');
    if (!list) return;

    try {
        const exams = await API.get('/exam-generator/exams');

        if (!exams.length) {
            list.innerHTML = `
                <div class="empty-state">
                    <div class="empty-state-icon">📄</div>
                    <div class="empty-state-text">
                        Nenhuma prova gerada ainda.
                    </div>
                </div>`;
            return;
        }

        list.innerHTML = exams.map(e => renderExamCard(e)).join('');
    } catch (e) {
        list.innerHTML =
            `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

function renderExamCard(exam) {
    const statusConf = {
        GENERATING: { cls: 'badge-warning', label: '⚙️ Gerando...',  spin: true  },
        COMPLETED:  { cls: 'badge-success', label: '✅ Concluída',    spin: false },
        ERROR:      { cls: 'badge-danger',  label: '❌ Erro',          spin: false },
    };
    const sc = statusConf[exam.status] || statusConf.GENERATING;

    const date = new Date(exam.createdAt).toLocaleString('pt-BR');

    return `
    <div class="eg-exam-card" id="exam-${exam.id}">
        <div style="display:flex;justify-content:space-between;
                    align-items:flex-start;gap:10px;flex-wrap:wrap">
            <div>
                <div style="font-weight:700;font-size:14px">
                    ${escapeHtml(exam.name)}
                </div>
                <div style="font-size:12px;color:var(--text-muted);margin-top:3px">
                    📋 ${exam.totalQuestions} questões •
                    ${exam.ragUsed   ? '🧠 RAG '  : ''}
                    ${exam.cacheUsed ? '⚡ Cache ' : ''}
                    • ${date}
                </div>
            </div>
            <div style="display:flex;gap:6px;align-items:center;flex-wrap:wrap">
                <span class="badge ${sc.cls}">
                    ${sc.spin ? '<span class="mini-spinner"></span> ' : ''}
                    ${sc.label}
                </span>
                ${exam.status === 'COMPLETED' ? `
                <button class="btn btn-primary btn-sm"
                        onclick="viewExam(${exam.id})">
                    👁️ Ver Prova
                </button>
                <button class="btn btn-secondary btn-sm"
                        onclick="startExamSimulation(${exam.id})">
                    🎯 Iniciar Simulado
                </button>
                <button class="btn btn-secondary btn-sm"
                        onclick="downloadAnswerKey(${exam.id})">
                    📋 Gabarito
                </button>` : ''}
            </div>
        </div>
    </div>`;
}

// ── Polling status ────────────────────────────────────────────────────
function startPollingExam(examId) {
    if (_genPolling) clearInterval(_genPolling);
    _genPolling = setInterval(async () => {
        try {
            const exam = await API.get(`/exam-generator/exams/${examId}`);
            const card = document.getElementById(`exam-${examId}`);
            if (card) card.outerHTML = renderExamCard(exam);

            if (exam.status !== 'GENERATING') {
                clearInterval(_genPolling);
                _genPolling = null;
                if (exam.status === 'COMPLETED') {
                    showToast(
                        `✅ Prova "${exam.name}" gerada com ` +
                        `${exam.totalQuestions} questões!`,
                        'success'
                    );
                }
            }
        } catch (_) {}
    }, 3000);
}

// ── Visualizar prova gerada ───────────────────────────────────────────
async function viewExam(examId) {
    const modal = document.getElementById('egExamViewModal');
    const body  = document.getElementById('egExamViewBody');
    if (!modal || !body) return;

    body.innerHTML = '<div class="loading-spinner">Carregando prova...</div>';
    modal.classList.add('open');
    document.body.style.overflow = 'hidden';

    try {
        const [exam, questions] = await Promise.all([
            API.get(`/exam-generator/exams/${examId}`),
            API.get(`/exam-generator/exams/${examId}/questions`),
        ]);

        document.getElementById('egExamViewTitle').textContent = exam.name;

        body.innerHTML = `
            <div style="margin-bottom:16px;display:flex;gap:12px;
                        flex-wrap:wrap;font-size:13px">
                <span>📋 <strong>${questions.length}</strong> questões</span>
                ${exam.ragUsed   ? '<span>🧠 Gerada com RAG</span>'   : ''}
                ${exam.cacheUsed ? '<span>⚡ Usou cache semântico</span>' : ''}
            </div>

            ${questions.map((q, i) => `
            <div class="eg-question-card">
                <div class="eg-question-header">
                    <span class="eg-question-num">${q.orderNumber}</span>
                    <div style="flex:1">
                        <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:6px">
                            ${q.discipline ? `
                            <span class="badge badge-info" style="font-size:10px">
                                ${escapeHtml(q.discipline)}
                            </span>` : ''}
                            <span class="badge badge-${
                                q.difficulty === 'FACIL'   ? 'success' :
                                q.difficulty === 'DIFICIL' ? 'danger'  : 'warning'
                            }" style="font-size:10px">
                                ${q.difficulty || 'MEDIO'}
                            </span>
                        </div>
                        <div class="eg-question-stmt">
                            ${escapeHtml(q.statement)}
                        </div>
                    </div>
                    <!-- Gabarito oculto por padrão -->
                    <button class="btn btn-sm eg-reveal-btn"
                            style="background:var(--bg);color:var(--text-muted)"
                            onclick="revealAnswer(this, ${q.correctAnswer})">
                        👁️ Ver gabarito
                    </button>
                </div>
                ${q.lawReference ? `
                <div style="font-size:12px;color:var(--text-muted);
                            margin-top:6px">
                    📖 ${escapeHtml(q.lawReference)}
                </div>` : ''}
                ${q.explanation ? `
                <div class="eg-explanation" style="display:none"
                     id="expl-${examId}-${i}">
                    💬 ${escapeHtml(q.explanation)}
                </div>` : ''}
                <button class="btn btn-sm"
                        style="margin-top:6px;font-size:11px"
                        onclick="toggleExplanation('expl-${examId}-${i}')">
                    📖 Ver explicação
                </button>
            </div>`).join('')}`;

    } catch (e) {
        body.innerHTML =
            `<p style="color:var(--danger)">Erro: ${e.message}</p>`;
    }
}

function revealAnswer(btn, isCorrect) {
    btn.textContent = isCorrect ? '✅ CERTO' : '❌ ERRADO';
    btn.style.background = isCorrect
            ? 'var(--success)' : 'var(--danger)';
    btn.style.color = '#fff';
    btn.onclick = null;
}

function toggleExplanation(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.style.display = el.style.display === 'none' ? 'block' : 'none';
}

async function downloadAnswerKey(examId) {
    try {
        const [exam, key] = await Promise.all([
            API.get(`/exam-generator/exams/${examId}`),
            API.get(`/exam-generator/exams/${examId}/answer-key`),
        ]);

        const text = `GABARITO — ${exam.name}\n` +
                '='.repeat(40) + '\n\n' +
                key.map(k =>
                    `Q${String(k.order).padStart(2,'0')}: ${
                        k.answer ? 'CERTO' : 'ERRADO'}`
                ).join('\n') +
                '\n\n' +
                `Total: ${key.length} questões\n` +
                `Certas: ${key.filter(k => k.answer).length}\n` +
                `Erradas: ${key.filter(k => !k.answer).length}`;

        const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
        const url  = URL.createObjectURL(blob);
        const a    = document.createElement('a');
        a.href     = url;
        a.download = `gabarito_${examId}.txt`;
        a.click();
        URL.revokeObjectURL(url);

    } catch (e) {
        showToast('Erro ao baixar gabarito: ' + e.message, 'error');
    }
}

async function startExamSimulation(examId) {
    try {
        const data = await API.post(
            `/exam-generator/exams/${examId}/start-simulation`, {});

        // Injeta questões no simulador e redireciona
        sessionStorage.setItem('generatedExamData', JSON.stringify(data));
        window.location.href = '/simulation.html?mode=generated';

    } catch (e) {
        showToast('Erro: ' + e.message, 'error');
    }
}

function closeExamViewModal() {
    document.getElementById('egExamViewModal')?.classList.remove('open');
    document.body.style.overflow = '';
}