// ── Pareto 80/20 ────────────────────────────────────────────────────────
let currentContestId = null;
let hiddenVisible = false;

document.addEventListener('DOMContentLoaded', async () => {
    const contests = await loadContestsIntoSelect('contestSelect');
    if (contests?.length) {
        const def = contests.find(c => c.isDefault) || contests[0];
        document.getElementById('contestSelect').value = def.id;
        currentContestId = def.id;
        await loadPareto();
    }
});

async function loadPareto() {
    const contestId = document.getElementById('contestSelect').value;
    if (!contestId) return;
    currentContestId = contestId;

    try {
        const data = await API.get(`/pareto/dashboard/${contestId}`);

        const priority = data.priorityTopics || [];
        const performance = data.userPerformance || [];

        // Métricas
        const avgRate = priority.length
            ? (priority.reduce((s, t) => s + parseFloat(t.incidenceRate), 0) / priority.length * 100).toFixed(0)
            : 0;

        document.getElementById('metricTotal').textContent = priority.length;
        document.getElementById('metricAvgRate').textContent = `${avgRate}%`;
        document.getElementById('paretoMetrics').style.display = 'grid';

        // Todos os tópicos do concurso para calcular ocultos
        let allTopics = [];
        try {
            allTopics = await API.get(`/admin/topics/${contestId}`);
        } catch (_) {}

        const hiddenTopics = allTopics.filter(t => t.isHidden);
        document.getElementById('metricHidden').textContent = hiddenTopics.length;

        // Renderiza prioridades
        renderPriorityList(priority);

        // Renderiza ocultos
        renderHiddenList(hiddenTopics);

        // Renderiza performance
        renderPerformance(performance);

        // Mostra seções
        document.getElementById('prioritySection').style.display = 'block';
        document.getElementById('hiddenSection').style.display = 'block';
        document.getElementById('performanceSection').style.display = performance.length ? 'block' : 'none';

    } catch (e) {
        showToast('Erro ao carregar análise Pareto', 'error');
        console.error(e);
    }
}

function renderPriorityList(topics) {
    const container = document.getElementById('priorityList');
    document.getElementById('priorityCount').textContent = topics.length;

    if (!topics.length) {
        container.innerHTML = `<p style="color:var(--text-muted)">
            Nenhum tópico prioritário encontrado. Verifique se os tópicos têm
            <code>incidenceRate</code> ≥ 0.70 no banco de dados.
        </p>`;
        return;
    }

    container.innerHTML = topics.map(t => {
        const rate = (parseFloat(t.incidenceRate) * 100).toFixed(0);
        const cls = rate >= 80 ? 'high' : 'med';
        const fillCls = rate >= 80 ? 'fill-high' : 'fill-med';
        const rateCls = rate >= 80 ? 'rate-high' : 'rate-med';

        return `
        <div class="pareto-item ${cls}">
            <div>
                <div class="pareto-item-name">${t.topicName || t.name || 'Tópico'}</div>
                <div class="pareto-item-discipline">${t.discipline || ''}</div>
                <div class="pareto-bar-row">
                    <div class="pareto-bar">
                        <div class="pareto-bar-fill ${fillCls}" style="width:${rate}%"></div>
                    </div>
                    <span class="pareto-rate ${rateCls}">${rate}%</span>
                </div>
            </div>
            <div class="pareto-item-actions">
                <a href="/study.html?topicId=${t.topicId || t.id}" class="btn btn-primary btn-sm">
                    📚 Estudar
                </a>
                <button class="btn btn-secondary btn-sm"
                        onclick="toggleHidden(${t.topicId || t.id}, true)">
                    👁️ Ocultar
                </button>
            </div>
        </div>`;
    }).join('');
}

function renderHiddenList(topics) {
    const container = document.getElementById('hiddenList');

    if (!topics.length) {
        container.innerHTML = `<p style="color:var(--text-muted)">
            Nenhum tópico oculto. Tópicos com incidência abaixo de 70% serão ocultados automaticamente.
        </p>`;
        return;
    }

    container.innerHTML = topics.map(t => {
        const rate = (parseFloat(t.incidenceRate || 0) * 100).toFixed(0);
        return `
        <div class="pareto-item hidden-item">
            <div>
                <div class="pareto-item-name">${t.name}</div>
                <div class="pareto-item-discipline">${t.discipline || ''}</div>
                <div class="pareto-bar-row">
                    <div class="pareto-bar">
                        <div class="pareto-bar-fill fill-low" style="width:${rate}%"></div>
                    </div>
                    <span class="pareto-rate rate-low">${rate}%</span>
                </div>
            </div>
            <div class="pareto-item-actions">
                <button class="btn btn-secondary btn-sm"
                        onclick="toggleHidden(${t.id}, false)">
                    ✅ Reativar
                </button>
            </div>
        </div>`;
    }).join('');
}

function renderPerformance(performance) {
    const container = document.getElementById('performanceList');
    if (!performance.length) return;

    container.innerHTML = performance.map(p => {
        const acc = p.accuracyRate != null
            ? (parseFloat(p.accuracyRate) * 100).toFixed(1) + '%'
            : '--';
        return `
        <div class="perf-item">
            <div class="perf-item-name">Tópico #${p.topicId}</div>
            <span class="mastery-badge mastery-${p.masteryLevel}">${p.masteryLevel}</span>
            <span class="perf-accuracy">${acc}</span>
            <span class="perf-answered">${p.totalAnswered || 0} questões</span>
        </div>`;
    }).join('');
}

async function toggleHidden(topicId, hide) {
    try {
        await API.patch(`/pareto/topics/${topicId}/toggle-hidden`, { hidden: hide });
        showToast(hide ? 'Tópico ocultado do cronograma' : 'Tópico reativado!', 'success');
        await loadPareto();
    } catch (e) {
        showToast('Erro ao atualizar tópico', 'error');
    }
}

async function recalculate() {
    if (!currentContestId) { showToast('Selecione um concurso', 'error'); return; }
    try {
        await API.post(`/pareto/recalculate/${currentContestId}`, {});
        showToast('Prioridades recalculadas!', 'success');
        await loadPareto();
    } catch (e) {
        showToast('Erro ao recalcular', 'error');
    }
}

function toggleHiddenView() {
    hiddenVisible = !hiddenVisible;
    document.getElementById('hiddenList').style.display = hiddenVisible ? 'flex' : 'none';
}