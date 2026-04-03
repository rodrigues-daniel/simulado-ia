// ── Módulo de Estudo Inverso ────────────────────────────────────────────
let questions = [];
let currentIndex = 0;
let sessionId = null;
let sessionStats = { correct: 0, wrong: 0, skipped: 0 };
let questionStartTime = null;

document.addEventListener('DOMContentLoaded', async () => {
    await loadContestsIntoSelect('contestSelect');

    const params = new URLSearchParams(location.search);
    if (params.get('topicId')) {
        document.getElementById('topicSelect').dataset.preload = params.get('topicId');
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
        const sel = document.getElementById('topicSelect');
        sel.innerHTML = '<option value="">Selecione o tópico</option>' +
            topics.map(t => `<option value="${t.id}">${t.name}</option>`).join('');
    } catch (e) {
        showToast('Erro ao carregar tópicos', 'error');
    }
}

async function startStudy() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) { showToast('Selecione um tópico', 'error'); return; }

    try {
        const session = await API.post('/study/sessions', { topicId: parseInt(topicId) });
        sessionId = session.id;
        questions = await API.get(`/questions/topic/${topicId}`);

        if (!questions.length) {
            showToast('Nenhuma questão encontrada. Gere questões com IA ou importe no Admin.', 'error');
            return;
        }

        currentIndex = 0;
        sessionStats = { correct: 0, wrong: 0, skipped: 0 };

        document.getElementById('topicSelector').style.display  = 'none';
        document.getElementById('questionArea').style.display   = 'block';
        document.getElementById('feedbackArea').style.display   = 'none';
        document.getElementById('sessionResult').style.display  = 'none';

        renderQuestion();
    } catch (e) {
        showToast('Erro ao iniciar estudo', 'error');
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

function renderQuestion() {
    if (currentIndex >= questions.length) {
        showSessionResult();
        return;
    }

    const q = questions[currentIndex];

    document.getElementById('currentQ').textContent    = currentIndex + 1;
    document.getElementById('totalQ').textContent      = questions.length;
    document.getElementById('questionStatement').textContent = q.statement;
    document.getElementById('feedbackArea').style.display   = 'none';
    document.getElementById('processingBox').style.display  = 'none';

    // Habilita botões
    document.querySelectorAll('.btn-certo, .btn-errado')
            .forEach(b => b.disabled = false);

    // Detecta palavras-armadilha
    const traps    = q.trapKeywords || [];
    const trapAlert = document.getElementById('trapAlert');
    if (traps.length) {
        document.getElementById('trapWords').textContent = traps.join(', ');
        trapAlert.style.display = 'flex';
    } else {
        trapAlert.style.display = 'none';
    }

    const diffMap = { FACIL: 'badge-success', MEDIO: 'badge-warning', DIFICIL: 'badge-danger' };
    const diffBadge = document.getElementById('difficultyBadge');
    diffBadge.className   = `badge ${diffMap[q.difficulty] || 'badge-info'}`;
    diffBadge.textContent = q.difficulty || '';

    questionStartTime = Date.now();
}

async function answer(userAnswer) {
    const q         = questions[currentIndex];
    const timeSpent = Date.now() - (questionStartTime || Date.now());

    if (!sessionId) {
        showToast('Sessão inválida. Reinicie o estudo.', 'error');
        return;
    }

    // ── Desabilita botões imediatamente ────────────────────────────────
    document.querySelectorAll('.btn-certo, .btn-errado')
            .forEach(b => b.disabled = true);

    // ── Mostra indicador de processamento ──────────────────────────────
    showProcessing(userAnswer);

    try {
        const result = await API.post(`/questions/${q.id}/answer`, {
            sessionId:   sessionId,
            answer:      userAnswer,
            timeSpentMs: timeSpent
        });

        if (result.isCorrect) {
            sessionStats.correct++;
        } else {
            sessionStats.wrong++;
        }

        renderFeedback(result, userAnswer);

    } catch (e) {
        console.error('[answer] erro:', e);
        hideProcessing();
        document.querySelectorAll('.btn-certo, .btn-errado')
                .forEach(b => b.disabled = false);
        showToast('Erro ao registrar resposta. Tente novamente.', 'error');
    }
}

// ── Indicador de processamento ──────────────────────────────────────────

function showProcessing(userAnswer) {
    const box = document.getElementById('processingBox');

    // Mensagem varia conforme a resposta para dar feedback imediato
    const isCorrectGuess = userAnswer;
    const emoji   = isCorrectGuess ? '✅' : '❌';
    const label   = isCorrectGuess ? 'CERTO' : 'ERRADO';

    document.getElementById('processingAnswer').textContent  = `${emoji} Você marcou: ${label}`;
    document.getElementById('processingStatus').textContent  = 'Registrando resposta...';
    document.getElementById('processingBar').style.width     = '30%';
    document.getElementById('processingSource').textContent  = '';

    box.style.display = 'block';

    // Anima a barra e atualiza o status em etapas
    setTimeout(() => {
        document.getElementById('processingStatus').textContent = 'Verificando gabarito...';
        document.getElementById('processingBar').style.width    = '55%';
    }, 300);

    setTimeout(() => {
        document.getElementById('processingStatus').textContent = isCorrectGuess
            ? 'Buscando próxima questão...'
            : '🤖 Consultando professor virtual via IA...';
        document.getElementById('processingSource').textContent = isCorrectGuess
            ? ''
            : 'O modelo llama3.2:3b está gerando a explicação. Pode levar alguns segundos.';
        document.getElementById('processingBar').style.width = '80%';
    }, 700);
}

function hideProcessing() {
    document.getElementById('processingBox').style.display = 'none';
    document.getElementById('processingBar').style.width   = '0%';
}

// ── Feedback pós-resposta ───────────────────────────────────────────────

function renderFeedback(result, userAnswer) {
    // Completa a barra e esconde o processing
    document.getElementById('processingBar').style.width = '100%';

    setTimeout(() => {
        hideProcessing();

        document.getElementById('questionArea').style.display = 'none';
        document.getElementById('feedbackArea').style.display = 'block';

        const resultEl = document.getElementById('answerResult');

        if (result.isCorrect) {
            resultEl.innerHTML = `
                <div class="answer-correct">
                    <div class="answer-big-icon">✅</div>
                    <div class="answer-title correct">CORRETO!</div>
                    <div class="answer-subtitle">${result.explanation || 'Muito bem! Continue assim.'}</div>
                </div>`;
        } else {
            resultEl.innerHTML = `
                <div class="answer-wrong">
                    <div class="answer-big-icon">❌</div>
                    <div class="answer-title wrong">INCORRETO!</div>
                    <div class="answer-subtitle">
                        Resposta correta: <strong>${result.correctAnswer ? 'CERTO' : 'ERRADO'}</strong>
                    </div>
                </div>`;
        }

        // Parágrafo da lei — Estudo Inverso
        const lawBox = document.getElementById('lawParagraphBox');
        if (!result.isCorrect && result.lawParagraph) {
            document.getElementById('lawReference').textContent = result.lawReference || 'Base Legal';
            document.getElementById('lawParagraph').textContent = result.lawParagraph;
            lawBox.style.display = 'block';
        } else {
            lawBox.style.display = 'none';
        }

        // Campo do professor
        const profBox = document.getElementById('professorBox');
        if (!result.isCorrect && result.professorExplanation) {
            document.getElementById('professorExplanation').textContent = result.professorExplanation;
            document.getElementById('professorTip').textContent = result.professorTip
                ? `💡 ${result.professorTip}` : '';

            // Indica se veio da IA ou do banco
            const sourceTag = document.getElementById('professorSource');
            if (sourceTag) {
                const fromAI = result.professorExplanation !== result.explanation;
                sourceTag.textContent  = fromAI ? '🤖 Gerado por IA' : '📖 Explicação do banco';
                sourceTag.className    = `prof-source-tag ${fromAI ? 'from-ai' : 'from-db'}`;
            }

            profBox.style.display = 'block';
        } else {
            profBox.style.display = 'none';
        }

    }, 400);
}

function nextQuestion() {
    currentIndex++;
    document.getElementById('feedbackArea').style.display  = 'none';
    document.getElementById('questionArea').style.display  = 'block';
    renderQuestion();
}

function showSessionResult() {
    document.getElementById('questionArea').style.display  = 'none';
    document.getElementById('feedbackArea').style.display  = 'none';
    document.getElementById('sessionResult').style.display = 'block';

    const total = sessionStats.correct + sessionStats.wrong + sessionStats.skipped;
    const acc   = total > 0 ? ((sessionStats.correct / total) * 100).toFixed(1) : 0;

    document.getElementById('sessionStats').innerHTML = `
        <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px;text-align:center;">
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
    document.getElementById('sessionResult').style.display  = 'none';
    document.getElementById('topicSelector').style.display  = 'block';
}