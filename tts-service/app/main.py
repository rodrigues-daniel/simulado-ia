"""
ConcursoAI — Kokoro TTS Service
Pipeline: texto → Kokoro → WAV/streaming
Integração: Ollama + Simulador de Questões
"""

import asyncio
import io
import logging
import os
import re
import time
import uuid
from pathlib import Path
from typing import AsyncGenerator, Optional

import httpx
import numpy as np
import soundfile as sf
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import (
    FileResponse,
    JSONResponse,
    StreamingResponse,
)
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

# ── Logging ─────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
log = logging.getLogger("tts")

# ── Kokoro ───────────────────────────────────────────────────────────────────
try:
    from kokoro import KPipeline
    KOKORO_AVAILABLE = True
    log.info("Kokoro TTS carregado com sucesso.")
except ImportError:
    KOKORO_AVAILABLE = False
    log.warning("Kokoro não disponível — modo mock ativo.")

# ── Config em memória ────────────────────────────────────────────────────────
_CONFIG = {
    "voice":   os.getenv("TTS_DEFAULT_VOICE", "bf_alice"),
    "speed":   float(os.getenv("TTS_DEFAULT_SPEED", "1.0")),
    "lang":    os.getenv("TTS_LANG", "p"),
    "enabled": True,
}

# Vozes disponíveis no Kokoro (pt-BR compatíveis)
AVAILABLE_VOICES = {
    "bf_alice":  {"label": "Alice (feminino)",   "lang": "p"},
    "bm_george": {"label": "George (masculino)", "lang": "p"},
    "af_heart":  {"label": "Heart (feminino)",   "lang": "a"},
    "am_adam":   {"label": "Adam (masculino)",   "lang": "a"},
    "bf_emma":   {"label": "Emma (feminino)",    "lang": "b"},
    "bm_lewis":  {"label": "Lewis (masculino)",  "lang": "b"},
}

# ── Kokoro pipeline singleton ────────────────────────────────────────────────
_pipeline: Optional[object] = None

def get_pipeline() -> Optional[object]:
    global _pipeline
    if not KOKORO_AVAILABLE:
        return None
    if _pipeline is None:
        log.info("Inicializando Kokoro pipeline lang=%s", _CONFIG["lang"])
        _pipeline = KPipeline(lang_code=_CONFIG["lang"])
        log.info("Kokoro pipeline pronto.")
    return _pipeline

# ── Cache de áudio ────────────────────────────────────────────────────────────
CACHE_DIR = Path("/app/cache")
CACHE_DIR.mkdir(parents=True, exist_ok=True)

def cache_key(text: str, voice: str, speed: float) -> str:
    import hashlib
    raw = f"{text}|{voice}|{speed}"
    return hashlib.sha256(raw.encode()).hexdigest()[:16]

def get_cached(text: str, voice: str, speed: float) -> Optional[Path]:
    key  = cache_key(text, voice, speed)
    path = CACHE_DIR / f"{key}.wav"
    return path if path.exists() else None

def save_cache(text: str, voice: str, speed: float,
               audio: np.ndarray, sr: int) -> Path:
    key  = cache_key(text, voice, speed)
    path = CACHE_DIR / f"{key}.wav"
    sf.write(str(path), audio, sr)
    return path

# ── Text chunking para textos longos ─────────────────────────────────────────
def split_text(text: str, max_chars: int = 200) -> list[str]:
    """
    Divide texto em chunks respeitando pontuação.
    Evita cortar no meio de frases.
    """
    text = text.strip()
    if len(text) <= max_chars:
        return [text]

    # Tenta dividir por parágrafo, depois por sentença
    separators = ["\n\n", "\n", ". ", "! ", "? ", "; ", ", "]
    chunks: list[str] = []
    remaining = text

    while len(remaining) > max_chars:
        split_at = -1
        for sep in separators:
            idx = remaining.rfind(sep, 0, max_chars)
            if idx > 0:
                split_at = idx + len(sep)
                break

        if split_at <= 0:
            split_at = max_chars

        chunks.append(remaining[:split_at].strip())
        remaining = remaining[split_at:].strip()

    if remaining:
        chunks.append(remaining)

    return [c for c in chunks if c]

# ── Geração de áudio ──────────────────────────────────────────────────────────
def generate_audio(text: str, voice: str, speed: float) -> tuple[np.ndarray, int]:
    """Gera áudio completo via Kokoro ou mock."""
    pipeline = get_pipeline()

    if pipeline is None:
        # Mock: silêncio de 1s
        sr = 24000
        return np.zeros(sr, dtype=np.float32), sr

    samples_list = []
    sr           = 24000

    for chunk in split_text(text):
        generator = pipeline(
            chunk,
            voice=voice,
            speed=speed,
            split_pattern=None,
        )
        for _, _, audio in generator:
            if audio is not None:
                samples_list.append(audio)

    if not samples_list:
        return np.zeros(sr, dtype=np.float32), sr

    combined = np.concatenate(samples_list)
    return combined, sr


async def generate_audio_stream(
    text: str, voice: str, speed: float
) -> AsyncGenerator[bytes, None]:
    """
    Streaming de áudio chunk a chunk.
    Cada chunk é um WAV válido — o player começa antes de terminar.
    """
    pipeline = get_pipeline()

    if pipeline is None:
        # Mock: envia silêncio em 3 partes
        sr     = 24000
        silence = np.zeros(sr // 3, dtype=np.float32)
        for _ in range(3):
            buf = io.BytesIO()
            sf.write(buf, silence, sr, format="WAV", subtype="PCM_16")
            yield buf.getvalue()
            await asyncio.sleep(0.1)
        return

    sr = 24000
    for chunk_text in split_text(text):
        try:
            generator = pipeline(
                chunk_text,
                voice=voice,
                speed=speed,
                split_pattern=None,
            )
            for _, _, audio in generator:
                if audio is not None and len(audio) > 0:
                    buf = io.BytesIO()
                    sf.write(buf, audio, sr, format="WAV", subtype="PCM_16")
                    yield buf.getvalue()
                    await asyncio.sleep(0)  # permite event loop rodar
        except Exception as e:
            log.warning("Chunk '%s...' falhou: %s", chunk_text[:30], e)
            continue


# ── FastAPI ───────────────────────────────────────────────────────────────────
app = FastAPI(
    title="ConcursoAI TTS Service",
    description="Kokoro TTS + Ollama + Simulador",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve frontend
FRONTEND_DIR = Path("/app/frontend")
if FRONTEND_DIR.exists():
    app.mount("/ui", StaticFiles(directory=str(FRONTEND_DIR), html=True),
              name="frontend")


# ── Models ────────────────────────────────────────────────────────────────────
class TTSRequest(BaseModel):
    text:  str
    voice: Optional[str] = None
    speed: Optional[float] = None

class ConfigRequest(BaseModel):
    voice:   Optional[str]   = None
    speed:   Optional[float] = None
    lang:    Optional[str]   = None
    enabled: Optional[bool]  = None

class AIRequest(BaseModel):
    prompt:   str
    stream:   bool = False
    voice:    Optional[str]  = None
    speed:    Optional[float] = None
    model:    Optional[str]  = None

class QuestionAudioRequest(BaseModel):
    question_id: int
    field: str = "answer"  # "question" | "answer" | "explanation"


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status":          "ok",
        "kokoro":          KOKORO_AVAILABLE,
        "tts_enabled":     _CONFIG["enabled"],
        "voice":           _CONFIG["voice"],
        "speed":           _CONFIG["speed"],
        "cache_files":     len(list(CACHE_DIR.glob("*.wav"))),
    }


@app.get("/voices")
async def list_voices():
    return {"voices": AVAILABLE_VOICES}


@app.get("/config")
async def get_config():
    return _CONFIG


@app.post("/config")
async def update_config(req: ConfigRequest):
    global _pipeline
    if req.voice   is not None: _CONFIG["voice"]   = req.voice
    if req.speed   is not None: _CONFIG["speed"]   = req.speed
    if req.enabled is not None: _CONFIG["enabled"] = req.enabled

    # Se lang mudou, reinicia pipeline
    if req.lang is not None and req.lang != _CONFIG["lang"]:
        _CONFIG["lang"] = req.lang
        _pipeline = None
        log.info("Pipeline reiniciado para lang=%s", req.lang)

    return {"status": "ok", "config": _CONFIG}


@app.post("/tts")
async def tts(req: TTSRequest):
    """Gera WAV completo e retorna arquivo."""
    if not _CONFIG["enabled"]:
        raise HTTPException(503, "TTS desabilitado.")

    text  = req.text.strip()
    voice = req.voice or _CONFIG["voice"]
    speed = req.speed or _CONFIG["speed"]

    if not text:
        raise HTTPException(400, "Texto vazio.")
    if len(text) > 5000:
        raise HTTPException(400, "Texto muito longo (máx 5000 chars).")

    t0 = time.time()

    # Verifica cache
    cached = get_cached(text, voice, speed)
    if cached:
        log.info("Cache hit — %s", cached.name)
        return FileResponse(
            str(cached),
            media_type="audio/wav",
            headers={
                "X-Cache":    "HIT",
                "X-Duration": "0",
            },
        )

    try:
        audio, sr = await asyncio.get_event_loop().run_in_executor(
            None, generate_audio, text, voice, speed
        )
    except Exception as e:
        log.error("Erro TTS: %s", e)
        raise HTTPException(500, f"Erro ao gerar áudio: {e}")

    path     = save_cache(text, voice, speed, audio, sr)
    elapsed  = round(time.time() - t0, 3)

    log.info("TTS gerado: %.2fs | %d chars | %s",
             elapsed, len(text), voice)

    return FileResponse(
        str(path),
        media_type="audio/wav",
        headers={
            "X-Cache":        "MISS",
            "X-Duration":     str(elapsed),
            "X-Chars":        str(len(text)),
            "X-Voice":        voice,
        },
    )


@app.post("/stream")
async def stream_tts(req: TTSRequest):
    """Streaming de áudio chunk a chunk."""
    if not _CONFIG["enabled"]:
        raise HTTPException(503, "TTS desabilitado.")

    text  = req.text.strip()
    voice = req.voice or _CONFIG["voice"]
    speed = req.speed or _CONFIG["speed"]

    if not text:
        raise HTTPException(400, "Texto vazio.")

    log.info("Streaming TTS: %d chars | voice=%s", len(text), voice)

    return StreamingResponse(
        generate_audio_stream(text, voice, speed),
        media_type="audio/wav",
        headers={
            "X-Voice":            voice,
            "Cache-Control":      "no-cache",
            "Transfer-Encoding":  "chunked",
        },
    )


@app.post("/ai")
async def ai_tts(req: AIRequest):
    """
    Pipeline completo:
    1. Envia prompt ao Ollama
    2. Recebe resposta texto
    3. Gera áudio (stream ou completo)
    """
    ollama_url   = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    model        = req.model or os.getenv("OLLAMA_MODEL", "llama3.2:3b")
    voice        = req.voice or _CONFIG["voice"]
    speed        = req.speed or _CONFIG["speed"]

    # 1. Chama Ollama
    try:
        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(
                f"{ollama_url}/api/generate",
                json={"model": model, "prompt": req.prompt, "stream": False},
            )
            resp.raise_for_status()
            ai_text = resp.json().get("response", "").strip()
    except httpx.ConnectError:
        raise HTTPException(503, "Ollama indisponível.")
    except Exception as e:
        raise HTTPException(500, f"Erro Ollama: {e}")

    if not ai_text:
        raise HTTPException(500, "Ollama retornou resposta vazia.")

    # 2. Se stream → retorna áudio streaming com header de texto
    if req.stream and _CONFIG["enabled"]:
        log.info("AI→TTS streaming: %d chars", len(ai_text))

        async def combined_stream():
            # Primeiro envia o texto como chunk especial (JSON header)
            import json
            header = json.dumps({"type": "text", "content": ai_text})
            yield f"TEXT:{header}\n".encode()
            # Depois envia o áudio
            async for chunk in generate_audio_stream(ai_text, voice, speed):
                yield chunk

        return StreamingResponse(
            combined_stream(),
            media_type="application/octet-stream",
            headers={"X-AI-Text": ai_text[:200]},
        )

    # 3. Sem stream → retorna JSON com texto + URL de áudio
    cache_path = None
    audio_url  = None

    if _CONFIG["enabled"]:
        try:
            audio, sr  = await asyncio.get_event_loop().run_in_executor(
                None, generate_audio, ai_text, voice, speed
            )
            cache_path = save_cache(ai_text, voice, speed, audio, sr)
            audio_url  = f"/audio/{cache_path.name}"
        except Exception as e:
            log.warning("TTS falhou, retornando só texto: %s", e)

    return JSONResponse({
        "text":      ai_text,
        "audio_url": audio_url,
        "model":     model,
        "voice":     voice,
        "tts":       _CONFIG["enabled"],
    })


@app.get("/audio/{filename}")
async def serve_audio(filename: str):
    """Serve arquivos de áudio do cache."""
    # Sanitiza nome do arquivo
    filename = Path(filename).name
    if not filename.endswith(".wav"):
        raise HTTPException(400, "Somente .wav permitido.")
    path = CACHE_DIR / filename
    if not path.exists():
        raise HTTPException(404, "Áudio não encontrado.")
    return FileResponse(str(path), media_type="audio/wav")


@app.get("/questions/{question_id}/audio")
async def question_audio(question_id: int, field: str = "answer"):
    """
    Integração com o simulador de questões.
    Busca a questão no simulador e gera áudio do campo solicitado.
    """
    simulador_url = os.getenv("SIMULADOR_BASE_URL", "http://localhost:8080")

    # Busca questão no simulador
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(
                f"{simulador_url}/api/questions/{question_id}"
            )
            resp.raise_for_status()
            question = resp.json()
    except httpx.ConnectError:
        raise HTTPException(503, "Simulador indisponível.")
    except httpx.HTTPStatusError as e:
        raise HTTPException(e.response.status_code, "Questão não encontrada.")

    # Seleciona campo
    text_map = {
        "question":    question.get("statement", ""),
        "answer":      question.get("explanation", ""),
        "professor":   question.get("professorTip", ""),
        "law":         question.get("lawParagraph", ""),
    }
    text = text_map.get(field, "")
    if not text:
        raise HTTPException(404, f"Campo '{field}' vazio ou não encontrado.")

    voice = _CONFIG["voice"]
    speed = _CONFIG["speed"]

    # Verifica cache
    cached = get_cached(text, voice, speed)
    if not cached:
        audio, sr = await asyncio.get_event_loop().run_in_executor(
            None, generate_audio, text, voice, speed
        )
        cached = save_cache(text, voice, speed, audio, sr)

    base_url = os.getenv("TTS_PUBLIC_URL", "http://localhost:8765")

    return JSONResponse({
        "question_id":      question_id,
        "field":            field,
        "text":             text[:100] + "..." if len(text) > 100 else text,
        "audio_url":        f"{base_url}/audio/{cached.name}",
        "audio_stream_url": f"{base_url}/stream",
        "voice":            voice,
    })


@app.post("/questions/{question_id}/stream-audio")
async def stream_question_audio(question_id: int, field: str = "answer"):
    """Streaming direto do áudio de uma questão."""
    simulador_url = os.getenv("SIMULADOR_BASE_URL", "http://localhost:8080")

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(
                f"{simulador_url}/api/questions/{question_id}"
            )
            resp.raise_for_status()
            question = resp.json()
    except Exception as e:
        raise HTTPException(503, f"Erro ao buscar questão: {e}")

    text_map = {
        "question":  question.get("statement", ""),
        "answer":    question.get("explanation", ""),
        "professor": question.get("professorTip", ""),
        "law":       question.get("lawParagraph", ""),
    }
    text  = text_map.get(field, "")
    voice = _CONFIG["voice"]
    speed = _CONFIG["speed"]

    if not text:
        raise HTTPException(404, "Campo vazio.")

    return StreamingResponse(
        generate_audio_stream(text, voice, speed),
        media_type="audio/wav",
        headers={"Transfer-Encoding": "chunked"},
    )


@app.delete("/cache")
async def clear_cache():
    """Limpa cache de áudio."""
    removed = 0
    for f in CACHE_DIR.glob("*.wav"):
        f.unlink()
        removed += 1
    log.info("Cache limpo: %d arquivos removidos", removed)
    return {"removed": removed}


@app.get("/cache/stats")
async def cache_stats():
    files   = list(CACHE_DIR.glob("*.wav"))
    total   = sum(f.stat().st_size for f in files)
    return {
        "files":      len(files),
        "total_mb":   round(total / 1024 / 1024, 2),
    }