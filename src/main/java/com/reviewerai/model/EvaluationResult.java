package com.reviewerai.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Le résultat complet d'une évaluation de projet.
 *
 * <p>Seul objet que manipulent les vues, le rapport et l'historique. Il contient tout ce que
 * le sujet exige de retrouver dans le document final : le projet, la date, la configuration,
 * le modèle utilisé, les critères, les notes, et le détail par critère.
 *
 * <p>Le rapport se déduit entièrement de cet objet. C'est ce qui garantit que la structure du
 * document ne dépend pas du modèle : le LLM remplit des champs, il ne dessine pas le rapport.
 *
 * @param project     le projet évalué
 * @param analysedAt  instant de fin d'analyse
 * @param modelName   nom du modèle interrogé, ou {@code hors ligne} si aucun appel n'a eu lieu
 * @param configLabel résumé lisible de la configuration utilisée
 * @param criteria    un résultat par critère demandé, dans l'ordre d'exécution
 * @param duration    durée totale
 * @param llmCalls    nombre d'appels réellement passés au modèle
 * @param warnings    incidents non bloquants rencontrés en chemin
 */
public record EvaluationResult(
        ProjectRef project,
        Instant analysedAt,
        String modelName,
        String configLabel,
        List<CriterionResult> criteria,
        Duration duration,
        int llmCalls,
        List<String> warnings) {

    public EvaluationResult {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(analysedAt, "analysedAt");
        modelName = modelName == null ? "" : modelName;
        configLabel = configLabel == null ? "" : configLabel;
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        duration = duration == null ? Duration.ZERO : duration;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Résultat vide, produit quand le projet ne contient rien d'analysable. */
    public static EvaluationResult empty(ProjectRef project) {
        return new EvaluationResult(project, Instant.now(), "", "",
                List.of(), Duration.ZERO, 0, List.of());
    }

    /** Les critères réellement évalués ; ceux en échec sont exclus. */
    public List<CriterionResult> evaluatedCriteria() {
        return criteria.stream().filter(CriterionResult::evaluated).toList();
    }

    /** Somme des notes obtenues, sur les seuls critères évalués. */
    public int totalScore() {
        return evaluatedCriteria().stream().mapToInt(CriterionResult::score).sum();
    }

    /** Somme des notes maximales, sur les seuls critères évalués. */
    public int totalMaxScore() {
        return evaluatedCriteria().stream().mapToInt(CriterionResult::maxScore).sum();
    }

    /**
     * Note globale sur 20.
     *
     * <p>Calculée sur les critères évalués uniquement : un critère perdu à cause d'une panne du
     * modèle ne doit pas peser comme un zéro. Renvoie {@code 0} si aucun critère n'a abouti.
     */
    public double overallScore() {
        int max = totalMaxScore();
        return max == 0 ? 0.0 : 20.0 * totalScore() / max;
    }

    /** Tous les signalements, tous critères confondus. */
    public List<Finding> allFindings() {
        return criteria.stream().flatMap(c -> c.findings().stream()).toList();
    }

    /** Nombre de signalements d'une gravité donnée. */
    public long countBySeverity(Severity severity) {
        return allFindings().stream().filter(f -> f.severity() == severity).count();
    }

    /** Vrai si au moins un critère n'a pas pu être évalué. */
    public boolean isPartial() {
        return criteria.stream().anyMatch(c -> !c.evaluated());
    }
}
