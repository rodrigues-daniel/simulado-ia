package br.cebraspe.simulado.ai.rag.cleaning;

public enum CleaningStrategy {

    PDF_LEGAL(
        "Documento Jurídico/Legal (PDF)",
        "Remove cabeçalhos de página, rodapés, numeração, " +
        "normaliza citações legais e remove artefatos de OCR.",
        0.85
    ),
    PDF_ACADEMIC(
        "Documento Acadêmico (PDF)",
        "Remove referências bibliográficas isoladas, " +
        "cabeçalhos de seção repetidos e notas de rodapé.",
        0.80
    ),
    CSV_STRUCTURED(
        "Dados Estruturados (CSV)",
        "Remove linhas vazias, normaliza separadores, " +
        "trata valores nulos e remove duplicatas exatas.",
        0.90
    ),
    TXT_PLAIN(
        "Texto Plano (TXT)",
        "Remove linhas em branco excessivas, normaliza " +
        "espaços e quebras de linha, remove caracteres de controle.",
        0.88
    ),
    TXT_QUESTIONS(
        "Banco de Questões (TXT)",
        "Identifica e preserva estrutura de questões, " +
        "remove gabaritos inline e normaliza numeração.",
        0.82
    ),
    GENERIC(
        "Limpeza Genérica",
        "Aplica limpeza básica: espaços, caracteres especiais " +
        "e normalização Unicode.",
        0.70
    );

    public final String label;
    public final String description;
    public final double baseConfidence;

    CleaningStrategy(String label, String description, double baseConfidence) {
        this.label          = label;
        this.description    = description;
        this.baseConfidence = baseConfidence;
    }
}