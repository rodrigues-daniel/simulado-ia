package br.cebraspe.simulado.domain.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula pontuação Cebraspe com modelo real:
 * - Certa:  +1 ponto
 * - Errada: -1 ponto (anula uma certa)
 * - Branco:  0 ponto (neutro)
 *
 * Nota Líquida = Certas - Erradas  (mínimo 0)
 * Nota Bruta   = Certas
 *
 * Estratégia ótima: só responder quando P(acerto) > 50%.
 * Se P(acerto) = 50%: EV = 0.5*(+1) + 0.5*(-1) = 0 → deixar em branco
 * Se P(acerto) > 50%: EV > 0 → vale responder
 */
public class ScoreCalculator {

    private final int     totalQuestions;
    private final int     correct;
    private final int     wrong;
    private final int     blank;
    private final double  pointsCorrect;  // padrão 1.0
    private final double  pointsWrong;    // padrão 1.0 (deduzido)

    // Notas de corte para comparação
    private final Double cutScoreAmpla;
    private final Double cutScoreQuota;
    private final int    vacanciesAmpla;
    private final int    vacanciesQuota;

    public ScoreCalculator(int total, int correct, int wrong, int blank,
                            double pointsCorrect, double pointsWrong,
                            Double cutScoreAmpla, Double cutScoreQuota,
                            int vacanciesAmpla, int vacanciesQuota) {
        this.totalQuestions = total;
        this.correct        = correct;
        this.wrong          = wrong;
        this.blank          = blank;
        this.pointsCorrect  = pointsCorrect;
        this.pointsWrong    = pointsWrong;
        this.cutScoreAmpla  = cutScoreAmpla;
        this.cutScoreQuota  = cutScoreQuota;
        this.vacanciesAmpla = vacanciesAmpla;
        this.vacanciesQuota = vacanciesQuota;
    }

    // ── Pontuações ─────────────────────────────────────────────────────
    public double grossScore() {
        return round(correct * pointsCorrect);
    }

    public double netScore() {
        double raw = (correct * pointsCorrect) - (wrong * pointsWrong);
        return round(Math.max(0, raw));
    }

    public double maxPossible() {
        return round(totalQuestions * pointsCorrect);
    }

    // ── Análise de risco de chute ────────────────────────────────────────
    public double guessRiskRate() {
        int answered = correct + wrong;
        if (answered == 0) return 0;
        return round((double) wrong / answered * 100);
    }

    public String riskLevel() {
        double rate = guessRiskRate();
        if (rate > 40) return "ALTO";
        if (rate > 25) return "MEDIO";
        if (rate > 0)  return "BAIXO";
        return "NEUTRO";
    }

    // ── Quantas pode deixar em branco mantendo a nota ──────────────────
    // Se deixar X em branco e responder apenas as que tem >50% certeza:
    // NetScore = Certas - Erradas
    // Para maximizar: só responda quando tem mais de 50% de certeza
    public int optimalBlankCount() {
        // Dado o desempenho atual, calcula quantas questões sem resposta
        // maximizariam a nota líquida
        if (wrong == 0) return blank; // já está ótimo

        // Taxa atual de acerto entre as respondidas
        int answered = correct + wrong;
        if (answered == 0) return totalQuestions;

        double accuracy = (double) correct / answered;

        // Se acerta menos de 50%: deveria deixar tudo em branco
        if (accuracy <= 0.5) return answered;

        // Se acerta mais de 50%: vale responder — calcula quantas pode
        // deixar em branco sem perder nota
        // Fórmula: NetScore = C - E ≥ target
        // Deixar em branco as questões onde incerteza > 50%
        int canLeaveBlank = (int) Math.round(wrong * 2.0);
        return Math.min(canLeaveBlank, blank + wrong);
    }

    // ── 5 Cenários comparativos ─────────────────────────────────────────
    public List<Scenario> buildScenarios() {
        List<Scenario> scenarios = new ArrayList<>();
        int answered = correct + wrong;

        // Taxa atual de acerto
        double accuracy = answered > 0 ? (double) correct / answered : 0;

        // Cenário 1: Resultado atual (como está)
        scenarios.add(new Scenario(
                "Resultado Atual",
                "Desempenho real desta simulação.",
                correct, wrong, blank,
                netScore(),
                classifyVsTarget(netScore())
        ));

        // Cenário 2: Se tivesse deixado em branco todas as erradas
        int c2Correct = correct;
        int c2Wrong   = 0;
        int c2Blank   = blank + wrong;
        double c2Net  = round(c2Correct * pointsCorrect);
        scenarios.add(new Scenario(
                "Sem Arriscar",
                "Se tivesse deixado em branco todas as questões que errou.",
                c2Correct, c2Wrong, c2Blank,
                c2Net,
                classifyVsTarget(c2Net)
        ));

        // Cenário 3: Se tivesse respondido todos os brancos e acertado 50%
        int extraAnswered = blank;
        int extraCorrect  = (int) Math.round(extraAnswered * 0.5);
        int extraWrong    = extraAnswered - extraCorrect;
        int c3Correct     = correct + extraCorrect;
        int c3Wrong       = wrong  + extraWrong;
        double c3Net      = round(Math.max(0,
                c3Correct * pointsCorrect - c3Wrong * pointsWrong));
        scenarios.add(new Scenario(
                "Chutando 50% dos Brancos",
                "Se tivesse chutado as questões em branco com 50% de acerto.",
                c3Correct, c3Wrong, 0,
                c3Net,
                classifyVsTarget(c3Net)
        ));

        // Cenário 4: Desempenho com 70% de acerto nas respondidas
        int c4Answered = answered + blank;
        int c4Correct  = (int) Math.round(c4Answered * 0.7);
        int c4Wrong    = c4Answered - c4Correct;
        double c4Net   = round(Math.max(0,
                c4Correct * pointsCorrect - c4Wrong * pointsWrong));
        scenarios.add(new Scenario(
                "Meta 70% de Acerto",
                "Projeção se você acertasse 70% de todas as questões.",
                c4Correct, c4Wrong, 0,
                c4Net,
                classifyVsTarget(c4Net)
        ));

        // Cenário 5: Desempenho máximo (só responde quando tem certeza)
        // Assume que accuracy >= 70%: responde 80% das questões
        int c5Answered = (int) Math.round(totalQuestions * 0.8);
        int c5Correct  = (int) Math.round(c5Answered * Math.max(accuracy, 0.75));
        int c5Wrong    = c5Answered - c5Correct;
        int c5Blank    = totalQuestions - c5Answered;
        double c5Net   = round(Math.max(0,
                c5Correct * pointsCorrect - c5Wrong * pointsWrong));
        scenarios.add(new Scenario(
                "Estratégia Otimizada",
                "Responde apenas 80% com alta certeza, deixa o resto em branco.",
                c5Correct, c5Wrong, c5Blank,
                c5Net,
                classifyVsTarget(c5Net)
        ));

        return scenarios;
    }

    // ── Status vs nota de corte ──────────────────────────────────────────
    public CutScoreStatus cutScoreStatus() {
        double net = netScore();
        String ampla = classifyVsTarget(net);
        String quota = cutScoreQuota != null
                ? (net >= cutScoreQuota ? "APROVADO" : "REPROVADO")
                : "N/A";

        double needAmpla = cutScoreAmpla != null
                ? Math.max(0, cutScoreAmpla - net) : 0;
        double needQuota = cutScoreQuota != null
                ? Math.max(0, cutScoreQuota - net) : 0;

        // Quantas questões adicionais precisaria acertar
        int qNeedAmpla = (int) Math.ceil(needAmpla / pointsCorrect);
        int qNeedQuota = (int) Math.ceil(needQuota / pointsCorrect);

        return new CutScoreStatus(
                net,
                cutScoreAmpla,
                cutScoreQuota,
                ampla,
                quota,
                needAmpla,
                needQuota,
                qNeedAmpla,
                qNeedQuota,
                vacanciesAmpla,
                vacanciesQuota,
                // Percentual de cota (geralmente 20%)
                vacanciesAmpla > 0
                        ? round((double) vacanciesQuota /
                                (vacanciesAmpla + vacanciesQuota) * 100)
                        : 0
        );
    }

    // ── Estratégias de prova ─────────────────────────────────────────────
    public List<String> buildStrategies() {
        List<String> strategies = new ArrayList<>();
        double accuracy = correct + wrong > 0
                ? (double) correct / (correct + wrong) : 0;

        // Estratégia 1: Gestão de brancos
        if (accuracy < 0.5) {
            strategies.add(
                "🚫 Seu acerto atual é abaixo de 50%. " +
                "Deixar em branco é MELHOR do que chutar — você perde pontos " +
                "com cada erro. Responda apenas o que tem certeza."
            );
        } else if (accuracy < 0.7) {
            strategies.add(
                "⚠️ Acerto entre 50-70%. Seja seletivo: só responda " +
                "quando tiver pelo menos 60% de certeza. " +
                "Deixe em branco as questões com dúvida real."
            );
        } else {
            strategies.add(
                "✅ Bom acerto (>70%). Você pode responder com mais segurança. " +
                "Ainda assim, questões com palavras absolutas " +
                "(sempre, nunca, exclusivamente) merecem atenção extra."
            );
        }

        // Estratégia 2: Palavras-armadilha
        strategies.add(
            "🎯 Estratégia anti-armadilha: ao ver 'sempre', 'nunca', " +
            "'exclusivamente', 'somente' ou 'apenas', marque como ERRADO " +
            "por padrão — acerta ~70% das vezes no Cebraspe."
        );

        // Estratégia 3: Gestão de tempo
        strategies.add(
            "⏱ Gestão de tempo: com " + totalQuestions + " questões, " +
            "você tem em média " +
            round((90.0 / totalQuestions)) + " min/questão (prova de 90min). " +
            "Marque as difíceis e volte depois — não fique preso."
        );

        // Estratégia 4: Cota
        if (vacanciesQuota > 0) {
            strategies.add(
                "🏷 Cota (" + round((double) vacanciesQuota /
                        (vacanciesAmpla + vacanciesQuota) * 100) + "% das vagas): " +
                "se concorrer por cota, a nota de corte é geralmente " +
                "menor. Foque em atingir " +
                (cutScoreQuota != null ? cutScoreQuota : "a nota mínima") +
                " pontos."
            );
        }

        // Estratégia 5: Revisão
        strategies.add(
            "🔄 Reserve os últimos 15 minutos para revisão. " +
            "Questões com negação dupla (não... não...) têm alta " +
            "taxa de erro — releia com calma."
        );

        return strategies;
    }

    private String classifyVsTarget(double score) {
        if (cutScoreAmpla == null) return "SEM_META";
        return score >= cutScoreAmpla ? "APROVADO" : "REPROVADO";
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // ── Records de resultado ─────────────────────────────────────────────
    public record Scenario(
            String label,
            String description,
            int correct,
            int wrong,
            int blank,
            double netScore,
            String vsTarget
    ) {}

    public record CutScoreStatus(
            double netScore,
            Double cutScoreAmpla,
            Double cutScoreQuota,
            String statusAmpla,
            String statusQuota,
            double needMoreAmpla,
            double needMoreQuota,
            int    questionsNeedAmpla,
            int    questionsNeedQuota,
            int    vacanciesAmpla,
            int    vacanciesQuota,
            double quotaPercentage
    ) {}
}