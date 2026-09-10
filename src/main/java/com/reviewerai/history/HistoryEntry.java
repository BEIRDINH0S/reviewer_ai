package com.reviewerai.history;

import com.reviewerai.model.EvaluationResult;

import java.time.Instant;
import java.util.Objects;

/**
 * Une évaluation passée, telle qu'elle apparaît dans la liste de l'historique.
 *
 * <p>Volontairement plus pauvre qu'un {@link EvaluationResult} : afficher la liste des vingt
 * dernières analyses ne doit pas obliger à relire vingt rapports complets depuis le disque.
 * Le résultat entier n'est chargé que si l'utilisateur ouvre l'entrée.
 *
 * @param id           identifiant de l'analyse, unique et triable dans le temps
 * @param projectName  nom du projet évalué
 * @param analysedAt   date de l'analyse
 * @param overallScore note globale sur 20
 * @param criteriaCount nombre de critères évalués
 * @param modelName    modèle interrogé
 */
public record HistoryEntry(
        String id,
        String projectName,
        Instant analysedAt,
        double overallScore,
        int criteriaCount,
        String modelName) {

    public HistoryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(analysedAt, "analysedAt");
        modelName = modelName == null ? "" : modelName;
    }

    /** Résumé d'un résultat complet. */
    public static HistoryEntry of(String id, EvaluationResult result) {
        return new HistoryEntry(
                id,
                result.project().name(),
                result.analysedAt(),
                result.overallScore(),
                result.evaluatedCriteria().size(),
                result.modelName());
    }
}
