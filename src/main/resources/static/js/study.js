// ── Módulo de Estudo Inverso ────────────────────────────────────────────
let questions = [];
let currentIndex = 0;
let sessionId = null;
let sessionStats = { correct: 0, wrong: 0, skipped: 0 };
let questionStartTime = null;

document.addEventListener('DOMContentLoaded', async () => {
    await loadContestsIntoSelect('contestSelect');

    // Carrega tópico por URL param se existir
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
        // Cria sessão de estudo
        sessionId = await API.post('/study/sessions', { topicId: parseInt(topicId) })
            .then(s => s.id);
        questions = await API.get(`/questions/topic/${topicId}`);

        if (!questions.length) {
            showToast('Nenhuma questão encontrada. Gere questões com IA ou importe no Admin.', 'error');
            return;
        }

        currentIndex = 0;
        sessionStats = { correct: 0, wrong: 0, skipped: 0 };

        document.getElementById('topicSelector').style.display = 'none';
        document.getElementById('questionArea').style.display = 'block';
        document.getElementById('feedbackArea').style.display = 'none';
        document.getElementById('sessionResult').style.display = 'none';

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
    document.getElementById('currentQ').textContent = currentIndex + 1;
    document.getElementById('totalQ').textContent = questions.length;
    document.getElementById('questionStatement').textContent = q.statement;
    document.getElementById('feedbackArea').style.display = 'none';

    // Detecta palavras-armadilha na questão
    const traps = q.trapKeywords || [];
    const trapAlert = document.getElementById('trapAlert');
    if (traps.length) {
        document.getElementById('trapWords').textContent = traps.join(', ');
        trapAlert.style.display = 'flex';
    } else {
        trapAlert.style.display = 'none';
    }

    const diffMap = { FACIL: 'badge-success', MEDIO: 'badge-warning', DIFICIL: 'badge-danger' };
    document.getElementById('difficultyBadge').className = `badge ${diffMap[q.difficulty] || 'badge-info'}`;
    document.getElementById('difficultyBadge').textContent = q.difficulty || '';

    questionStartTime = Date.now();
}

async function answer(userAnswer) {
    const q = questions[currentIndex];
    const timeSpent = Date.now() - questionStartTime;

    try {
        const result = await API.post(`/questions/${q.id}/answer`, {
            sessionId,
            answer: userAnswer,
            timeSpentMs: timeSpent
        });

        if (result.isCorrect) {
            sessionStats.correct++;
        } else {
            sessionStats.wrong++;
        }

        renderFeedback(result, userAnswer);
    } catch (e) {
        showToast('Erro ao registrar resposta', 'error');
    }
}

function renderFeedback(result, userAnswer) {
    document.getElementById('questionArea').style.display = 'none';
    document.getElementById('feedbackArea').style.display = 'block';

    const resultEl = document.getElementById('answerResult');
    if (result.isCorrect) {
        resultEl.innerHTML = `<div style="text-align:center;padding:20px;">
            <div style="font-size:48px;">✅</div>
            <div style="font-size:22px;font-weight:700;color:var(--success);margin-top:8px;">CORRETO!</div>
            <div style="color:var(--text-muted);margin-top:6px;">${result.explanation || ''}</div>
        </div>`;
    } else {
        resultEl.innerHTML = `<div style="text-align:center;padding:20px;">
            <div style="font-size:48px;">❌</div>
            <div style="font-size:22px;font-weight:700;color:var(--danger);margin-top:8px;">INCORRETO!</div>
            <div style="color:var(--text-muted);margin-top:6px;">
                Resposta correta: <strong>${result.correctAnswer ? 'CERTO' : 'ERRADO'}</strong>
            </div>
        </div>`;

        // ── ESTUDO INVERSO: exibe parágrafo específico da lei ──────────
        if (result.lawParagraph) {
            document.getElementById('lawParagraphBox').style.display = 'block';
            document.getElementById('lawReference').textContent = result.lawReference || 'Base Legal';
            document.getElementById('lawParagraph').textContent = result.lawParagraph;
        } else {
            document.getElementById('lawParagraphBox').style.display = 'none';
        }

        // ── Campo do Professor ─────────────────────────────────────────
        if (result.professorExplanation) {
            document.getElementById('professorBox').style.display = 'block';
            document.getElementById('professorExplanation').textContent = result.professorExplanation;
            document.getElementById('professorTip').textContent = result.professorTip
                ? `💡 ${result.professorTip}` : '';
        } else {
            document.getElementById('professorBox').style.display = 'none';
        }
    }
}

function nextQuestion() {
    currentIndex++;
    document.getElementById('feedbackArea').style.display = 'none';
    document.getElementById('questionArea').style.display = 'block';
    renderQuestion();
}

function showSessionResult() {
    document.getElementById('questionArea').style.display = 'none';
    document.getElementById('feedbackArea').style.display = 'none';
    document.getElementById('sessionResult').style.display = 'block';

    const total = sessionStats.correct + sessionStats.wrong + sessionStats.skipped;
    const acc = total > 0 ? ((sessionStats.correct / total) * 100).toFixed(1) : 0;

    document.getElementById('sessionStats').innerHTML = `
        <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px;text-align:center;">
            <div class="score-card"><span class="score-label">Corretas</span>
                <span class="score-value green">${sessionStats.correct}</span></div>
            <div class="score-card"><span class="score-label">Erradas</span>
                <span class="score-value red">${sessionStats.wrong}</span></div>
            <div class="score-card highlight"><span class="score-label">Aproveitamento</span>
                <span class="score-value">${acc}%</span></div>
        </div>`;
}

function restartStudy() {
    currentIndex = 0;
    sessionStats = { correct: 0, wrong: 0, skipped: 0 };
    document.getElementById('sessionResult').style.display = 'none';
    document.getElementById('topicSelector').style.display = 'block';
}