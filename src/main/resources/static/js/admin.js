// ── Admin Panel ─────────────────────────────────────────────────────────
const EXAMPLES = {
    contests: JSON.stringify([{
        name: "TCU - Técnico Federal de Controle Externo",
        organ: "Tribunal de Contas da União",
        role: "Técnico Administrativo",
        year: 2025,
        level: "MEDIO",
        isDefault: true
    }], null, 2),

    topics: JSON.stringify([{
        contestId: 1,
        name: "Princípios da Administração Pública (LIMPE)",
        discipline: "Direito Administrativo",
        lawReference: "CF/1988, Art. 37",
        incidenceRate: 0.92
    }, {
        contestId: 1,
        name: "Atos Administrativos",
        discipline: "Direito Administrativo",
        lawReference: "Lei 9.784/1999",
        incidenceRate: 0.78
    }], null, 2),

    questions: JSON.stringify([{
        topicId: 1,
        contestId: 1,
        statement: "De acordo com o art. 37 da CF/88, a administração pública obedecerá exclusivamente ao princípio da legalidade, sendo os demais princípios derivados deste.",
        correctAnswer: false,
        lawParagraph: "Art. 37. A administração pública direta e indireta de qualquer dos Poderes da União, dos Estados, do Distrito Federal e dos Municípios obedecerá aos princípios de legalidade, impessoalidade, moralidade, publicidade e eficiência.",
        lawReference: "CF/1988, Art. 37, caput",
        explanation: "ERRADO. O artigo 37 elenca cinco princípios expressos (LIMPE), não apenas o da legalidade.",
        professorTip: "A palavra 'exclusivamente' é a armadilha clássica. O LIMPE tem 5 princípios autônomos.",
        trapKeywords: ["exclusivamente"],
        year: 2023,
        source: "TCU 2023 - Adaptada",
        difficulty: "FACIL"
    }], null, 2)
};

// ── CORREÇÃO: sem chamada automática que quebrava a página ──────────────
document.addEventListener('DOMContentLoaded', () => {
    // Não chama loadRagDocuments() aqui pois a aba RAG está oculta
    // e uma falha de rede quebrava todo o DOMContentLoaded
    switchTabById('contests');
});

// ── Tabs ────────────────────────────────────────────────────────────────
function switchTab(tabName) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');

    // Marca a tab clicada como ativa
    const clickedTab = event?.target;
    if (clickedTab) clickedTab.classList.add('active');

    const section = document.getElementById(`tab-${tabName}`);
    if (section) section.style.display = 'block';

    // Carrega documentos RAG apenas quando a aba for aberta
    if (tabName === 'rag') {
        loadRagDocuments();
    }
}

function switchTabById(tabName) {
    document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');
    const section = document.getElementById(`tab-${tabName}`);
    if (section) section.style.display = 'block';

    // Marca primeira tab como ativa visualmente
    const firstTab = document.querySelector('.tab');
    if (firstTab) {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        firstTab.classList.add('active');
    }
}

// ── Exemplos ─────────────────────────────────────────────────────────────
function loadExample(type) {
    const el = document.getElementById(`${type}Payload`);
    if (el) el.value = EXAMPLES[type] || '';
}

// ── Bulk Loads ───────────────────────────────────────────────────────────
async function loadContests() {
    await bulkLoad('contests', '/admin/contests/bulk', 'contestsResult');
}

async function loadTopics() {
    await bulkLoad('topics', '/admin/topics/bulk', 'topicsResult');
}

async function loadQuestions() {
    await bulkLoad('questions', '/admin/questions/bulk', 'questionsResult');
}

async function bulkLoad(payloadId, endpoint, resultId) {
    const textarea = document.getElementById(`${payloadId}Payload`);
    const resultEl = document.getElementById(resultId);

    if (!textarea?.value?.trim()) {
        resultEl.className = 'result-box error';
        resultEl.textContent = '❌ Cole o JSON antes de importar.';
        return;
    }

    try {
        const payload = JSON.parse(textarea.value);
        resultEl.className = 'result-box';
        resultEl.textContent = '⏳ Importando...';

        const result = await API.post(endpoint, payload);

        resultEl.className = 'result-box success';
        resultEl.textContent = `✅ ${result.length} registro(s) importado(s) com sucesso!`;
        showToast(`${result.length} registros importados!`, 'success');
    } catch (e) {
        resultEl.className = 'result-box error';
        if (e.message.includes('JSON')) {
            resultEl.textContent = `❌ JSON inválido. Verifique a sintaxe.`;
        } else {
            resultEl.textContent = `❌ Erro: ${e.message}`;
        }
        showToast('Erro na importação', 'error');
    }
}

// ── RAG ──────────────────────────────────────────────────────────────────
async function uploadPdf() {
    const fileInput = document.getElementById('pdfFile');
    const topicId = document.getElementById('ragTopicId').value;
    const contestId = document.getElementById('ragContestId').value;
    const resultEl = document.getElementById('ragResult');

    if (!fileInput?.files[0]) {
        showToast('Selecione um arquivo PDF', 'error');
        return;
    }

    resultEl.className = 'result-box';
    resultEl.textContent = '⏳ Enviando e processando PDF... pode levar alguns segundos.';

    try {
        const params = {};
        if (topicId) params.topicId = topicId;
        if (contestId) params.contestId = contestId;

        const result = await API.uploadFile('/rag/upload', fileInput.files[0], params);

        resultEl.className = 'result-box success';
        resultEl.textContent = `✅ "${result.name}" ingerido com sucesso! ${result.totalChunks} chunks criados.`;
        loadRagDocuments();
    } catch (e) {
        resultEl.className = 'result-box error';
        resultEl.textContent = `❌ Erro: ${e.message}`;
    }
}

async function ingestText() {
    const textarea = document.getElementById('ragTextPayload');
    const resultEl = document.getElementById('ragResult');

    if (!textarea?.value?.trim()) {
        showToast('Cole o JSON antes de ingerir', 'error');
        return;
    }

    try {
        const payload = JSON.parse(textarea.value);
        resultEl.className = 'result-box';
        resultEl.textContent = '⏳ Ingerindo texto...';

        const result = await API.post('/rag/ingest-text', payload);

        resultEl.className = 'result-box success';
        resultEl.textContent = `✅ "${result.name}" ingerido! ${result.totalChunks} chunks criados.`;
        loadRagDocuments();
    } catch (e) {
        resultEl.className = 'result-box error';
        resultEl.textContent = `❌ Erro: ${e.message}`;
    }
}

async function loadRagDocuments() {
    const container = document.getElementById('ragDocumentsList');
    if (!container) return; // aba pode estar oculta

    try {
        const docs = await API.get('/rag/documents');

        if (!docs?.length) {
            container.innerHTML = '<p style="color:var(--text-muted)">Nenhum documento ingerido ainda.</p>';
            return;
        }

        container.innerHTML = `
        <table style="width:100%;border-collapse:collapse;font-size:13px;margin-top:8px">
            <thead>
                <tr style="background:var(--bg)">
                    <th style="padding:10px 8px;text-align:left;border-bottom:2px solid var(--border)">Nome</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Tipo</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Chunks</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Status</th>
                    <th style="padding:10px 8px;text-align:center;border-bottom:2px solid var(--border)">Data</th>
                </tr>
            </thead>
            <tbody>
                ${docs.map(d => `
                <tr style="border-bottom:1px solid var(--border)">
                    <td style="padding:10px 8px;font-weight:500">${d.name}</td>
                    <td style="padding:10px 8px;text-align:center">
                        <span class="badge badge-info">${d.sourceType}</span>
                    </td>
                    <td style="padding:10px 8px;text-align:center">${d.totalChunks}</td>
                    <td style="padding:10px 8px;text-align:center">
                        <span class="badge ${d.status === 'COMPLETED' ? 'badge-success' : 'badge-warning'}">
                            ${d.status}
                        </span>
                    </td>
                    <td style="padding:10px 8px;text-align:center;color:var(--text-muted)">
                        ${d.createdAt ? new Date(d.createdAt).toLocaleDateString('pt-BR') : '--'}
                    </td>
                </tr>`).join('')}
            </tbody>
        </table>`;
    } catch (e) {
        container.innerHTML = '<p style="color:var(--text-muted)">Erro ao carregar documentos.</p>';
        console.error('Erro RAG docs:', e);
    }
}