// ═══════════════════════════════════════════════════════════════
// TTS CLIENT — inclua no api.js ou num arquivo tts.js separado
// ═══════════════════════════════════════════════════════════════

const TTS = {
    BASE: 'http://localhost:8765',
    enabled: true,
    _currentAudio: null,
    _config: { voice: 'bf_alice', speed: 1.0 },

    // ── Carrega config do servidor ────────────────────────────────
    async loadConfig() {
        try {
            const r = await fetch(`${this.BASE}/config`);
            this._config = await r.json();
            this.enabled = this._config.enabled;
        } catch (_) {
            console.warn('[TTS] Serviço indisponível.');
            this.enabled = false;
        }
    },

    // ── Para áudio atual ──────────────────────────────────────────
    stop() {
        if (this._currentAudio) {
            this._currentAudio.pause();
            this._currentAudio = null;
        }
    },

    // ── Toca texto completo (com cache) ──────────────────────────
    async speak(text, { voice, speed, onStart, onEnd, onError } = {}) {
        if (!this.enabled || !text?.trim()) return;
        this.stop();

        const r = await fetch(`${this.BASE}/tts`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                text,
                voice: voice || this._config.voice,
                speed: speed || this._config.speed,
            }),
        });

        if (!r.ok) throw new Error(`TTS HTTP ${r.status}`);

        const blob = await r.blob();
        const url = URL.createObjectURL(blob);
        const audio = new Audio(url);
        this._currentAudio = audio;

        audio.onplay = onStart;
        audio.onended = () => { URL.revokeObjectURL(url); onEnd?.(); };
        audio.onerror = onError;
        await audio.play();
    },

    // ── Streaming em tempo real ───────────────────────────────────
    async stream(text, { voice, speed, onStart, onEnd, onError } = {}) {
        if (!this.enabled || !text?.trim()) return;
        this.stop();

        const r = await fetch(`${this.BASE}/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                text,
                voice: voice || this._config.voice,
                speed: speed || this._config.speed,
            }),
        });

        if (!r.ok) throw new Error(`Stream HTTP ${r.status}`);

        // Lê chunks e vai tocando conforme chegam
        const reader = r.body.getReader();
        const chunks = [];

        onStart?.();

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            chunks.push(value);

            // Toca incrementalmente a cada novo chunk
            const blob = new Blob(chunks, { type: 'audio/wav' });
            const url = URL.createObjectURL(blob);

            if (!this._currentAudio) {
                const audio = new Audio(url);
                this._currentAudio = audio;
                audio.onended = () => { onEnd?.(); };
                await audio.play().catch(() => { });
            } else {
                // Atualiza source sem interromper
                const prevTime = this._currentAudio.currentTime;
                this._currentAudio.src = url;
                this._currentAudio.currentTime = prevTime;
            }
        }
    },

    // ── Botão "Ouvir resposta" ────────────────────────────────────
    createButton(text, { stream = false, label = '🔊 Ouvir', containerId } = {}) {
        const btn = document.createElement('button');
        btn.className = 'tts-btn';
        btn.innerHTML = label;
        btn.dataset.text = text;

        const setLoading = () => {
            btn.className = 'tts-btn loading';
            btn.innerHTML = '<span class="tts-spinner"></span> Carregando...';
            btn.disabled = true;
        };
        const setPlaying = () => {
            btn.className = 'tts-btn playing';
            btn.innerHTML = stream
                ? '⏹ <span class="tts-live"><span class="tts-live-dot"></span>AO VIVO</span>'
                : '⏹ Pausar';
            btn.disabled = false;
        };
        const setIdle = () => {
            btn.className = 'tts-btn';
            btn.innerHTML = label;
            btn.disabled = false;
        };
        const setError = () => {
            btn.className = 'tts-btn error';
            btn.innerHTML = '⚠️ Erro';
            btn.disabled = false;
            setTimeout(setIdle, 2000);
        };

        let playing = false;

        btn.onclick = async () => {
            if (playing) {
                this.stop();
                playing = false;
                setIdle();
                return;
            }

            playing = true;
            setLoading();

            try {
                const method = stream ? this.stream : this.speak;
                await method.call(this, text, {
                    onStart: setPlaying,
                    onEnd: () => { playing = false; setIdle(); },
                    onError: () => { playing = false; setError(); },
                });
            } catch (e) {
                console.error('[TTS]', e);
                playing = false;
                setError();
            }
        };

        // Anexa ao container se fornecido
        if (containerId) {
            document.getElementById(containerId)?.appendChild(btn);
        }

        return btn;
    },
};

// ── Inicializa ao carregar ────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => TTS.loadConfig());


// ── Funções de integração com o simulador ─────────────────────────────────

let _currentAnswerText = '';
let _currentIAText = '';

function getAnswerText() {
    return document.getElementById('answerText')?.innerText || '';
}

async function ouvirQuestao() {
    const text = `De acordo com o art. 37 da CF/88, a administração pública
        obedecerá exclusivamente ao princípio da legalidade,
        sendo os demais princípios meramente orientadores.`;

    const text2 = document.getElementById('questionStatement').textContent

    const btn = document.getElementById('btnOuvirQuestao');
    const status = document.getElementById('statusQuestao');

    try {
        btn.className = 'tts-btn loading';
        btn.innerHTML = '<span class="tts-spinner"></span> Carregando...';
        btn.disabled = true;
        status.textContent = 'Gerando áudio...';

        await TTS.speak(text2, {
            onStart: () => {
                btn.className = 'tts-btn playing';
                btn.innerHTML = '⏹ Pausar';
                btn.disabled = false;
                status.textContent = '▶ Reproduzindo questão';
            },
            onEnd: () => {
                btn.className = 'tts-btn';
                btn.innerHTML = '🔊 Ouvir questão';
                btn.disabled = false;
                status.textContent = '';
            },
        });
    } catch (e) {
        btn.className = 'tts-btn error';
        btn.innerHTML = '⚠️ Erro TTS';
        btn.disabled = false;
        status.textContent = 'Serviço indisponível';
        setTimeout(() => {
            btn.className = 'tts-btn';
            btn.innerHTML = '🔊 Ouvir questão';
        }, 2000);
    }
}

async function ouvirResposta(useStream) {
    const text = getAnswerText();
    const btnId = useStream ? 'btnStreamResposta' : 'btnOuvirResposta';
    const btn = document.getElementById(btnId);
    const status = document.getElementById('statusResposta');
    const player = document.getElementById('ttsPlayer');
    const audio = document.getElementById('audioPlayer');

    if (!text) return;
    _currentAnswerText = text;

    const setLoading = () => {
        btn.className = 'tts-btn loading';
        btn.innerHTML = '<span class="tts-spinner"></span> '
            + (useStream ? 'Conectando stream...' : 'Gerando áudio...');
        btn.disabled = true;
    };

    const setPlaying = () => {
        btn.className = 'tts-btn playing';
        btn.innerHTML = useStream
            ? '⏹ <span class="tts-live"><span class="tts-live-dot"></span>AO VIVO</span>'
            : '⏹ Pausar';
        btn.disabled = false;
        status.textContent = useStream ? '📡 Streaming ativo' : '▶ Reproduzindo';
    };

    const setIdle = () => {
        btn.className = 'tts-btn';
        btn.innerHTML = useStream ? '📡 Streaming ao vivo' : '🔊 Ouvir resposta';
        btn.disabled = false;
        status.textContent = '';
    };

    try {
        setLoading();

        if (useStream) {
            await TTS.stream(text, {
                onStart: setPlaying,
                onEnd: setIdle,
            });
        } else {
            // Áudio completo com player inline
            const r = await fetch(`${TTS.BASE}/tts`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text }),
            });
            const blob = await r.blob();
            const url = URL.createObjectURL(blob);
            const cacheHit = r.headers.get('X-Cache') === 'HIT';
            const dur = r.headers.get('X-Duration');

            audio.src = url;
            player.style.display = 'flex';
            document.getElementById('playerInfo').textContent =
                cacheHit ? '⚡ Do cache' : `⏱ Gerado em ${dur}s`;

            setPlaying();
            audio.onended = setIdle;
            await audio.play();
        }

    } catch (e) {
        btn.className = 'tts-btn error';
        btn.innerHTML = '⚠️ TTS Indisponível';
        btn.disabled = false;
        setTimeout(setIdle, 2000);
    }
}

async function gerarComIA(streamAudio) {
    const prompt = document.getElementById('iaPrompt')?.value?.trim();
    if (!prompt) return;

    const resultDiv = document.getElementById('iaResult');
    const textDiv = document.getElementById('iaText');

    resultDiv.style.display = 'none';
    textDiv.textContent = '⏳ Gerando resposta com IA...';
    resultDiv.style.display = 'block';

    try {
        const r = await fetch(`${TTS.BASE}/ai`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                prompt,
                stream: streamAudio,
                voice: TTS._config.voice,
            }),
        });

        if (streamAudio) {
            // Streaming: lê texto do header, toca áudio em paralelo
            const aiText = r.headers.get('X-AI-Text') || '';
            textDiv.textContent = aiText;
            _currentIAText = aiText;

            // Toca stream
            const reader = r.body.getReader();
            const chunks = [];
            const audio = new Audio();
            let started = false;

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                // Pula header TEXT:
                const str = new TextDecoder().decode(value);
                if (str.startsWith('TEXT:')) continue;

                chunks.push(value);
                const blob = new Blob(chunks, { type: 'audio/wav' });
                const url = URL.createObjectURL(blob);

                if (!started) {
                    audio.src = url;
                    await audio.play().catch(() => { });
                    started = true;
                }
            }
        } else {
            const data = await r.json();
            textDiv.textContent = data.text;
            _currentIAText = data.text;

            if (data.audio_url) {
                const audio = document.getElementById('audioPlayer');
                const player = document.getElementById('ttsPlayer');
                audio.src = `${TTS.BASE}${data.audio_url.replace(/^.*\/audio\//, '/audio/')}`;
                player.style.display = 'flex';
                document.getElementById('playerInfo').textContent = '🤖 Resposta da IA';
            }
        }

    } catch (e) {
        textDiv.textContent = `Erro: ${e.message}`;
    }
}

async function ouvirIA() {
    if (_currentIAText) await TTS.speak(_currentIAText);
}