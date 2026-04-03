// ── Discursiva ──────────────────────────────────────────────────────────
let currentTopicId = null;
let currentContestId = null;
let currentSkeleton = null;

document.addEventListener('DOMContentLoaded', async () => {
    await loadContestsIntoSelect('contestSelect');
});

async function loadTopics() {
    const contestId = document.getElementById('contestSelect').value;
    currentContestId = contestId || null;
    const sel = document.getElementById('topicSelect');

    if (!contestId) {
        sel.innerHTML = '<option value="">Selecione o concurso primeiro</option>';
        return;
    }

    try {
        const topics = await API.get(`/admin/topics/${contestId}`);
        if (!topics.length) {
            sel.innerHTML = '<option value="">Nenhum tópico cadastrado</option>';
            return;
        }
        sel.innerHTML = '<option value="">Selecione o tópico</option>' +
            topics.map(t =>
                `<option value="${t.id}">${t.name} — ${t.discipline}</option>`
            ).join('');
    } catch (e) {
        showToast('Erro ao carregar tópicos', 'error');
    }
}

async function generateSkeleton() {
    const topicId = document.getElementById('topicSelect').value;
    const contestId = document.getElementById('contestSelect').value;

    if (!topicId) { showToast('Selecione um tópico', 'error'); return; }

    currentTopicId = topicId;
    currentContestId = contestId;

    document.getElementById('loadingBox').style.display = 'block';
    document.getElementById('skeletonResult').style.display = 'none';

    try {
        const skeleton = await API.post('/essays/generate', {
            topicId: parseInt(topicId),
            contestId: contestId ? parseInt(contestId) : null
        });
        currentSkeleton = skeleton;
        renderSkeleton(skeleton);
        await loadHistory();
    } catch (e) {
        showToast('Erro ao gerar esqueleto. Verifique se o Ollama está rodando.', 'error');
    } finally {
        document.getElementById('loadingBox').style.display = 'none';
    }
}

function renderSkeleton(s) {
    document.getElementById('skeletonResult').style.display = 'block';
    document.getElementById('essayTitle').textContent = s.title || 'Esqueleto de Redação';

    // Introdução
    document.getElementById('essayIntro').textContent = s.introduction || '';

    // Desenvolvimento — lista numerada
    const bodyEl = document.getElementById('essayBody');
    const points = s.bodyPoints || [];
    if (points.length) {
        bodyEl.innerHTML = points.map((p, i) => `
            <div class="body-point">
                <div class="body-point-num">${i + 1}</div>
                <div class="body-point-text">${p}</div>
            </div>`).join('');
    } else {
        bodyEl.textContent = 'Nenhum ponto gerado.';
    }

    // Conclusão
    document.getElementById('essayConclusion').textContent = s.conclusion || '';

    // Keywords
    const keywords = s.mandatoryKeywords || [];
    document.getElementById('essayKeywords').innerHTML =
        keywords.map(k => `<span class="keyword-tag">${k}</span>`).join('') ||
        '<span style="color:var(--text-muted)">Nenhuma palavra-chave</span>';

    // Dica da banca
    if (s.bancaTips) {
        document.getElementById('bancaTipBox').style.display = 'block';
        document.getElementById('bancaTip').textContent = s.bancaTips;
    } else {
        document.getElementById('bancaTipBox').style.display = 'none';
    }

    // Scroll suave até o resultado
    document.getElementById('skeletonResult').scrollIntoView({ behavior: 'smooth' });
}

async function loadHistory() {
    if (!currentTopicId) return;
    try {
        const list = await API.get(`/essays/topic/${currentTopicId}`);
        const container = document.getElementById('historyList');

        if (!list.length) {
            container.innerHTML = '<p style="color:var(--text-muted)">Nenhum esqueleto gerado ainda para este tópico.</p>';
            return;
        }

        container.innerHTML = list.map(s => `
            <div class="history-item" onclick='renderSkeleton(${JSON.stringify(s)})'>
                <div>
                    <div class="history-item-title">${s.title}</div>
                    <div class="history-item-meta">
                        ${s.generatedByAi ? '🤖 Gerado por IA' : '📝 Manual'} •
                        ${new Date(s.createdAt).toLocaleDateString('pt-BR')}
                    </div>
                </div>
                <span class="badge badge-info">${(s.mandatoryKeywords || []).length} keywords</span>
            </div>`).join('');
    } catch (e) {
        console.error('Erro ao carregar histórico:', e);
    }
}

function copySkeleton() {
    if (!currentSkeleton) return;
    const s = currentSkeleton;
    const text = `
${s.title}

INTRODUÇÃO:
${s.introduction}

DESENVOLVIMENTO:
${(s.bodyPoints || []).map((p, i) => `${i + 1}. ${p}`).join('\n')}

CONCLUSÃO:
${s.conclusion}

PALAVRAS-CHAVE OBRIGATÓRIAS: ${(s.mandatoryKeywords || []).join(', ')}

DICA DA BANCA: ${s.bancaTips || ''}
    `.trim();

    navigator.clipboard.writeText(text)
        .then(() => showToast('Esqueleto copiado!', 'success'))
        .catch(() => showToast('Erro ao copiar', 'error'));
}