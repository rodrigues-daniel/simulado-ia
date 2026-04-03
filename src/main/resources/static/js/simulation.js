// ── Simulado Cebraspe ───────────────────────────────────────────────────
let simulationId = null;
let simQuestions = [];
let simAnswers = {};
let simCurrentIdx = 0;
let timerInterval = null;
let timeRemaining = 0;

document.addEventListener('DOMContentLoaded', () => {
    loadContestsIntoSelect('contestSelect');
});

async function startSimulation() {
    const contestId = document.getElementById('contestSelect').value;
    const questionCount = parseInt(document.getElementById('questionCount').value);
    const timeLimitMin = parseInt(document.getElementById('timeLimit').value);

    if (!contestId) { showToast('Selecione um concurso', 'error'); return; }

    try {
        const sim = await API.post('/simulations', {
            contestId: parseInt(contestId),
            name: `Simulado ${new Date().toLocaleDateString('pt-BR')}`,
            questionCount,
            timeLimitMin
        });
        simulationId = sim.id;
        simQuestions = await API.get(`/simulations/${sim.id}/questions`);
        simCurrentIdx = 0;
        simAnswers = {};

        document.getElementById('setupArea').style.display = 'none';
        document.getElementById('simulationArea').style.display = 'block';

        buildQuestionsNav();
        renderSimQuestion();
        startTimer(timeLimitMin * 60);
    } catch (e) {
        showToast('Erro ao criar simulado. Verifique se há questões cadastradas.', 'error');
    }
}

function buildQuestionsNav() {
    const nav = document.getElementById('questionsNav');
    nav.innerHTML = simQuestions.map((q, i) =>
        `<button class="q-nav-btn ${i === 0 ? 'current' : ''}"
                 id="nav-${i}" onclick="goToQuestion(${i})">${i + 1}</button>`
    ).join('');
    document.getElementById('simProgress').textContent = `0/${simQuestions.length}`;
}

function renderSimQuestion() {
    const q = simQuestions[simCurrentIdx];
    if (!q) return;

    document.getElementById('simCurrentQ').textContent = simCurrentIdx + 1;

    // Busca detalhes da questão pelo id
    API.get(`/questions/${q.questionId || q.id}`).then(question => {
        document.getElementById('simStatement').textContent = question.statement;
    }).catch(() => {
        document.getElementById('simStatement').textContent = 'Questão não encontrada.';
    });

    // Atualiza nav
    document.querySelectorAll('.q-nav-btn').forEach((b, i) =>
        b.classList.toggle('current', i === simCurrentIdx));

    // Restaura resposta anterior se houver
    const prev = simAnswers[simCurrentIdx];
    document.getElementById('btnCerto').style.outline = prev === true ? '3px solid var(--success)' : '';
    document.getElementById('btnErrado').style.outline = prev === false ? '3px solid var(--danger)' : '';
    document.getElementById('btnBranco').style.outline = prev === null ? '3px solid #94a3b8' : '';

    updateProgress();
}

function simAnswer(answer) {
    simAnswers[simCurrentIdx] = answer;

    const navBtn = document.getElementById(`nav-${simCurrentIdx}`);
    navBtn.className = 'q-nav-btn ' +
        (answer === true ? 'answered-certo' : answer === false ? 'answered-errado' : 'answered-branco');

    updateProgress();

    // Avança automaticamente
    if (simCurrentIdx < simQuestions.length - 1) {
        setTimeout(() => goToQuestion(simCurrentIdx + 1), 400);
    }
}

function goToQuestion(idx) {
    simCurrentIdx = idx;
    renderSimQuestion();
}

function updateProgress() {
    const answered = Object.keys(simAnswers).length;
    document.getElementById('simProgress').textContent = `${answered}/${simQuestions.length}`;
    const pct = (answered / simQuestions.length) * 100;
    document.getElementById('progressFill').style.width = `${pct}%`;
}

function startTimer(seconds) {
    timeRemaining = seconds;
    timerInterval = setInterval(() => {
        timeRemaining--;
        const m = Math.floor(timeRemaining / 60).toString().padStart(2, '0');
        const s = (timeRemaining % 60).toString().padStart(2, '0');
        const display = document.getElementById('timerDisplay');
        display.textContent = `⏱️ ${m}:${s}`;
        if (timeRemaining <= 300) display.classList.add('warning');
        if (timeRemaining <= 0) { clearInterval(timerInterval); finishSimulation(); }
    }, 1000);
}

async function finishSimulation() {
    clearInterval(timerInterval);
    if (!simulationId) return;

    // Monta mapa questionId → resposta
    const answersMap = {};
    simQuestions.forEach((q, i) => {
        if (simAnswers[i] !== undefined && simAnswers[i] !== null) {
            answersMap[q.questionId || q.id] = simAnswers[i];
        }
    });

    try {
        const result = await API.post(`/simulations/${simulationId}/finish`, answersMap);
        renderResult(result);
    } catch (e) {
        showToast('Erro ao finalizar simulado', 'error');
    }
}

function renderResult(result) {
    document.getElementById('simulationArea').style.display = 'none';
    document.getElementById('resultArea').style.display = 'block';

    document.getElementById('grossScore').textContent = result.grossScore;
    document.getElementById('netScore').textContent = result.netScore;
    document.getElementById('correctCount').textContent = result.correctCount;
    document.getElementById('wrongCount').textContent = result.wrongCount;

    // Risco de chute
    const riskEl = document.getElementById('riskLevel');
    riskEl.textContent = result.riskLevel;
    riskEl.className = `risk-level ${result.riskLevel}`;

    const riskDescs = {
        ALTO: 'Você marcou muitas questões sem certeza. O chute está anulando seu desempenho. Evite responder quando não tiver certeza.',
        MEDIO: 'Tendência moderada de chute. Preste atenção às questões onde há dúvida.',
        BAIXO: 'Boa estratégia! Você está controlando bem o risco de penalização.',
        NEUTRO: 'Muitas questões em branco. Analise os tópicos e reforce o estudo.'
    };
    document.getElementById('riskDescription').textContent = riskDescs[result.riskLevel] || '';

    // Palavras-armadilha
    if (result.keywordTrapsHit?.length) {
        document.getElementById('trapsReportBox').style.display = 'block';
        document.getElementById('trapsReport').innerHTML =
            result.keywordTrapsHit.map(k => `<span class="trap-tag">${k}</span>`).join('');
    }
}