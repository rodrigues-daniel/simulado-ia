// ── Módulo de Estudo Inverso ────────────────────────────────────────────
let questions = [];
let currentIndex = 0;
let sessionId = null;
let sessionStats = { correct: 0, wrong: 0, skipped: 0 };
let questionStartTime = null;
let totalAvailable = 0;   // total no banco antes de limitar

// ── Fila de pré-carregamento ────────────────────────────────────────────
let prefetchQueue = [];
const PREFETCH_AHEAD = 2;

document.addEventListener('DOMContentLoaded', async () => {
    await loadContestsIntoSelect('contestSelect');

    const params = new URLSearchParams(location.search);
    if (params.get('topicId')) {
        await loadTopics();
        document.getElementById('topicSelect').value = params.get('topicId');
        if (params.get('limit')) {
            const sel = document.getElementById('questionLimitSelect');
            if (sel) sel.value = params.get('limit');
        }
        await onTopicSelected();
        await startStudy();
    }
});

// ── Carrega tópicos do concurso selecionado ─────────────────────────────
async function loadTopics() {
    const contestId = document.getElementById('contestSelect').value;
    const sel = document.getElementById('topicSelect');

    sel.innerHTML = '<option value="">Selecione o tópico</option>';
    hideTopicInfo();
    hideSuggestionBanner();

    if (!contestId) return;

    try {
        const topics = await API.get(`/admin/topics/${contestId}`);
        if (!topics.length) {
            sel.innerHTML = '<option value="">Nenhum tópico cadastrado</option>';
            return;
        }

        // Agrupa por disciplina
        const grouped = {};
        topics.forEach(t => {
            const disc = t.discipline || 'Sem Disciplina';
            if (!grouped[disc]) grouped[disc] = [];
            grouped[disc].push(t);
        });

        // Ordena disciplinas alfabeticamente
        const sortedDiscs = Object.keys(grouped).sort();

        let html = '<option value="">Selecione o tópico</option>';

        sortedDiscs.forEach(disc => {
            // Ordenar tópicos por incidência dentro da disciplina
            const sorted = grouped[disc].sort(
                (a, b) => (b.incidenceRate || 0) - (a.incidenceRate || 0)
            );

            html += `<optgroup label="📚 ${disc}">`;
            sorted.forEach(t => {
                const rate = t.incidenceRate
                    ? ` — ${(t.incidenceRate * 100).toFixed(0)}%` : '';
                const hidden = t.isHidden ? ' [oculto]' : '';
                html +=
                    `<option value="${t.id}"
                             data-discipline="${escapeHtml(t.discipline || '')}"
                             data-rate="${t.incidenceRate || 0}"
                             ${t.isHidden ? 'style="color:var(--text-muted)"' : ''}>
                        ${escapeHtml(t.name)}${rate}${hidden}
                     </option>`;
            });
            html += '</optgroup>';
        });

        sel.innerHTML = html;

    } catch (e) {
        showToast('Erro ao carregar tópicos', 'error');
    }
}

// ── Ao selecionar tópico — mostra info + verifica questões ──────────────
async function onTopicSelected() {
    const topicId = document.getElementById('topicSelect').value;
    hideSuggestionBanner();
    hideTopicInfo();

    if (!topicId) return;

    // Mostra informações do tópico
    const sel = document.getElementById('topicSelect');
    const option = sel.options[sel.selectedIndex];
    const rate = parseFloat(option.dataset.rate || 0);
    const disc = option.dataset.discipline || '';
    showTopicInfo(option.text, disc, rate);

    // Verifica quantidade de questões disponíveis
    try {
        const check = await API.get(`/ia-admin/topics/${topicId}/check`);
        updateLimitSelector(check.total);
        if (check.suggestGenerate) {
            showSuggestionBanner(check);
        }
    } catch (_) { }
}

// ── Atualiza o seletor de quantidade com base no total disponível ───────
function updateLimitSelector(total) {
    totalAvailable = total;
    const sel = document.getElementById('questionLimitSelect');
    if (!sel) return;

    // Atualiza opção "Todas" com o total real
    sel.options[0].text = total > 0
        ? `Todas (${total} disponíveis)`
        : 'Todas disponíveis';

    // Desabilita opções maiores que o total (sem remover)
    Array.from(sel.options).forEach(opt => {
        const val = parseInt(opt.value);
        if (val > 0 && total > 0 && val > total) {
            opt.disabled = true;
            opt.text = `${val} questões (só há ${total})`;
        } else {
            opt.disabled = false;
            opt.text = val === 0
                ? (total > 0 ? `Todas (${total} disponíveis)` : 'Todas disponíveis')
                : `${val} questões`;
        }
    });

    // Se a opção atual está desabilitada, volta para "Todas"
    if (sel.options[sel.selectedIndex]?.disabled) {
        sel.value = '0';
    }
}

// ── Info do tópico selecionado ──────────────────────────────────────────
function showTopicInfo(name, discipline, rate) {
    const bar = document.getElementById('topicInfo');
    const content = document.getElementById('topicInfoContent');
    if (!bar || !content) return;

    const ratePct = (rate * 100).toFixed(0);
    const rateColor = rate >= 0.80 ? 'var(--success)'
        : rate >= 0.70 ? 'var(--warning)' : 'var(--danger)';

    content.innerHTML = `
        <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
            <div>
                <span style="font-size:12px;font-weight:700;
                             color:var(--text-muted);text-transform:uppercase">
                    Disciplina
                </span>
                <div style="font-size:13px;font-weight:600">${discipline || '—'}</div>
            </div>
            <div>
                <span style="font-size:12px;font-weight:700;
                             color:var(--text-muted);text-transform:uppercase">
                    Incidência Histórica
                </span>
                <div style="display:flex;align-items:center;gap:6px">
                    <div style="width:80px;height:6px;background:var(--border);
                                border-radius:4px;overflow:hidden">
                        <div style="width:${ratePct}%;height:100%;
                                    background:${rateColor};border-radius:4px"></div>
                    </div>
                    <span style="font-size:14px;font-weight:800;color:${rateColor}">
                        ${ratePct}%
                    </span>
                </div>
            </div>
            <div id="topicQuestionCount" style="margin-left:auto">
                <span style="font-size:12px;font-weight:700;
                             color:var(--text-muted);text-transform:uppercase">
                    Questões no banco
                </span>
                <div style="font-size:18px;font-weight:800;color:var(--primary)">
                    ${totalAvailable > 0 ? totalAvailable : '...'}
                </div>
            </div>
        </div>`;

    bar.style.display = 'block';
}

function hideTopicInfo() {
    const bar = document.getElementById('topicInfo');
    if (bar) bar.style.display = 'none';
}

// ── Início da sessão ────────────────────────────────────────────────────
async function startStudy() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) { showToast('Selecione um tópico', 'error'); return; }

    const limitSel = document.getElementById('questionLimitSelect');
    const orderSel = document.getElementById('questionOrderSelect');
    const limit = parseInt(limitSel?.value || '0');
    const order = orderSel?.value || 'random';

    const btn = document.querySelector('button[onclick="startStudy()"]');
    if (btn) { btn.disabled = true; btn.textContent = '⏳ Carregando...'; }

    try {
        const session = await API.post('/study/sessions', {
            topicId: parseInt(topicId)
        });
        sessionId = session.sessionId;
        let allQuestions = session.questions || [];

        if (!allQuestions.length) {
            showToast(
                session.message || 'Nenhuma questão disponível.',
                'error'
            );
            return;
        }

        // Aplica ordenação
        allQuestions = applyOrder(allQuestions, order);

        // Aplica limite
        if (limit > 0 && allQuestions.length > limit) {
            allQuestions = allQuestions.slice(0, limit);
        }

        questions = allQuestions;
        currentIndex = 0;
        sessionStats = { correct: 0, wrong: 0, skipped: 0 };
        prefetchQueue = questions.map(q => ({
            question: q,
            prefetchedExplanation: null,
            prefetchSource: null,
            prefetchDone: false,
            prefetchError: false
        }));

        // Exibe banner de origem se veio da IA
        if (session.message) {
            showStudySourceBanner(session.source, session.message);
        }

        // Mostra resumo da sessão antes de começar
        showSessionStart(questions.length, limit, order);

    } catch (e) {
        console.error('[startStudy]', e);
        showToast('Erro ao iniciar estudo.', 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = '🚀 Iniciar Estudo'; }
    }
}

// ── Ordenação das questões ──────────────────────────────────────────────
function applyOrder(qs, order) {
    const diffOrder = { FACIL: 1, MEDIO: 2, DIFICIL: 3 };

    switch (order) {
        case 'random':
            // Fisher-Yates shuffle
            for (let i = qs.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [qs[i], qs[j]] = [qs[j], qs[i]];
            }
            return qs;

        case 'difficulty_asc':
            return [...qs].sort((a, b) =>
                (diffOrder[a.difficulty] || 2) - (diffOrder[b.difficulty] || 2)
            );

        case 'difficulty_desc':
            return [...qs].sort((a, b) =>
                (diffOrder[b.difficulty] || 2) - (diffOrder[a.difficulty] || 2)
            );

        case 'newest':
            return [...qs].sort((a, b) =>
                new Date(b.createdAt || 0) - new Date(a.createdAt || 0)
            );

        default:
            return qs;
    }
}

// ── Resumo antes de iniciar ─────────────────────────────────────────────
function showSessionStart(count, limit, order) {
    const orderLabels = {
        random: '🎲 Aleatória',
        difficulty_asc: '📗 Fáceis primeiro',
        difficulty_desc: '📕 Difíceis primeiro',
        newest: '🆕 Mais recentes',
    };

    const limited = limit > 0 && totalAvailable > limit;

    // Atualiza cabeçalho da sessão com informações
    const header = document.getElementById('sessionInfoHeader');
    if (header) {
        header.innerHTML = `
            <div class="session-start-info">
                <span>📋 <strong>${count}</strong> questões</span>
                ${limited ? `<span>📌 de ${totalAvailable} disponíveis</span>` : ''}
                <span>${orderLabels[order] || '🎲 Aleatória'}</span>
            </div>`;
        header.style.display = 'block';
    }

    show('questionArea');
    hide('topicSelector');
    hide('feedbackArea');
    hide('sessionResult');
    hide('processingBox');

    renderQuestion();
    schedulePrefetch();
}

// ── Renderiza questão atual ─────────────────────────────────────────────
function renderQuestion() {
    if (currentIndex >= questions.length) {
        showSessionResult();
        return;
    }

    const q = questions[currentIndex];

    document.getElementById('currentQ').textContent = currentIndex + 1;
    document.getElementById('totalQ').textContent = questions.length;
    document.getElementById('questionStatement').textContent = q.statement;

    hide('feedbackArea');
    hide('processingBox');
    setAnswerButtons(true);

    // Palavras-armadilha
    const traps = q.trapKeywords || [];
    const trapAlert = document.getElementById('trapAlert');
    if (traps.length) {
        document.getElementById('trapWords').textContent = traps.join(', ');
        trapAlert.style.display = 'flex';
    } else {
        trapAlert.style.display = 'none';
    }

    // Dificuldade
    const diffMap = {
        FACIL: 'badge-success',
        MEDIO: 'badge-warning',
        DIFICIL: 'badge-danger'
    };
    const diffBadge = document.getElementById('difficultyBadge');
    if (diffBadge) {
        diffBadge.className = `badge ${diffMap[q.difficulty] || 'badge-info'}`;
        diffBadge.textContent = q.difficulty || '';
    }

    // Contador visual de progresso
    updateProgressBar();
    updatePrefetchIndicator();

    questionStartTime = Date.now();
    setTimeout(applyTermHighlights, 50);
}

// ── Barra de progresso ─────────────────────────────────────────────────
function updateProgressBar() {
    const bar = document.getElementById('studyProgressBar');
    if (!bar) return;
    const pct = questions.length > 0
        ? Math.round((currentIndex / questions.length) * 100) : 0;
    bar.style.width = `${pct}%`;

    const label = document.getElementById('studyProgressLabel');
    if (label) {
        const remaining = questions.length - currentIndex;
        label.textContent = remaining > 0
            ? `${remaining} questão(ões) restantes`
            : 'Última questão!';
    }
}

// ── Resto das funções (mantidas do código anterior) ────────────────────

async function generateAIQuestions() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) { showToast('Selecione um tópico primeiro', 'error'); return; }
    showToast('Gerando questões com IA... aguarde', 'success');
    try {
        await API.post('/questions/generate', {
            topicId: parseInt(topicId), count: 10
        });
        showToast('10 questões geradas!', 'success');
        await onTopicSelected();
    } catch (e) {
        showToast('Erro ao gerar questões', 'error');
    }
}

async function generateAndStart() {
    const topicId = document.getElementById('topicSelect').value;
    if (!topicId) return;
    hideSuggestionBanner();
    const btn = document.querySelector('button[onclick="startStudy()"]');
    if (btn) { btn.disabled = true; btn.textContent = '🤖 Gerando...'; }
    try {
        await API.post('/questions/generate', {
            topicId: parseInt(topicId), count: 10
        });
        showToast('Questões geradas! Iniciando...', 'success');
        await onTopicSelected();
        await startStudy();
    } catch (e) {
        showToast('Erro ao gerar questões.', 'error');
        if (btn) { btn.disabled = false; btn.textContent = '🚀 Iniciar Estudo'; }
    }
}

async function startStudyWithExisting() {
    hideSuggestionBanner();
    await startStudy();
}

// ── Prefetch ────────────────────────────────────────────────────────────
async function schedulePrefetch() {
    const end = Math.min(currentIndex + PREFETCH_AHEAD + 1, questions.length);
    for (let i = currentIndex; i < end; i++) {
        const item = prefetchQueue[i];
        if (!item || item.prefetchDone || item.prefetchError) continue;
        prefetchItem(i);
    }
}

async function prefetchItem(index) {
    const item = prefetchQueue[index];
    if (!item || item.prefetchDone) return;
    const q = item.question;
    try {
        if (q.explanation && q.explanation.trim().length > 10) {
            item.prefetchedExplanation = q.explanation;
            item.prefetchSource = 'database';
            item.prefetchDone = true;
            updatePrefetchIndicator();
            return;
        }
        const ollamaOk = await checkOllamaAvailable();
        if (!ollamaOk) {
            item.prefetchDone = true;
            item.prefetchError = true;
            item.prefetchSource = 'unavailable';
            return;
        }
        const result = await API.post(
            `/questions/${q.id}/prefetch-explanation`, {});
        item.prefetchedExplanation = result.explanation || null;
        item.prefetchSource = result.source || 'ai';
        item.prefetchDone = true;
        updatePrefetchIndicator();
    } catch (e) {
        item.prefetchDone = true;
        item.prefetchError = true;
        item.prefetchSource = 'error';
    }
}

async function checkOllamaAvailable() {
    try {
        const res = await fetch('/api/ai/health', { method: 'GET' });
        return res.ok;
    } catch (_) { return false; }
}

function updatePrefetchIndicator() {
    const indicator = document.getElementById('prefetchIndicator');
    if (!indicator) return;
    const next = prefetchQueue[currentIndex + 1];
    if (!next) { indicator.style.display = 'none'; return; }
    if (next.prefetchDone && !next.prefetchError) {
        indicator.style.display = 'flex';
        indicator.className = 'prefetch-indicator ready';
        indicator.innerHTML = '⚡ Próxima questão pré-carregada';
    } else if (!next.prefetchDone) {
        indicator.style.display = 'flex';
        indicator.className = 'prefetch-indicator loading';
        indicator.innerHTML =
            '<span class="mini-spinner"></span> Pré-carregando próxima...';
    } else {
        indicator.style.display = 'none';
    }
}

// ── Resposta ─────────────────────────────────────────────────────────────
async function answer(userAnswer) {
    const q = questions[currentIndex];
    const timeSpent = Date.now() - (questionStartTime || Date.now());

    if (!sessionId) {
        showToast('Sessão inválida. Reinicie o estudo.', 'error'); return;
    }

    setAnswerButtons(false);
    showProcessingBox(userAnswer);

    try {
        const result = await API.post(`/questions/${q.id}/answer`, {
            sessionId: sessionId,
            answer: userAnswer,
            timeSpentMs: timeSpent
        });

        const isCorrect = result.isCorrect === true;
        if (isCorrect) sessionStats.correct++;
        else sessionStats.wrong++;

        if (!isCorrect) {
            const prefetch = prefetchQueue[currentIndex];
            if (prefetch?.prefetchDone && !prefetch?.prefetchError
                && prefetch?.prefetchedExplanation
                && !result.professorExplanation) {
                result.professorExplanation = prefetch.prefetchedExplanation;
            }
        }

        currentIndex++;
        schedulePrefetch();
        currentIndex--;

        renderFeedback(result, userAnswer, isCorrect);

    } catch (e) {
        console.error('[answer]', e);
        hideProcessingBox();
        setAnswerButtons(true);
        showToast('Erro ao registrar resposta. Tente novamente.', 'error');
    }
}

// ── Processing Box ─────────────────────────────────────────────────────
function showProcessingBox(userAnswer) {
    const choiceLabel = userAnswer === true ? '✅ Você marcou: CERTO'
        : userAnswer === false ? '❌ Você marcou: ERRADO'
            : '— Em branco';

    document.getElementById('processingAnswer').textContent = choiceLabel;
    document.getElementById('processingStatus').textContent = 'Registrando resposta...';
    document.getElementById('processingBar').style.width = '25%';
    document.getElementById('processingSource').textContent = '';
    show('processingBox');

    const item = prefetchQueue[currentIndex];
    const source = item?.prefetchSource;
    const done = item?.prefetchDone && !item?.prefetchError;

    setTimeout(() => {
        document.getElementById('processingStatus').textContent =
            'Verificando gabarito...';
        document.getElementById('processingBar').style.width = '50%';
    }, 250);

    setTimeout(() => {
        if (done) {
            const { label, detail } = getSourceLabel(source);
            document.getElementById('processingStatus').textContent = label;
            document.getElementById('processingSource').textContent = detail;
        } else {
            document.getElementById('processingStatus').textContent =
                '🤖 Consultando professor virtual via IA...';
            document.getElementById('processingSource').textContent =
                'PGVector + llama3.2:3b gerando explicação.';
        }
        document.getElementById('processingBar').style.width = '75%';
    }, 600);
}

function hideProcessingBox() {
    hide('processingBox');
    document.getElementById('processingBar').style.width = '0%';
}

function getSourceLabel(source) {
    const map = {
        database: {
            label: '📖 Carregando explicação do banco...',
            detail: 'Recuperado do PostgreSQL.'
        },
        ai: {
            label: '🤖 Carregando explicação da IA...',
            detail: 'Gerada com llama3.2:3b + PGVector.'
        },
        fallback: {
            label: '📖 Carregando explicação de fallback...',
            detail: 'IA indisponível. Usando banco.'
        },
    };
    return map[source] || { label: '⚡ Carregando...', detail: '' };
}

// ── Feedback ───────────────────────────────────────────────────────────
function renderFeedback(result, userAnswer, isCorrect) {
    document.getElementById('processingBar').style.width = '100%';

    setTimeout(() => {
        hideProcessingBox();
        hide('questionArea');
        show('feedbackArea');

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
                        ${result.explanation || 'Muito bem!'}
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

        // Parágrafo da lei
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

        // Campo do professor
        const profBox = document.getElementById('professorBox');
        if (!isCorrect && result.professorExplanation) {
            document.getElementById('professorExplanation').textContent =
                result.professorExplanation;
            document.getElementById('professorTip').textContent =
                result.professorTip ? `💡 ${result.professorTip}` : '';

            const sourceTag = document.getElementById('professorSource');
            if (sourceTag) {
                const prefetch = prefetchQueue[currentIndex];
                const srcKey = prefetch?.prefetchSource;
                const tagConfig = {
                    database: { text: '📖 Banco de dados', cls: 'from-db' },
                    ai: { text: '🤖 IA + Banco Vetorial', cls: 'from-ai' },
                    fallback: { text: '📖 Fallback estático', cls: 'from-db' },
                };
                const cfg = tagConfig[srcKey] || { text: '🤖 IA', cls: 'from-ai' };
                sourceTag.textContent = cfg.text;
                sourceTag.className = `prof-source-tag ${cfg.cls}`;
            }
            profBox.style.display = 'block';
        } else {
            profBox.style.display = 'none';
        }

        renderActionBar(questions[currentIndex]?.id, result.isSaved);
        setTimeout(applyTermHighlights, 100);
        const q = questions[currentIndex];
        if (q) generateMemoryTip(q, isCorrect);

    }, 350);
}


// ── Gera dica de memorização via IA ─────────────────────────────────
async function generateMemoryTip(question, isCorrect) {
    const tipBox = document.getElementById('memoryTipBox');
    if (!tipBox) return;

    // Só gera dica nas respostas erradas para não poluir o fluxo correto
    if (isCorrect) { tipBox.style.display = 'none'; return; }

    tipBox.style.display = 'block';
    tipBox.innerHTML = `
        <div class="memory-tip-header">🧠 Como memorizar</div>
        <div class="memory-tip-loading">
            <span class="mini-spinner"></span>
            Gerando dica de memorização...
        </div>`;

    try {
        const prompt = buildMemoryPrompt(question);
        const r = await fetch('/api/knowledge/test-pipeline', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                pergunta: prompt,
                materia: null,
                topicoId: question.topicId,
            }),
        });

        if (!r.ok) throw new Error('Pipeline indisponível');

        const data = await r.json();
        const tips = parseMemoryTips(data.resposta || '');
        renderMemoryTips(tipBox, tips);

    } catch (e) {
        // Fallback: dicas estáticas baseadas nas trap keywords
        const tips = buildStaticMemoryTips(question);
        renderMemoryTips(tipBox, tips);
    }
}

function buildMemoryPrompt(question) {
    return `Para a seguinte questão Cebraspe que um aluno errou, gere:
1. Uma dica prática de como memorizar o conceito correto (máx 2 linhas)
2. Uma regra mnemônica ou associação visual simples
3. Um alerta sobre a pegadinha para não repetir o erro

Questão: ${question.statement}
Gabarito: ${question.correctAnswer ? 'CERTO' : 'ERRADO'}
${question.trapKeywords?.length ? `Palavras-armadilha: ${question.trapKeywords.join(', ')}` : ''}

Responda em formato simples com os 3 itens numerados.`;
}

function parseMemoryTips(text) {
    // Tenta extrair itens numerados
    const lines = text.split('\n').filter(l => l.trim());
    const tips = [];

    lines.forEach(line => {
        const match = line.match(/^[1-3][.)]\s*(.+)/);
        if (match) tips.push(match[1].trim());
    });

    // Fallback: divide em parágrafos
    if (tips.length < 2) {
        return text.split('\n\n')
            .filter(p => p.trim().length > 10)
            .slice(0, 3);
    }
    return tips;
}

function buildStaticMemoryTips(question) {
    const tips = [];
    const traps = question.trapKeywords || [];

    // Dica baseada em palavras-armadilha
    if (traps.length) {
        tips.push(
            `⚠️ Cuidado com "${traps.join(', ')}" — ` +
            `termos absolutos no Cebraspe quase sempre indicam questão ERRADA.`
        );
    }

    // Dica baseada no gabarito
    if (!question.correctAnswer) {
        tips.push(
            `🔴 Gabarito ERRADO: procure o detalhe que tornou a afirmação incorreta. ` +
            `Geralmente é uma palavra que restringiu ou generalizou demais.`
        );
    } else {
        tips.push(
            `🟢 Gabarito CERTO: fixe o fundamento legal. ` +
            `Leia o artigo de referência em voz alta 3 vezes.`
        );
    }

    // Dica geral de revisão espaçada
    tips.push(
        `📅 Revisão espaçada: reveja esta questão em 1 dia, 3 dias e 7 dias. ` +
        `Use a função "📌 Salvar questão" para não perder.`
    );

    return tips;
}

function renderMemoryTips(tipBox, tips) {
    const icons = ['💡', '🔗', '📅'];
    const labels = [
        'Dica prática',
        'Como memorizar',
        'Revisão',
    ];

    tipBox.innerHTML = `
        <div class="memory-tip-header">🧠 Como não esquecer</div>
        ${tips.map((tip, i) => `
            <div class="memory-tip-item">
                <div class="memory-tip-icon">${icons[i] || '💡'}</div>
                <div>
                    <div class="memory-tip-label">${labels[i] || 'Dica'}</div>
                    <div class="memory-tip-text">${escapeHtml(tip)}</div>
                </div>
            </div>
        `).join('')}`;
}

// ── Próxima questão ─────────────────────────────────────────────────────
function nextQuestion() {
    currentIndex++;
    hide('feedbackArea');
    show('questionArea');
    renderQuestion();
    schedulePrefetch();
}

// ── Resultado da sessão ─────────────────────────────────────────────────
function showSessionResult() {
    hide('questionArea');
    hide('feedbackArea');
    show('sessionResult');

    const total = sessionStats.correct + sessionStats.wrong + sessionStats.skipped;
    const acc = total > 0
        ? ((sessionStats.correct / total) * 100).toFixed(1) : 0;
    const netScore = Math.max(0, sessionStats.correct - sessionStats.wrong);

    document.getElementById('sessionStats').innerHTML = `
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));
                    gap:14px;text-align:center;margin-bottom:16px">
            <div class="score-card">
                <span class="score-label">Corretas</span>
                <span class="score-value green">${sessionStats.correct}</span>
            </div>
            <div class="score-card">
                <span class="score-label">Erradas</span>
                <span class="score-value red">${sessionStats.wrong}</span>
            </div>
            <div class="score-card">
                <span class="score-label">Aproveitamento</span>
                <span class="score-value">${acc}%</span>
            </div>
            <div class="score-card highlight">
                <span class="score-label">Pontuação Líquida</span>
                <span class="score-value">${netScore}</span>
            </div>
        </div>

        <!-- Barra de desempenho -->
        <div style="margin-bottom:16px">
            <div style="height:12px;background:var(--border);
                        border-radius:6px;overflow:hidden;display:flex">
                <div style="width:${acc}%;background:${parseFloat(acc) >= 70 ? 'var(--success)' :
            parseFloat(acc) >= 50 ? 'var(--warning)' : 'var(--danger)'
        };transition:width .5s;border-radius:6px"></div>
            </div>
            <div style="display:flex;justify-content:space-between;
                        font-size:11px;color:var(--text-muted);margin-top:4px">
                <span>0%</span>
                <span style="font-weight:700;color:var(--primary)">${acc}%</span>
                <span>100%</span>
            </div>
        </div>

        <!-- Avaliação qualitativa -->
        <div class="session-eval ${getEvalClass(parseFloat(acc))}">
            ${getEvalMessage(parseFloat(acc), sessionStats.correct, total)}
        </div>`;
}

function getEvalClass(acc) {
    if (acc >= 80) return 'eval-excellent';
    if (acc >= 60) return 'eval-good';
    if (acc >= 40) return 'eval-regular';
    return 'eval-poor';
}

function getEvalMessage(acc, correct, total) {
    if (acc >= 80) return `🏆 Excelente! ${correct}/${total} corretas. Domínio avançado do tópico.`;
    if (acc >= 60) return `👍 Bom desempenho. Revise as questões erradas para consolidar o aprendizado.`;
    if (acc >= 40) return `📚 Desempenho regular. Recomendamos revisar o material do tópico.`;
    return `🎯 Precisa de atenção. Estude o fundamento legal e tente novamente.`;
}

// ── Reiniciar ──────────────────────────────────────────────────────────
function restartStudy() {
    currentIndex = 0;
    sessionStats = { correct: 0, wrong: 0, skipped: 0 };
    prefetchQueue = [];
    hide('sessionResult');
    show('topicSelector');
}

// ── Banners ────────────────────────────────────────────────────────────
function showSuggestionBanner(check) {
    const banner = document.getElementById('suggestionBanner');
    if (!banner) return;
    const isZero = check.total === 0;
    banner.style.cssText = `
        display:block;margin-top:14px;padding:14px 16px;border-radius:10px;
        background:${isZero ? '#ede9fe' : '#fef9c3'};
        border:1px solid ${isZero ? '#c4b5fd' : '#fde047'};
        font-size:13px;color:${isZero ? '#5b21b6' : '#854d0e'};
    `;
    banner.innerHTML = `
        <div style="display:flex;align-items:flex-start;gap:10px">
            <span style="font-size:20px;flex-shrink:0">
                ${isZero ? '🤖' : '💡'}
            </span>
            <div style="flex:1">
                <div style="font-weight:700;margin-bottom:4px">
                    ${isZero ? 'Sem questões cadastradas' : 'Poucas questões'}
                </div>
                <div style="margin-bottom:10px">${check.recommendation}</div>
                <div style="display:flex;gap:8px;flex-wrap:wrap">
                    <button class="btn btn-sm"
                            style="background:${isZero ? '#7c3aed' : '#d97706'};color:#fff"
                            onclick="generateAndStart()">
                        🤖 Gerar com IA e iniciar
                    </button>
                    ${!isZero ? `
                    <button class="btn btn-secondary btn-sm"
                            onclick="startStudyWithExisting()">
                        📚 Usar ${check.approved} questões existentes
                    </button>` : ''}
                </div>
            </div>
        </div>`;
}

function hideSuggestionBanner() {
    const b = document.getElementById('suggestionBanner');
    if (b) b.style.display = 'none';
}

function showStudySourceBanner(source, message) {
    const existing = document.getElementById('sourceBanner');
    if (existing) existing.remove();
    const isError = source === 'AI_ERROR' || source === 'AI_EMPTY';
    const banner = document.createElement('div');
    banner.id = 'sourceBanner';
    banner.style.cssText = `
        padding:12px 18px;border-radius:8px;margin-bottom:16px;
        font-size:13px;font-weight:600;display:flex;align-items:flex-start;gap:10px;
        background:${isError ? '#fee2e2' : '#fef9c3'};
        color:${isError ? '#991b1b' : '#854d0e'};
        border:1px solid ${isError ? '#fca5a5' : '#fde047'};
    `;
    banner.innerHTML =
        `<span style="font-size:18px;flex-shrink:0">
            ${isError ? '⚠️' : '🤖'}
         </span>
         <span>${message}</span>`;
    const qa = document.getElementById('questionArea');
    if (qa) qa.parentNode.insertBefore(banner, qa);
}

// ── Action Bar (salvar + anotar) ───────────────────────────────────────
function renderActionBar(questionId, isSaved) {
    if (!questionId) return;
    let bar = document.getElementById('actionBar');
    if (!bar) {
        bar = document.createElement('div');
        bar.id = 'actionBar';
        document.getElementById('feedbackArea')?.appendChild(bar);
    }
    bar.className = 'action-bar';
    bar.innerHTML = `
        <button class="btn-action ${isSaved ? 'saved' : ''}"
                id="btnSave" onclick="toggleSave(${questionId})">
            ${isSaved ? '🔖 Salvo' : '📌 Salvar questão'}
        </button>
        <button class="btn-action" onclick="toggleNotes(${questionId})">
            📝 Anotações
        </button>`;
    loadNotes(questionId);
}

// ── Salvar questão ─────────────────────────────────────────────────────
async function toggleSave(questionId) {
    const btn = document.getElementById('btnSave');
    const isSaved = btn.classList.contains('saved');
    try {
        if (isSaved) {
            await fetch(`/api/questions/${questionId}/save`, { method: 'DELETE' });
            btn.classList.remove('saved');
            btn.textContent = '📌 Salvar questão';
            showToast('Removido dos favoritos', 'success');
        } else {
            await API.post(`/questions/${questionId}/save`, {});
            btn.classList.add('saved');
            btn.textContent = '🔖 Salvo';
            showToast('Questão salva!', 'success');
        }
    } catch (e) { showToast('Erro ao salvar', 'error'); }
}

// ── Notas ──────────────────────────────────────────────────────────────
let notesVisible = false;
let currentNotesQuestionId = null;

async function toggleNotes(questionId) {
    currentNotesQuestionId = questionId;
    const nb = document.getElementById('notesBox');
    if (nb && notesVisible && currentNotesQuestionId === questionId) {
        nb.style.display = 'none';
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
        document.getElementById('feedbackArea')?.appendChild(notesBox);
    }
    try {
        const notes = await API.get(`/questions/${questionId}/notes`);
        renderNotesBox(questionId, notes);
        notesBox.style.display = 'block';
    } catch (e) { console.error('Notas:', e); }
}

function renderNotesBox(questionId, notes) {
    const box = document.getElementById('notesBox');
    box.innerHTML = `
        <div class="notes-header">
            <span>📝 Minhas Anotações</span>
            <button class="btn-icon"
                    onclick="document.getElementById('notesBox').style.display='none'">
                ✕
            </button>
        </div>
        <div id="notesList" class="notes-list">
            ${notes.length
            ? notes.map(n => renderNoteItem(n)).join('')
            : '<p style="color:var(--text-muted);font-size:13px">Nenhuma anotação.</p>'}
        </div>
        <div class="notes-input-row">
            <textarea id="newNoteInput" class="notes-textarea" rows="3"
                      placeholder="Escreva sua anotação..."></textarea>
            <button class="btn btn-primary btn-sm"
                    onclick="addNote(${questionId})">
                💾 Salvar
            </button>
        </div>`;
}

function renderNoteItem(note) {
    const date = new Date(note.createdAt).toLocaleDateString('pt-BR');
    return `
    <div class="note-item" id="note-${note.id}">
        <div class="note-text" id="note-text-${note.id}">
            ${escapeHtml(note.note)}
        </div>
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
    const text = input?.value?.trim();
    if (!text) { showToast('Escreva algo antes de salvar', 'error'); return; }
    try {
        const note = await API.post(`/questions/${questionId}/notes`, { note: text });
        const list = document.getElementById('notesList');
        const empty = list.querySelector('p');
        if (empty) empty.remove();
        list.insertAdjacentHTML('afterbegin', renderNoteItem(note));
        input.value = '';
        showToast('Anotação salva!', 'success');
    } catch (e) { showToast('Erro ao salvar anotação', 'error'); }
}

function editNote(noteId) {
    const textEl = document.getElementById(`note-text-${noteId}`);
    const current = textEl.textContent.trim();
    textEl.innerHTML = `
        <textarea id="edit-${noteId}"
                  style="width:100%;padding:6px;border:1px solid var(--border);
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
    const text = input?.value?.trim();
    if (!text) return;
    try {
        await API.put(`/questions/notes/${noteId}`, { note: text });
        document.getElementById(`note-text-${noteId}`).innerHTML = escapeHtml(text);
        showToast('Anotação atualizada!', 'success');
    } catch (e) { showToast('Erro ao atualizar', 'error'); }
}

function cancelEditNote(noteId, original) {
    document.getElementById(`note-text-${noteId}`).textContent = original;
}

async function deleteNote(noteId) {
    if (!confirm('Excluir esta anotação?')) return;
    try {
        await fetch(`/api/questions/notes/${noteId}`, { method: 'DELETE' });
        document.getElementById(`note-${noteId}`)?.remove();
        showToast('Anotação excluída', 'success');
    } catch (e) { showToast('Erro ao excluir', 'error'); }
}

// ── Utilitários ────────────────────────────────────────────────────────
function setAnswerButtons(enabled) {
    document.querySelectorAll('.btn-certo, .btn-errado')
        .forEach(b => b.disabled = !enabled);
}