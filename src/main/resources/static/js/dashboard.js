// ── Dashboard Principal ─────────────────────────────────────────────────
let currentContestId = null;

document.addEventListener('DOMContentLoaded', async () => {
    const contests = await loadContestsIntoSelect('contestSelect');
    if (contests?.length) {
        currentContestId = contests.find(c => c.isDefault)?.id || contests[0].id;
        await loadDashboard();
    }
});

async function loadDashboard() {
    const sel = document.getElementById('contestSelect');
    currentContestId = sel.value || currentContestId;
    if (!currentContestId) return;

    try {
        const data = await API.get(`/pareto/dashboard/${currentContestId}`);
        renderMetrics(data);
        renderParetoTopics(data.priorityTopics);
        renderKeywordTraps(data.keywordTraps);
    } catch (e) {
        showToast('Erro ao carregar dashboard', 'error');
    }
}

function renderMetrics(data) {
    const perf = data.userPerformance || [];
    const totalAnswered = perf.reduce((a, p) => a + (p.totalAnswered || 0), 0);
    const totalCorrect = perf.reduce((a, p) => a + (p.totalCorrect || 0), 0);
    const mastered = perf.filter(p => p.masteryLevel === 'AVANCADO').length;

    const accuracy = totalAnswered > 0
        ? ((totalCorrect / totalAnswered) * 100).toFixed(1) + '%'
        : '--';

    document.getElementById('accuracyRate').textContent = accuracy;
    document.getElementById('pendingTopics').textContent = data.priorityTopics?.length || 0;
    document.getElementById('masteredTopics').textContent = mastered;

    const traps = data.keywordTraps || [];
    const trapErrors = traps.reduce((a, t) => a + (t.wrongCount || 0), 0);
    document.getElementById('trapErrors').textContent = trapErrors;
}

function renderParetoTopics(topics) {
    const container = document.getElementById('paretoTopics');
    if (!topics?.length) {
        container.innerHTML = '<p style="color:var(--text-muted)">Nenhum tópico prioritário encontrado. Faça uma carga de dados no Admin.</p>';
        return;
    }
    container.innerHTML = topics.map(t => {
        const rate = (t.incidenceRate * 100).toFixed(0);
        const rateClass = rate >= 80 ? '' : rate >= 70 ? 'medium' : 'low';
        return `
        <div class="topic-card" onclick="location.href='/study.html?topicId=${t.topicId}'">
            <div class="topic-name">${t.topicName || 'Tópico'}</div>
            <div class="topic-discipline">${t.discipline || ''}</div>
            <div class="topic-bar">
                <div class="topic-bar-fill" style="width:${rate}%"></div>
            </div>
            <div class="topic-rate ${rateClass}">${rate}% de incidência</div>
        </div>`;
    }).join('');
}

function renderKeywordTraps(traps) {
    const container = document.getElementById('keywordTraps');
    if (!traps?.length) {
        container.innerHTML = '<span style="color:var(--text-muted)">Nenhum dado ainda. Complete algumas questões.</span>';
        return;
    }
    container.innerHTML = traps
        .filter(t => t.wrongCount > 0)
        .sort((a, b) => b.wrongCount - a.wrongCount)
        .map(t => {
            const risk = t.wrongCount > 5 ? 'high-risk' : t.wrongCount > 2 ? 'med-risk' : 'low-risk';
            return `<div class="keyword-card ${risk}">"${t.keyword}" — ${t.wrongCount} erros</div>`;
        }).join('');
}