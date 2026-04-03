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

    try {
        const session = await API.post('/study/sessions', { topicId: parseInt(topicId) });
        sessionId     = session.id;
        questions     = await API.get(`/questions/topic/${topicId}`);

        if (!questions.length) {
            showToast('Nenhuma questão encontrada. Gere com IA ou importe no Admin.', 'error');
            return;
        }

        currentIndex = 0;
        sessionStats = { correct: 0, wrong: 0, skipped: 0 };
        prefetchQueue = questions.map(q => ({
            question:             q,
            prefetchedExplanation: null,
            prefetchDone:         false,
            prefetchError:        false
        }));

        show('questionArea');
        hide('topicSelector');
        hide('feedbackArea');
        hide('sessionResult');
        hide('processingBox');

        renderQuestion();
        schedulePrefetch(); // inicia pré-carregamento em background

    } catch (e) {
        showToast('Erro ao iniciar estudo', 'error');
        console.error(e);
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
        // Se a questão já tem explanation estática no banco, usa ela
        if (q.explanation && q.explanation.trim().length > 10) {
            item.prefetchedExplanation = q.explanation;
            item.prefetchDone          = true;
            updatePrefetchIndicator();
            return;
        }

        // Não tem explanation — agenda geração via IA silenciosamente
        // Só pré-gera se o Ollama estiver disponível (verificação leve)
        const ollamaOk = await checkOllamaAvailable();
        if (!ollamaOk) {
            item.prefetchDone  = true;
            item.prefetchError = true;
            return;
        }

        // Chama endpoint de explicação prévia (sem registrar resposta)
        const result = await API.post(`/questions/${q.id}/prefetch-explanation`, {});
        item.prefetchedExplanation = result.explanation || null;
        item.prefetchDone          = true;
        updatePrefetchIndicator();

    } catch (e) {
        // Falha silenciosa — a explicação será gerada na hora se necessário
        item.prefetchDone  = true;
        item.prefetchError = true;
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
    // ── CORREÇÃO: mostra o que o usuário ESCOLHEU, não o gabarito ──────
    // userAnswer é o que o usuário clicou (true = CERTO, false = ERRADO)
    const choiceLabel = userAnswer === true  ? '✅ Você marcou: CERTO'
                      : userAnswer === false ? '❌ Você marcou: ERRADO'
                      : '— Em branco';

    document.getElementById('processingAnswer').textContent = choiceLabel;
    document.getElementById('processingStatus').textContent = 'Registrando resposta...';
    document.getElementById('processingBar').style.width    = '25%';
    document.getElementById('processingSource').textContent = '';

    show('processingBox');

    // Verifica se a próxima explicação já está pré-carregada
    const item = prefetchQueue[currentIndex];
    const hasPrefetch = item?.prefetchDone && !item?.prefetchError;

    setTimeout(() => {
        document.getElementById('processingStatus').textContent = 'Verificando gabarito...';
        document.getElementById('processingBar').style.width    = '50%';
    }, 250);

    setTimeout(() => {
        if (hasPrefetch) {
            document.getElementById('processingStatus').textContent =
                '⚡ Carregando explicação pré-processada...';
            document.getElementById('processingSource').textContent =
                'Explicação já estava em cache.';
        } else {
            document.getElementById('processingStatus').textContent =
                '🤖 Consultando professor virtual via IA...';
            document.getElementById('processingSource').textContent =
                'O modelo llama3.2:3b está gerando a explicação. Pode levar alguns segundos.';
        }
        document.getElementById('processingBar').style.width = '75%';
    }, 600);
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

        const resultEl = document.getElementById('answerResult');

        // ── Usa isCorrect calculado no bloco answer() ───────────────────
        if (isCorrect) {
            resultEl.innerHTML = `
                <div class="answer-correct">
                    <div class="answer-big-icon">✅</div>
                    <div class="answer-title correct">CORRETO!</div>
                    <div class="answer-subtitle">
                        ${result.explanation || 'Muito bem! Continue assim.'}
                    </div>
                </div>`;
        } else {
            resultEl.innerHTML = `
                <div class="answer-wrong">
                    <div class="answer-big-icon">❌</div>
                    <div class="answer-title wrong">INCORRETO!</div>
                    <div class="answer-subtitle">
                        Resposta correta:
                        <strong>${result.correctAnswer === true ? 'CERTO' : 'ERRADO'}</strong>
                    </div>
                </div>`;
        }

        // Parágrafo da lei — apenas no erro
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

        // Campo do professor — apenas no erro
        const profBox = document.getElementById('professorBox');
        if (!isCorrect && result.professorExplanation) {
            document.getElementById('professorExplanation').textContent =
                result.professorExplanation;
            document.getElementById('professorTip').textContent =
                result.professorTip ? `💡 ${result.professorTip}` : '';

            // Tag de origem da explicação
            const sourceTag = document.getElementById('professorSource');
            if (sourceTag) {
                const fromCache = prefetchQueue[currentIndex]?.prefetchDone
                               && prefetchQueue[currentIndex]?.prefetchedExplanation
                               === result.professorExplanation;
                sourceTag.textContent = fromCache
                    ? '⚡ Cache pré-carregado'
                    : '🤖 Gerado por IA';
                sourceTag.className = `prof-source-tag ${fromCache ? 'from-db' : 'from-ai'}`;
            }

            profBox.style.display = 'block';
        } else {
            profBox.style.display = 'none';
        }

    }, 350);
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