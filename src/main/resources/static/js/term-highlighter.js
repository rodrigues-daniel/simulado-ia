// ══════════════════════════════════════════════════════════════════════
// ── DESTAQUE DE TERMOS TÉCNICOS COM TOOLTIP ───────────────────────────
// ══════════════════════════════════════════════════════════════════════

const LEGAL_TERMS = {
    // ── Princípios ─────────────────────────────────────────────────────
    'legalidade': {
        def: 'Princípio que obriga a Administração a agir somente conforme a lei.',
        ref: 'CF/88, Art. 37',
        cat: 'principio',
    },
    'impessoalidade': {
        def: 'Veda tratamento diferenciado por razões pessoais. A atuação é do órgão, não do agente.',
        ref: 'CF/88, Art. 37',
        cat: 'principio',
    },
    'moralidade': {
        def: 'Exige conduta ética, honesta e de boa-fé da Administração.',
        ref: 'CF/88, Art. 37',
        cat: 'principio',
    },
    'publicidade': {
        def: 'Atos administrativos devem ser divulgados para conhecimento geral e controle.',
        ref: 'CF/88, Art. 37',
        cat: 'principio',
    },
    'eficiência': {
        def: 'Administração deve atingir resultados com menor custo e maior qualidade.',
        ref: 'CF/88, Art. 37 (EC 19/98)',
        cat: 'principio',
    },
    'LIMPE': {
        def: 'Mnemônico: Legalidade, Impessoalidade, Moralidade, Publicidade, Eficiência.',
        ref: 'CF/88, Art. 37',
        cat: 'principio',
    },

    // ── Atos administrativos ────────────────────────────────────────────
    'ato administrativo': {
        def: 'Manifestação unilateral de vontade da Administração que produz efeitos jurídicos.',
        ref: 'Doutrina — Di Pietro, Hely Meirelles',
        cat: 'ato',
    },
    'discricionariedade': {
        def: 'Margem de liberdade do agente para escolher, dentro da lei, a decisão mais conveniente.',
        ref: 'Lei 9.784/99',
        cat: 'ato',
    },
    'vinculação': {
        def: 'Ato em que a lei define todos os elementos, sem margem de escolha para o agente.',
        ref: 'Doutrina',
        cat: 'ato',
    },
    'motivação': {
        def: 'Obrigação de declarar os fundamentos de fato e de direito do ato administrativo.',
        ref: 'Lei 9.784/99, Art. 50',
        cat: 'ato',
    },
    'proporcionalidade': {
        def: 'A medida adotada deve ser adequada, necessária e proporcional ao fim almejado.',
        ref: 'Lei 9.784/99, Art. 2º',
        cat: 'principio',
    },
    'razoabilidade': {
        def: 'Exige que as decisões administrativas sejam lógicas e coerentes com os fatos.',
        ref: 'Lei 9.784/99, Art. 2º',
        cat: 'principio',
    },

    // ── Servidores públicos ─────────────────────────────────────────────
    'estabilidade': {
        def: 'Garantia ao servidor efetivo após 3 anos de efetivo exercício e avaliação de desempenho.',
        ref: 'CF/88, Art. 41',
        cat: 'servidor',
    },
    'vitaliciedade': {
        def: 'Garantia de magistrados e membros do MP. Perda do cargo só por sentença judicial transitada.',
        ref: 'CF/88, Art. 95',
        cat: 'servidor',
    },
    'cargo em comissão': {
        def: 'Cargo de livre nomeação e exoneração, para funções de direção, chefia e assessoramento.',
        ref: 'CF/88, Art. 37, V',
        cat: 'servidor',
    },
    'função de confiança': {
        def: 'Exercida exclusivamente por servidores de carreira efetiva; difere do cargo em comissão.',
        ref: 'CF/88, Art. 37, V',
        cat: 'servidor',
    },

    // ── Controle externo ────────────────────────────────────────────────
    'TCU': {
        def: 'Tribunal de Contas da União — órgão de controle externo do Congresso Nacional.',
        ref: 'CF/88, Art. 71',
        cat: 'controle',
    },
    'controle interno': {
        def: 'Sistema de controle exercido pelos próprios Poderes sobre seus atos.',
        ref: 'CF/88, Art. 74',
        cat: 'controle',
    },
    'controle externo': {
        def: 'Fiscalização exercida pelo Congresso Nacional com auxílio do TCU.',
        ref: 'CF/88, Art. 70–75',
        cat: 'controle',
    },
    'tomada de contas especial': {
        def: 'Processo instaurado para apurar responsabilidade por dano ao erário.',
        ref: 'Lei 8.443/92',
        cat: 'controle',
    },

    // ── Palavras-armadilha ──────────────────────────────────────────────
    'sempre': {
        def: '⚠️ ARMADILHA: Termo absoluto. Raramente correto em questões Cebraspe.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
    'nunca': {
        def: '⚠️ ARMADILHA: Negação absoluta. Questões com "nunca" costumam ser ERRADAS.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
    'exclusivamente': {
        def: '⚠️ ARMADILHA: Restrição total. Verifique se há exceções na lei.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
    'somente': {
        def: '⚠️ ARMADILHA: Limitador que costuma ter exceções não mencionadas.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
    'apenas': {
        def: '⚠️ ARMADILHA: Similar a "somente". Verifique se há outros casos.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
    'obrigatoriamente': {
        def: '⚠️ ARMADILHA: Pode haver exceções expressas na legislação.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
    'automaticamente': {
        def: '⚠️ ARMADILHA: Efeito imediato que pode exigir ato formal intermediário.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
    'qualquer': {
        def: '⚠️ ARMADILHA: Generalização ampla que costuma ter restrições legais.',
        ref: 'Padrão Cebraspe',
        cat: 'armadilha',
    },
};

// Categorias com cores
const TERM_CATEGORY_STYLE = {
    principio: { bg: '#dbeafe', color: '#1e40af', border: '#93c5fd' },
    ato: { bg: '#dcfce7', color: '#166534', border: '#86efac' },
    servidor: { bg: '#ede9fe', color: '#5b21b6', border: '#c4b5fd' },
    controle: { bg: '#fef9c3', color: '#854d0e', border: '#fde047' },
    armadilha: { bg: '#fee2e2', color: '#991b1b', border: '#fca5a5' },
};

// ── Tooltip singleton ─────────────────────────────────────────────────
let _tooltip = null;

function getTooltip() {
    if (!_tooltip) {
        _tooltip = document.createElement('div');
        _tooltip.id = 'termTooltip';
        _tooltip.className = 'term-tooltip';
        _tooltip.style.display = 'none';
        document.body.appendChild(_tooltip);

        // Fecha ao clicar fora
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.term-highlight') &&
                !e.target.closest('#termTooltip')) {
                hideTermTooltip();
            }
        });
    }
    return _tooltip;
}

function showTermTooltip(el, termKey) {
    const termData = LEGAL_TERMS[termKey.toLowerCase()] ||
        LEGAL_TERMS[termKey];
    if (!termData) return;

    const style = TERM_CATEGORY_STYLE[termData.cat] || TERM_CATEGORY_STYLE.ato;
    const tooltip = getTooltip();

    tooltip.innerHTML = `
        <div class="tooltip-header" style="background:${style.bg};
             border-bottom:1px solid ${style.border}">
            <span class="tooltip-term">${escapeHtml(termKey)}</span>
            <span class="tooltip-cat"
                  style="color:${style.color}">${termData.cat}</span>
        </div>
        <div class="tooltip-body">
            <p class="tooltip-def">${escapeHtml(termData.def)}</p>
            <div class="tooltip-ref">📖 ${escapeHtml(termData.ref)}</div>
        </div>
        <div class="tooltip-actions">
            <button class="tooltip-btn-copy"
                    onclick="copyTerm('${escapeHtml(termKey)}',
                                      '${escapeHtml(termData.def)}')">
                📋 Copiar definição
            </button>
            <button class="tooltip-btn-close"
                    onclick="hideTermTooltip()">✕</button>
        </div>`;

    // Posiciona próximo ao elemento
    const rect = el.getBoundingClientRect();
    const scrollY = window.scrollY;
    const scrollX = window.scrollX;

    tooltip.style.display = 'block';

    const tw = tooltip.offsetWidth || 300;
    const th = tooltip.offsetHeight || 150;

    let top = rect.bottom + scrollY + 6;
    let left = rect.left + scrollX;

    // Evita sair pela direita
    if (left + tw > window.innerWidth - 20) {
        left = window.innerWidth - tw - 20;
    }
    // Evita sair pela parte inferior
    if (top + th > scrollY + window.innerHeight - 20) {
        top = rect.top + scrollY - th - 6;
    }

    tooltip.style.top = `${top}px`;
    tooltip.style.left = `${left}px`;
}

function hideTermTooltip() {
    const t = document.getElementById('termTooltip');
    if (t) t.style.display = 'none';
}

function copyTerm(term, definition) {
    const text = `${term}: ${definition}`;
    navigator.clipboard.writeText(text)
        .then(() => showToast('Definição copiada!', 'success'))
        .catch(() => showToast('Erro ao copiar', 'error'));
    hideTermTooltip();
}

// ── Highlight de termos em um elemento DOM ────────────────────────────
function highlightTerms(element) {
    if (!element) return;

    const html = element.innerHTML;
    const already = element.dataset.highlighted;
    if (already === '1') return;
    element.dataset.highlighted = '1';

    // Ordena termos do mais longo para o mais curto (evita sobreposição)
    const terms = Object.keys(LEGAL_TERMS).sort(
        (a, b) => b.length - a.length
    );

    let text = element.textContent || '';

    // Usa regex para substituir — opera sobre textContent e reconstrói
    // Abordagem segura: trabalha com o texto puro, depois aplica no DOM
    const nodes = Array.from(element.childNodes);
    element.innerHTML = '';

    nodes.forEach(node => {
        if (node.nodeType === Node.TEXT_NODE) {
            const frag = highlightTextNode(node.textContent, terms);
            element.appendChild(frag);
        } else {
            element.appendChild(node.cloneNode(true));
        }
    });
}

function highlightTextNode(text, terms) {
    const frag = document.createDocumentFragment();

    // Constrói regex que casa qualquer dos termos (case-insensitive, word boundary)
    const escaped = terms.map(t =>
        t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    );
    const pattern = new RegExp(`\\b(${escaped.join('|')})\\b`, 'gi');

    let lastIndex = 0;
    let match;

    while ((match = pattern.exec(text)) !== null) {
        // Texto antes do match
        if (match.index > lastIndex) {
            frag.appendChild(
                document.createTextNode(text.slice(lastIndex, match.index))
            );
        }

        // Termo encontrado
        const matchedTerm = match[0];
        const termKey = Object.keys(LEGAL_TERMS).find(
            k => k.toLowerCase() === matchedTerm.toLowerCase()
        ) || matchedTerm;
        const termData = LEGAL_TERMS[termKey];
        const style = TERM_CATEGORY_STYLE[termData?.cat] ||
            TERM_CATEGORY_STYLE.ato;

        const span = document.createElement('span');
        span.className = 'term-highlight';
        span.dataset.term = termKey;
        span.textContent = matchedTerm;
        span.style.cssText = `
            background: ${style.bg};
            color: ${style.color};
            border-bottom: 2px solid ${style.border};
            border-radius: 3px;
            padding: 1px 3px;
            cursor: pointer;
            font-weight: 600;
        `;

        // Hover: mostra tooltip
        span.addEventListener('mouseenter', () => showTermTooltip(span, termKey));
        // Click: fixa tooltip (mobile)
        span.addEventListener('click', (e) => {
            e.stopPropagation();
            showTermTooltip(span, termKey);
        });

        frag.appendChild(span);
        lastIndex = match.index + matchedTerm.length;
    }

    // Texto restante
    if (lastIndex < text.length) {
        frag.appendChild(document.createTextNode(text.slice(lastIndex)));
    }

    return frag;
}

// ── Aplica highlight automaticamente nos elementos da questão ─────────
function applyTermHighlights() {
    // Enunciado da questão
    const stmt = document.getElementById('questionStatement');
    if (stmt) highlightTerms(stmt);

    // Explicação / resposta
    const exp = document.getElementById('professorExplanation');
    if (exp) highlightTerms(exp);

    // Parágrafo da lei
    const law = document.getElementById('lawParagraph');
    if (law) highlightTerms(law);
}