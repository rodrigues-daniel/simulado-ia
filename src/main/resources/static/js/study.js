// ── Módulo de Estudo Inverso ────────────────────────────────────────────
let questions        = [];
let currentIndex     = 0;
let sessionId        = null;
let sessionStats     = { correct: 0, wrong: 0, skipped: 0 };
let questionStartTime = null;

// ── Fila de pré-carregamento ────────────────────────────────────────────
// Cada item: { question, prefetchedExplanation, prefetchDone, prefetchError }
let prefetchQueue    = [];
const PREFETCH_AHEAD = 2; // quantas questões à frente pré-carregar

document.addEventListener('DOMContentLoaded', async () => {
    await loadContestsIntoSelect('contestSelect');

    const params = new URLSearchParams(location.search);
    if (params.get('topicId')) {
        await loadTopics();
        document.getElementById('topicSelect').value = params.get('topicId');
        await startStudy();
    }
});

async function loadTopics() {
    const contestId = document.getElementById('contestSelect').value;
    if (!contestId) return;
    try {
        const topics = await API.get(`/admin/topics/${contestId}`);
        const sel    = document.getElementById('topicSelect');
        sel.innerHTML = '<option value="">Selecione o tópico</option>' +
            topics.map(t => `<option value="${t.id}">${t.name}</option>`).join('');
    } catch (e) {
        showToast('Erro ao carregar tópicos', 'error');
    }
}

// ── Início da sessão ────────────────────────────────────────────────────
async function startStudy() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) { showToast('Selecione um tópico', 'error'); return; }

    // Mostra indicador de carregamento no botão
    const btn = document.querySelector('button[onclick="startStudy()"]');
    if (btn) { btn.disabled = true; btn.textContent = '⏳ Carregando...'; }

    try {
        // Uma única chamada: cria sessão + retorna (ou gera) questões
        const response = await API.post('/study/sessions', {
            topicId: parseInt(topicId)
        });

        sessionId = response.sessionId;
        questions = response.questions || [];

        // Exibe aviso se as questões vieram da IA
        if (response.message) {
            showStudySourceBanner(response.source, response.message);
        }

        if (!questions.length) {
            showToast(
                response.message || 'Nenhuma questão disponível para este tópico.',
                'error'
            );
            return;
        }

        currentIndex  = 0;
        sessionStats  = { correct: 0, wrong: 0, skipped: 0 };
        prefetchQueue = questions.map(q => ({
            question:              q,
            prefetchedExplanation: null,
            prefetchDone:          false,
            prefetchError:         false
        }));

        show('questionArea');
        hide('topicSelector');
        hide('feedbackArea');
        hide('sessionResult');
        hide('processingBox');

        renderQuestion();
        schedulePrefetch();

    } catch (e) {
        console.error('[startStudy] erro:', e);
        showToast('Erro ao iniciar estudo. Verifique o servidor.', 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = '🚀 Iniciar Estudo'; }
    }
}


// Banner informativo sobre a origem das questões
function showStudySourceBanner(source, message) {
    // Remove banner anterior se existir
    const existing = document.getElementById('sourceBanner');
    if (existing) existing.remove();

    const isAI    = source === 'AI_GENERATED';
    const isError = source === 'AI_ERROR' || source === 'AI_EMPTY';

    const banner = document.createElement('div');
    banner.id    = 'sourceBanner';
    banner.style.cssText = `
        padding: 12px 18px;
        border-radius: 8px;
        margin-bottom: 16px;
        font-size: 13px;
        font-weight: 600;
        display: flex;
        align-items: flex-start;
        gap: 10px;
        background: ${isError ? '#fee2e2' : '#fef9c3'};
        color:      ${isError ? '#991b1b' : '#854d0e'};
        border:     1px solid ${isError ? '#fca5a5' : '#fde047'};
    `;

    banner.innerHTML = `
        <span style="font-size:18px;flex-shrink:0">${isError ? '⚠️' : '🤖'}</span>
        <span>${message}</span>
    `;

    // Insere antes da questionArea
    const questionArea = document.getElementById('questionArea');
    if (questionArea) {
        questionArea.parentNode.insertBefore(banner, questionArea);
    }
}


async function generateAIQuestions() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) { showToast('Selecione um tópico primeiro', 'error'); return; }
    showToast('Gerando questões com IA... aguarde', 'success');
    try {
        await API.post('/questions/generate', { topicId: parseInt(topicId), count: 10 });
        showToast('10 questões geradas com sucesso!', 'success');
    } catch (e) {
        showToast('Erro ao gerar questões', 'error');
    }
}

// ── Renderiza questão atual ─────────────────────────────────────────────
function renderQuestion() {
    if (currentIndex >= questions.length) {
        showSessionResult();
        return;
    }

    const q = questions[currentIndex];

    document.getElementById('currentQ').textContent         = currentIndex + 1;
    document.getElementById('totalQ').textContent           = questions.length;
    document.getElementById('questionStatement').textContent = q.statement;

    hide('feedbackArea');
    hide('processingBox');

    // Habilita botões
    setAnswerButtons(true);

    // Palavras-armadilha
    const traps     = q.trapKeywords || [];
    const trapAlert = document.getElementById('trapAlert');
    if (traps.length) {
        document.getElementById('trapWords').textContent = traps.join(', ');
        trapAlert.style.display = 'flex';
    } else {
        trapAlert.style.display = 'none';
    }

    // Dificuldade
    const diffMap   = { FACIL: 'badge-success', MEDIO: 'badge-warning', DIFICIL: 'badge-danger' };
    const diffBadge = document.getElementById('difficultyBadge');
    diffBadge.className   = `badge ${diffMap[q.difficulty] || 'badge-info'}`;
    diffBadge.textContent = q.difficulty || '';

    // Indicador de pré-carregamento (info discreta)
    updatePrefetchIndicator();

    questionStartTime = Date.now();
}

// ── Pré-carregamento em background ─────────────────────────────────────
//
// Estratégia:
//   1. Para cada questão à frente (até PREFETCH_AHEAD), busca do banco
//      se já existe explanation — se sim, armazena em prefetchQueue
//   2. Se não houver explanation no banco, agenda geração via IA
//      em background sem bloquear a UI
//   3. Quando o usuário responder ERRADO, a explicação já está pronta
//      e é exibida instantaneamente

async function schedulePrefetch() {
    const end = Math.min(currentIndex + PREFETCH_AHEAD + 1, questions.length);

    for (let i = currentIndex; i < end; i++) {
        const item = prefetchQueue[i];
        if (!item || item.prefetchDone || item.prefetchError) continue;

        // Não bloqueia — roda em background
        prefetchItem(i);
    }
}

async function prefetchItem(index) {
    const item = prefetchQueue[index];
    if (!item || item.prefetchDone) return;

    const q = item.question;

    try {
        // ── Caso 1: banco relacional tem explicação suficiente ──────────
        if (q.explanation && q.explanation.trim().length > 10) {
            item.prefetchedExplanation = q.explanation;
            item.prefetchSource        = 'database'; // ← rastreia origem
            item.prefetchDone          = true;
            updatePrefetchIndicator();
            return;
        }

        // ── Caso 2: banco vazio — tenta Ollama + RAG ───────────────────
        const ollamaOk = await checkOllamaAvailable();
        if (!ollamaOk) {
            item.prefetchDone  = true;
            item.prefetchError = true;
            item.prefetchSource = 'unavailable';
            return;
        }

        const result = await API.post(
            `/questions/${q.id}/prefetch-explanation`, {}
        );

        item.prefetchedExplanation = result.explanation || null;
        item.prefetchSource        = result.source || 'ai'; // 'database','ai','fallback'
        item.prefetchDone          = true;
        updatePrefetchIndicator();

    } catch (e) {
        item.prefetchDone   = true;
        item.prefetchError  = true;
        item.prefetchSource = 'error';
    }
}

async function checkOllamaAvailable() {
    try {
        const res = await fetch('/api/ai/health', { method: 'GET' });
        return res.ok;
    } catch (_) {
        return false;
    }
}

function updatePrefetchIndicator() {
    const indicator = document.getElementById('prefetchIndicator');
    if (!indicator) return;

    const next = prefetchQueue[currentIndex + 1];
    if (!next) {
        indicator.style.display = 'none';
        return;
    }

    if (next.prefetchDone && !next.prefetchError) {
        indicator.style.display  = 'flex';
        indicator.className      = 'prefetch-indicator ready';
        indicator.innerHTML      = '⚡ Próxima questão pré-carregada';
    } else if (!next.prefetchDone) {
        indicator.style.display  = 'flex';
        indicator.className      = 'prefetch-indicator loading';
        indicator.innerHTML      = '<span class="mini-spinner"></span> Pré-carregando próxima...';
    } else {
        indicator.style.display = 'none';
    }
}

// ── Resposta do usuário ─────────────────────────────────────────────────
async function answer(userAnswer) {
    const q         = questions[currentIndex];
    const timeSpent = Date.now() - (questionStartTime || Date.now());

    if (!sessionId) {
        showToast('Sessão inválida. Reinicie o estudo.', 'error');
        return;
    }

    setAnswerButtons(false);
    showProcessingBox(userAnswer);

    try {
        // Verifica se já temos a explicação pré-carregada
        const prefetchItem = prefetchQueue[currentIndex];
        const hasPrefetch  = prefetchItem?.prefetchDone
                          && !prefetchItem?.prefetchError
                          && prefetchItem?.prefetchedExplanation;

        // Envia resposta ao servidor
        const result = await API.post(`/questions/${q.id}/answer`, {
            sessionId:   sessionId,
            answer:      userAnswer,
            timeSpentMs: timeSpent
        });

        // ── CORREÇÃO DO BUG DE INVERSÃO ────────────────────────────────
        // result.isCorrect vem do servidor — nunca inverte
        // Apenas usa o valor booleano diretamente sem nenhuma conversão
        const isCorrect = result.isCorrect === true;

        if (isCorrect) {
            sessionStats.correct++;
        } else {
            sessionStats.wrong++;

            // Se temos explicação pré-carregada e o servidor não trouxe uma via IA
            // injeta a pré-carregada para exibição imediata
            if (hasPrefetch && !result.professorExplanation) {
                result.professorExplanation = prefetchItem.prefetchedExplanation;
            }
        }

        // Avança o pré-carregamento para as próximas questões
        currentIndex++;
        schedulePrefetch();
        currentIndex--; // volta para renderFeedback usar o índice correto

        renderFeedback(result, userAnswer, isCorrect);

    } catch (e) {
        console.error('[answer] erro:', e);
        hideProcessingBox();
        setAnswerButtons(true);
        showToast('Erro ao registrar resposta. Tente novamente.', 'error');
    }
}

// ── Processing Box ──────────────────────────────────────────────────────
function showProcessingBox(userAnswer) {
    const choiceLabel = userAnswer === true  ? '✅ Você marcou: CERTO'
                      : userAnswer === false ? '❌ Você marcou: ERRADO'
                      : '— Em branco';

    document.getElementById('processingAnswer').textContent = choiceLabel;
    document.getElementById('processingStatus').textContent = 'Registrando resposta...';
    document.getElementById('processingBar').style.width    = '25%';
    document.getElementById('processingSource').textContent = '';

    show('processingBox');

    const item   = prefetchQueue[currentIndex];
    const source = item?.prefetchSource;
    const done   = item?.prefetchDone && !item?.prefetchError;

    setTimeout(() => {
        document.getElementById('processingStatus').textContent = 'Verificando gabarito...';
        document.getElementById('processingBar').style.width    = '50%';
    }, 250);

    setTimeout(() => {
        if (done) {
            // Mensagem honesta sobre a origem real
            const { label, detail } = getSourceLabel(source);
            document.getElementById('processingStatus').textContent = label;
            document.getElementById('processingSource').textContent = detail;
        } else {
            document.getElementById('processingStatus').textContent =
                '🤖 Consultando professor virtual via IA...';
            document.getElementById('processingSource').textContent =
                'PGVector (banco vetorial) + llama3.2:3b estão gerando a explicação.';
        }
        document.getElementById('processingBar').style.width = '75%';
    }, 600);
}

function getSourceLabel(source) {
    switch (source) {
        case 'database':
            return {
                label:  '📖 Carregando explicação do banco de dados...',
                detail: 'Explicação recuperada do PostgreSQL — sem uso de IA.'
            };
        case 'ai':
            return {
                label:  '🤖 Carregando explicação gerada por IA...',
                detail: 'Gerada pelo llama3.2:3b com contexto do banco vetorial (PGVector).'
            };
        case 'fallback':
            return {
                label:  '📖 Carregando explicação de fallback...',
                detail: 'IA indisponível. Usando explicação estática do banco de dados.'
            };
        default:
            return {
                label:  '⚡ Carregando explicação...',
                detail: ''
            };
    }
}
function hideProcessingBox() {
    hide('processingBox');
    document.getElementById('processingBar').style.width = '0%';
}

// ── Renderiza feedback ──────────────────────────────────────────────────
function renderFeedback(result, userAnswer, isCorrect) {
    document.getElementById('processingBar').style.width = '100%';

    setTimeout(() => {
        hideProcessingBox();
        hide('questionArea');
        show('feedbackArea');

        const q = questions[currentIndex];

        // ── Origem da questão ───────────────────────────────────────────
        const originTag = result.fromIA
            ? '<span class="question-origin-tag from-ia">🤖 Gerada por IA</span>'
            : '<span class="question-origin-tag from-manual">📝 Base Manual</span>';

        const resultEl = document.getElementById('answerResult');

        if (isCorrect) {
            resultEl.innerHTML = `
                <div class="answer-correct">
                    <div style="display:flex;justify-content:flex-end;margin-bottom:8px">
                        ${originTag}
                    </div>
                    <div class="answer-big-icon">✅</div>
                    <div class="answer-title correct">CORRETO!</div>
                    <div class="answer-subtitle">
                        ${result.explanation || 'Muito bem! Continue assim.'}
                    </div>
                </div>`;
        } else {
            resultEl.innerHTML = `
                <div class="answer-wrong">
                    <div style="display:flex;justify-content:flex-end;margin-bottom:8px">
                        ${originTag}
                    </div>
                    <div class="answer-big-icon">❌</div>
                    <div class="answer-title wrong">INCORRETO!</div>
                    <div class="answer-subtitle">
                        Resposta correta:
                        <strong>${result.correctAnswer ? 'CERTO' : 'ERRADO'}</strong>
                    </div>
                </div>`;
        }

        // ── Parágrafo da lei ────────────────────────────────────────────
        const lawBox = document.getElementById('lawParagraphBox');
        if (!isCorrect && result.lawParagraph) {
            document.getElementById('lawReference').textContent =
                result.lawReference || 'Base Legal';
            document.getElementById('lawParagraph').textContent =
                result.lawParagraph;
            lawBox.style.display = 'block';
        } else {
            lawBox.style.display = 'none';
        }

        // ── Campo do professor ──────────────────────────────────────────
        const profBox = document.getElementById('professorBox');
        if (!isCorrect && result.professorExplanation) {
            document.getElementById('professorExplanation').textContent =
                result.professorExplanation;
            document.getElementById('professorTip').textContent =
                result.professorTip ? `💡 ${result.professorTip}` : '';

            // Tag de origem da explicação
            const sourceTag  = document.getElementById('professorSource');
            const ragScore   = result.ragScore
                ? ` (score: ${(result.ragScore * 100).toFixed(0)}%)`
                : '';

            const tagConfig = {
                CACHE_DB:  { text: '📖 Cache — banco',          cls: 'from-db'  },
                CACHE_RAG: { text: `⚡ Cache — IA + RAG${ragScore}`, cls: 'from-ai' },
                AI_RAG:    { text: `🤖 IA + Banco Vetorial${ragScore}`, cls: 'from-ai' },
                AI_ONLY:   { text: '🤖 IA (sem RAG)',            cls: 'from-ai'  },
                FALLBACK:  { text: '📖 Fallback estático',       cls: 'from-fallback' }
            };
            const cfg = tagConfig[result.explanationSource] ||
                        { text: '🤖 IA', cls: 'from-ai' };
            if (sourceTag) {
                sourceTag.textContent = cfg.text;
                sourceTag.className   = `prof-source-tag ${cfg.cls}`;
            }

            profBox.style.display = 'block';
        } else {
            profBox.style.display = 'none';
        }

        // ── Barra de ações (salvar + anotar) ────────────────────────────
        renderActionBar(q.id, result.isSaved);

    }, 350);
}

// ── Barra de ações ──────────────────────────────────────────────────────
function renderActionBar(questionId, isSaved) {
    let bar = document.getElementById('actionBar');
    if (!bar) {
        bar = document.createElement('div');
        bar.id = 'actionBar';
        document.getElementById('feedbackArea').appendChild(bar);
    }

    bar.className = 'action-bar';
    bar.innerHTML = `
        <button class="btn-action ${isSaved ? 'saved' : ''}"
                id="btnSave" onclick="toggleSave(${questionId})">
            ${isSaved ? '🔖 Salvo' : '📌 Salvar questão'}
        </button>
        <button class="btn-action" onclick="toggleNotes(${questionId})">
            📝 Anotações
        </button>
    `;

    // Carrega notas existentes
    loadNotes(questionId);
}

// ── Salvar / desfavoritar ───────────────────────────────────────────────
async function toggleSave(questionId) {
    const btn    = document.getElementById('btnSave');
    const isSaved = btn.classList.contains('saved');

    try {
        if (isSaved) {
            await fetch(`/api/questions/${questionId}/save`, { method: 'DELETE' });
            btn.classList.remove('saved');
            btn.textContent = '📌 Salvar questão';
            showToast('Questão removida dos favoritos', 'success');
        } else {
            await API.post(`/questions/${questionId}/save`, {});
            btn.classList.add('saved');
            btn.textContent = '🔖 Salvo';
            showToast('Questão salva!', 'success');
        }
    } catch (e) {
        showToast('Erro ao salvar questão', 'error');
    }
}

// ── Notas ───────────────────────────────────────────────────────────────
let notesVisible = false;
let currentNotesQuestionId = null;

async function toggleNotes(questionId) {
    currentNotesQuestionId = questionId;
    let notesBox = document.getElementById('notesBox');

    if (notesBox && notesVisible && currentNotesQuestionId === questionId) {
        notesBox.style.display = 'none';
        notesVisible = false;
        return;
    }

    notesVisible = true;
    await loadNotes(questionId);
}


async function loadNotes(questionId) {
    let notesBox = document.getElementById('notesBox');
    if (!notesBox) {
        notesBox = document.createElement('div');
        notesBox.id = 'notesBox';
        notesBox.className = 'notes-box';
        document.getElementById('feedbackArea').appendChild(notesBox);
    }

    try {
        const notes = await API.get(`/questions/${questionId}/notes`);
        renderNotesBox(questionId, notes);
        notesBox.style.display = 'block';
    } catch (e) {
        console.error('Erro ao carregar notas:', e);
    }
}

function renderNotesBox(questionId, notes) {
    const box = document.getElementById('notesBox');
    box.innerHTML = `
        <div class="notes-header">
            <span>📝 Minhas Anotações</span>
            <button class="btn-icon" onclick="document.getElementById('notesBox').style.display='none'">
                ✕
            </button>
        </div>

        <div id="notesList" class="notes-list">
            ${notes.length ? notes.map(n => renderNoteItem(n)).join('') :
              '<p style="color:var(--text-muted);font-size:13px">Nenhuma anotação ainda.</p>'}
        </div>

        <div class="notes-input-row">
            <textarea id="newNoteInput" class="notes-textarea"
                      placeholder="Escreva sua anotação sobre esta questão..."
                      rows="3"></textarea>
            <button class="btn btn-primary btn-sm"
                    onclick="addNote(${questionId})">
                💾 Salvar
            </button>
        </div>
    `;
}

function renderNoteItem(note) {
    const date = new Date(note.createdAt).toLocaleDateString('pt-BR');
    return `
    <div class="note-item" id="note-${note.id}">
        <div class="note-text" id="note-text-${note.id}">${escapeHtml(note.note)}</div>
        <div class="note-meta">
            <span>${date}</span>
            <div style="display:flex;gap:6px">
                <button class="btn-icon-sm" onclick="editNote(${note.id})">✏️</button>
                <button class="btn-icon-sm" onclick="deleteNote(${note.id})">🗑️</button>
            </div>
        </div>
    </div>`;
}

async function addNote(questionId) {
    const input = document.getElementById('newNoteInput');
    const text  = input?.value?.trim();
    if (!text) { showToast('Escreva algo antes de salvar', 'error'); return; }

    try {
        const note = await API.post(`/questions/${questionId}/notes`, { note: text });
        const list = document.getElementById('notesList');
        const empty = list.querySelector('p');
        if (empty) empty.remove();
        list.insertAdjacentHTML('afterbegin', renderNoteItem(note));
        input.value = '';
        showToast('Anotação salva!', 'success');
    } catch (e) {
        showToast('Erro ao salvar anotação', 'error');
    }
}

function editNote(noteId) {
    const textEl = document.getElementById(`note-text-${noteId}`);
    const current = textEl.textContent;
    textEl.innerHTML = `
        <textarea id="edit-${noteId}" style="width:100%;padding:6px;border:1px solid var(--border);
                  border-radius:6px;font-size:13px;resize:vertical"
                  rows="3">${current}</textarea>
        <div style="display:flex;gap:6px;margin-top:6px">
            <button class="btn btn-primary btn-sm"
                    onclick="saveEditNote(${noteId})">💾 Salvar</button>
            <button class="btn btn-secondary btn-sm"
                    onclick="cancelEditNote(${noteId}, \`${escapeHtml(current)}\`)">
                Cancelar
            </button>
        </div>`;
}

async function saveEditNote(noteId) {
    const input = document.getElementById(`edit-${noteId}`);
    const text  = input?.value?.trim();
    if (!text) return;

    try {
        await API.put(`/questions/notes/${noteId}`, { note: text });
        const textEl = document.getElementById(`note-text-${noteId}`);
        textEl.innerHTML = escapeHtml(text);
        showToast('Anotação atualizada!', 'success');
    } catch (e) {
        showToast('Erro ao atualizar', 'error');
    }
}

function cancelEditNote(noteId, original) {
    const textEl = document.getElementById(`note-text-${noteId}`);
    textEl.textContent = original;
}

async function deleteNote(noteId) {
    if (!confirm('Excluir esta anotação?')) return;
    try {
        await fetch(`/api/questions/notes/${noteId}`, { method: 'DELETE' });
        document.getElementById(`note-${noteId}`)?.remove();
        showToast('Anotação excluída', 'success');
    } catch (e) {
        showToast('Erro ao excluir', 'error');
    }
}


// ── Próxima questão ─────────────────────────────────────────────────────
function nextQuestion() {
    currentIndex++;
    hide('feedbackArea');
    show('questionArea');
    renderQuestion();
    schedulePrefetch(); // agenda pré-carregamento das próximas
}

// ── Resultado final ─────────────────────────────────────────────────────
function showSessionResult() {
    hide('questionArea');
    hide('feedbackArea');
    show('sessionResult');

    const total = sessionStats.correct + sessionStats.wrong + sessionStats.skipped;
    const acc   = total > 0
        ? ((sessionStats.correct / total) * 100).toFixed(1)
        : 0;

    document.getElementById('sessionStats').innerHTML = `
        <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px;text-align:center">
            <div class="score-card">
                <span class="score-label">Corretas</span>
                <span class="score-value green">${sessionStats.correct}</span>
            </div>
            <div class="score-card">
                <span class="score-label">Erradas</span>
                <span class="score-value red">${sessionStats.wrong}</span>
            </div>
            <div class="score-card highlight">
                <span class="score-label">Aproveitamento</span>
                <span class="score-value">${acc}%</span>
            </div>
        </div>`;
}

function restartStudy() {
    currentIndex  = 0;
    sessionStats  = { correct: 0, wrong: 0, skipped: 0 };
    prefetchQueue = [];
    hide('sessionResult');
    show('topicSelector');
}

// ── Utilitários ─────────────────────────────────────────────────────────
function show(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'block';
}

function hide(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
}

function setAnswerButtons(enabled) {
    document.querySelectorAll('.btn-certo, .btn-errado')
            .forEach(b => b.disabled = !enabled);
}

// Chamado quando o usuário seleciona um tópico — antes de clicar em Iniciar
async function onTopicSelected() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) {
        hideSuggestionBanner();
        return;
    }

    try {
        const check = await API.get(`/ia-admin/topics/${topicId}/check`);
        if (check.suggestGenerate) {
            showSuggestionBanner(check);
        } else {
            hideSuggestionBanner();
        }
    } catch (_) {
        hideSuggestionBanner();
    }
}

function showSuggestionBanner(check) {
    let banner = document.getElementById('suggestionBanner');
    if (!banner) {
        banner = document.createElement('div');
        banner.id = 'suggestionBanner';
        const selector = document.getElementById('topicSelector');
        selector.appendChild(banner);
    }

    const isZero = check.total === 0;

    banner.style.cssText = `
        margin-top:14px;padding:14px 16px;border-radius:10px;
        background:${isZero ? '#ede9fe' : '#fef9c3'};
        border:1px solid ${isZero ? '#c4b5fd' : '#fde047'};
        font-size:13px;color:${isZero ? '#5b21b6' : '#854d0e'};
    `;

    banner.innerHTML = `
        <div style="display:flex;align-items:flex-start;gap:10px">
            <span style="font-size:20px;flex-shrink:0">${isZero ? '🤖' : '💡'}</span>
            <div style="flex:1">
                <div style="font-weight:700;margin-bottom:4px">
                    ${isZero ? 'Sem questões cadastradas' : 'Poucas questões disponíveis'}
                </div>
                <div style="margin-bottom:10px">${check.recommendation}</div>
                <div style="display:flex;gap:8px;flex-wrap:wrap">
                    <button class="btn btn-sm"
                            style="background:${isZero?'#7c3aed':'#d97706'};color:#fff"
                            onclick="generateAndStart()">
                        🤖 Gerar questões com IA e iniciar
                    </button>
                    ${!isZero ? `
                    <button class="btn btn-secondary btn-sm"
                            onclick="startStudyWithExisting()">
                        📚 Usar as ${check.approved} questões existentes
                    </button>` : ''}
                </div>
                ${check.ragChunks < 5 ? `
                <div style="margin-top:8px;font-size:12px;opacity:.8">
                    ⚠️ Banco vetorial com apenas ${check.ragChunks} chunk(s).
                    Adicione material em Admin → Material RAG para melhorar a qualidade das questões geradas.
                </div>` : ''}
            </div>
        </div>
    `;
}

function hideSuggestionBanner() {
    document.getElementById('suggestionBanner')?.remove();
}

async function generateAndStart() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) return;

    hideSuggestionBanner();
    const btn = document.querySelector('button[onclick="startStudy()"]');
    if (btn) { btn.disabled = true; btn.textContent = '🤖 Gerando questões...'; }

    try {
        await API.post('/questions/generate', { topicId: parseInt(topicId), count: 10 });
        showToast('Questões geradas! Iniciando estudo...', 'success');
        await startStudy();
    } catch (e) {
        showToast('Erro ao gerar questões. Tente iniciar o estudo mesmo assim.', 'error');
        if (btn) { btn.disabled = false; btn.textContent = '🚀 Iniciar Estudo'; }
    }
}

async function startStudyWithExisting() {
    hideSuggestionBanner();
    await startStudy();
}