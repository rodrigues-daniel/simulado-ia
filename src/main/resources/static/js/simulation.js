// ── Simulado Cebraspe ───────────────────────────────────────────────────
let simulationId  = null;
let simQuestions  = [];   // Question[] completo — não só IDs
let simAnswers    = {};   // índice → Boolean | null
let simCurrentIdx = 0;
let timerInterval = null;
let timeRemaining = 0;

document.addEventListener('DOMContentLoaded', () => {
    loadContestsIntoSelect('contestSelect');
});

// ── Setup ────────────────────────────────────────────────────────────────
async function startSimulation() {
    const contestId     = document.getElementById('contestSelect').value;
    const questionCount = parseInt(document.getElementById('questionCount').value);
    const timeLimitMin  = parseInt(document.getElementById('timeLimit').value);
    const source        = document.getElementById('questionSource')?.value || 'ALL';

    if (!contestId) { showToast('Selecione um concurso', 'error'); return; }

    const btn = document.querySelector('button[onclick="startSimulation()"]');
    if (btn) { btn.disabled = true; btn.textContent = '⏳ Carregando...'; }

    try {
        // 1. Busca questões conforme fonte
        const questions = await API.get(
            `/questions/for-simulation` +
            `?contestId=${contestId}` +
            `&source=${source}` +
            `&limit=${questionCount}`
        );

        if (!questions?.length) {
            showToast('Nenhuma questão disponível para os filtros selecionados.', 'error');
            return;
        }

        // 2. Cria o simulado enviando os IDs das questões escolhidas
        const sim = await API.post('/simulations', {
            contestId:     parseInt(contestId),
            name:          `Simulado ${sourceLabel(source)} — ` +
                           new Date().toLocaleDateString('pt-BR'),
            questionCount: questions.length,
            timeLimitMin,
            questionIds:   questions.map(q => q.id)  // ← envia IDs para persistir
        });

        // 3. Guarda questões COMPLETAS localmente
        simulationId  = sim.id;
        simQuestions  = questions;  // Question[] completo
        simCurrentIdx = 0;
        simAnswers    = {};

        hide('setupArea');
        document.getElementById('simulationArea').style.display = 'block';

        renderSourceBadge(source, questions.length);
        buildQuestionsNav();
        renderSimQuestion(0);
        startTimer(timeLimitMin * 60);

    } catch (e) {
        console.error('[startSimulation]', e);
        showToast('Erro ao criar simulado. Verifique o servidor.', 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = '🚀 Iniciar Simulado'; }
    }
}

function sourceLabel(source) {
    return source === 'IA' ? '🤖 IA' : source === 'MANUAL' ? '📝 Manual' : '📚 Misto';
}

// ── Navegação ────────────────────────────────────────────────────────────

function buildQuestionsNav() {
    const nav = document.getElementById('questionsNav');
    nav.innerHTML = simQuestions.map((q, i) =>
        `<button class="q-nav-btn ${i === 0 ? 'current' : ''}"
                 id="nav-${i}"
                 onclick="renderSimQuestion(${i})">${i + 1}</button>`
    ).join('');
    document.getElementById('simProgress').textContent =
        `0/${simQuestions.length}`;
}

// ── Renderiza questão — usa dados locais, SEM fetch ──────────────────────
function renderSimQuestion(idx) {
    simCurrentIdx = idx;

    // ── CORREÇÃO PRINCIPAL: usa simQuestions[idx] direto ────────────────
    const q = simQuestions[idx];
    if (!q) {
        document.getElementById('simStatement').textContent =
            'Questão não encontrada.';
        return;
    }

    document.getElementById('simCurrentQ').textContent = idx + 1;
    document.getElementById('simStatement').textContent = q.statement;

    // Tag de origem
    let originEl = document.getElementById('simOriginTag');
    if (!originEl) {
        originEl    = document.createElement('div');
        originEl.id = 'simOriginTag';
        originEl.style.cssText =
            'font-size:11px;font-weight:700;padding:3px 10px;' +
            'border-radius:20px;margin-bottom:10px;display:inline-block';
        document.getElementById('simStatement')
                .insertAdjacentElement('beforebegin', originEl);
    }

    const isIA = q.source === 'IA-GERADA';
    originEl.textContent       = isIA ? '🤖 Gerada por IA' : '📝 Base Manual';
    originEl.style.background  = isIA ? '#ede9fe' : '#dbeafe';
    originEl.style.color       = isIA ? '#5b21b6' : '#1e40af';

    // Destaca palavras-armadilha se houver
    renderTrapAlert(q.trapKeywords || []);

    // Atualiza nav
    document.querySelectorAll('.q-nav-btn')
            .forEach((b, i) => b.classList.toggle('current', i === idx));

    // Restaura resposta marcada anteriormente
    const prev = simAnswers[idx];
    document.getElementById('btnCerto').style.outline =
        prev === true  ? '3px solid var(--success)' : '';
    document.getElementById('btnErrado').style.outline =
        prev === false ? '3px solid var(--danger)'  : '';
    document.getElementById('btnBranco').style.outline =
        prev === null && prev !== undefined ? '3px solid #94a3b8' : '';

    updateProgress();
}

function renderTrapAlert(traps) {
    let alertEl = document.getElementById('simTrapAlert');

    if (!traps.length) {
        if (alertEl) alertEl.style.display = 'none';
        return;
    }

    if (!alertEl) {
        alertEl    = document.createElement('div');
        alertEl.id = 'simTrapAlert';
        alertEl.className = 'trap-alert';
        document.getElementById('simStatement')
                .insertAdjacentElement('beforebegin', alertEl);
    }

    alertEl.style.display = 'flex';
    alertEl.innerHTML =
        `<span>⚠️ Palavras-armadilha detectadas!</span>` +
        `<span class="trap-words">${traps.join(', ')}</span>`;
}

// ── Resposta ─────────────────────────────────────────────────────────────
function simAnswer(answer) {
    simAnswers[simCurrentIdx] = answer;

    const navBtn = document.getElementById(`nav-${simCurrentIdx}`);
    if (navBtn) {
        navBtn.className = 'q-nav-btn ' + (
            answer === true  ? 'answered-certo'  :
            answer === false ? 'answered-errado' : 'answered-branco'
        );
    }

    updateProgress();

    // Avança automaticamente para a próxima
    if (simCurrentIdx < simQuestions.length - 1) {
        setTimeout(() => renderSimQuestion(simCurrentIdx + 1), 350);
    }
}

function goToQuestion(idx) {
    renderSimQuestion(idx);
}

function updateProgress() {
    const answered = Object.keys(simAnswers).length;
    document.getElementById('simProgress').textContent =
        `${answered}/${simQuestions.length}`;
    const pct = (answered / simQuestions.length) * 100;
    document.getElementById('progressFill').style.width = `${pct}%`;
}

// ── Timer ────────────────────────────────────────────────────────────────
function startTimer(seconds) {
    timeRemaining = seconds;
    timerInterval = setInterval(() => {
        timeRemaining--;
        const m = Math.floor(timeRemaining / 60).toString().padStart(2, '0');
        const s = (timeRemaining % 60).toString().padStart(2, '0');
        const display = document.getElementById('timerDisplay');
        if (display) {
            display.textContent = `⏱️ ${m}:${s}`;
            if (timeRemaining <= 300) display.classList.add('warning');
        }
        if (timeRemaining <= 0) {
            clearInterval(timerInterval);
            finishSimulation();
        }
    }, 1000);
}

// ── Finalizar ────────────────────────────────────────────────────────────
async function finishSimulation() {
    clearInterval(timerInterval);
    if (!simulationId) return;

    // Monta mapa: questionId (Long) → resposta (Boolean)
    // simQuestions[i].id = ID da questão
    // simAnswers[i]      = true | false | undefined (em branco)
    const answersMap = {};
    simQuestions.forEach((q, i) => {
        const answer = simAnswers[i];
        // Só envia respostas efetivas — em branco não entra no mapa
        if (answer === true || answer === false) {
            answersMap[q.id] = answer;
        }
    });

    // Log de diagnóstico
    console.log('[finishSimulation] simulationId:', simulationId);
    console.log('[finishSimulation] total questões:', simQuestions.length);
    console.log('[finishSimulation] respostas enviadas:', Object.keys(answersMap).length);

    try {
        const result = await API.post(
            `/simulations/${simulationId}/finish`,
            answersMap
        );

        console.log('[finishSimulation] resultado:', result);
        renderResult(result);

    } catch (e) {
        console.error('[finishSimulation] erro:', e);
        showToast('Erro ao calcular resultado. Verifique o log do servidor.', 'error');
    }
}

// ── Resultado ────────────────────────────────────────────────────────────
function renderResult(result) {
    document.getElementById('simulationArea').style.display = 'none';
    document.getElementById('resultArea').style.display     = 'block';

    document.getElementById('grossScore').textContent  = result.grossScore;
    document.getElementById('netScore').textContent    = result.netScore;
    document.getElementById('correctCount').textContent = result.correctCount;
    document.getElementById('wrongCount').textContent   = result.wrongCount;

    // Risco de chute
    const riskEl = document.getElementById('riskLevel');
    riskEl.textContent = result.riskLevel;
    riskEl.className   = `risk-level ${result.riskLevel}`;

    const riskDescs = {
        ALTO:   'Você marcou muitas questões sem certeza. ' +
                'O chute está anulando seu desempenho. ' +
                'Deixe em branco quando não tiver certeza.',
        MEDIO:  'Tendência moderada de chute. ' +
                'Preste atenção às questões onde há dúvida.',
        BAIXO:  'Boa estratégia! Você está controlando bem o risco.',
        NEUTRO: 'Muitas questões em branco. ' +
                'Analise os tópicos e reforce o estudo.'
    };
    document.getElementById('riskDescription').textContent =
        riskDescs[result.riskLevel] || '';

    // Palavras-armadilha
    const trapsBox = document.getElementById('trapsReportBox');
    if (result.keywordTrapsHit?.length) {
        trapsBox.style.display = 'block';
        document.getElementById('trapsReport').innerHTML =
            result.keywordTrapsHit
                .map(k => `<span class="trap-tag">${k}</span>`)
                .join('');
    } else {
        trapsBox.style.display = 'none';
    }

    // Estatísticas da fonte
    renderSourceStats();
}

function renderSourceStats() {
    const iaCount     = simQuestions.filter(q => q.source === 'IA-GERADA').length;
    const manualCount = simQuestions.length - iaCount;

    if (!iaCount && !manualCount) return;

    const statsEl = document.getElementById('resultArea');
    const existing = document.getElementById('sourceStatsBox');
    if (existing) existing.remove();

    const box = document.createElement('div');
    box.id    = 'sourceStatsBox';
    box.style.cssText =
        'margin-top:14px;padding:14px;background:var(--bg);' +
        'border-radius:10px;border:1px solid var(--border);font-size:13px';
    box.innerHTML = `
        <div style="font-weight:700;margin-bottom:8px">📊 Composição do Simulado</div>
        <div style="display:flex;gap:12px;flex-wrap:wrap">
            ${iaCount ? `<span style="background:#ede9fe;color:#5b21b6;
                padding:4px 12px;border-radius:20px;font-weight:600">
                🤖 ${iaCount} questões IA</span>` : ''}
            ${manualCount ? `<span style="background:#dbeafe;color:#1e40af;
                padding:4px 12px;border-radius:20px;font-weight:600">
                📝 ${manualCount} questões manuais</span>` : ''}
        </div>`;
    statsEl.appendChild(box);
}

// ── Badge de fonte ───────────────────────────────────────────────────────
function renderSourceBadge(source, count) {
    const existing = document.getElementById('simSourceBadge');
    if (existing) existing.remove();

    const badge = document.createElement('div');
    badge.id    = 'simSourceBadge';
    badge.style.cssText =
        'display:inline-flex;align-items:center;gap:6px;' +
        'padding:5px 12px;border-radius:20px;font-size:12px;font-weight:700;' +
        `background:${source==='IA'?'#ede9fe':source==='MANUAL'?'#dbeafe':'#f1f5f9'};` +
        `color:${source==='IA'?'#5b21b6':source==='MANUAL'?'#1e40af':'#475569'}`;

    badge.textContent = source === 'IA'
        ? `🤖 ${count} questões IA`
        : source === 'MANUAL'
            ? `📝 ${count} questões manuais`
            : `📚 ${count} questões (misto)`;

    document.querySelector('.sim-progress')?.appendChild(badge);
}

// ── Utilitários ──────────────────────────────────────────────────────────
function hide(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
}