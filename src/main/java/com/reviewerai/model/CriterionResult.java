package com.reviewerai.model;

import java.util.List;
import java.util.Objects;

/**
 * L'évaluation d'un critère : une note, une appréciation, et le détail qui la justifie.
 *
 * <p>C'est la structure imposée par le sujet, reprise telle quelle depuis le format JSON
 * attendu du modèle : {@code criterion}, {@code score}, {@code maxScore}, {@code strengths},
 * {@code weaknesses}, {@code recommendations}.
 *
 * <p><b>Sécurité</b> : toutes les listes de texte viennent du modèle. Elles sont échappées
 * avant affichage, jamais avant stockage — l'échappement dépend du format de sortie.
 *
 * @param criterionId     identifiant technique, stable dans le temps
 * @param label           libellé affiché, en français
 * @param score           note obtenue, entre 0 et {@code maxScore}
 * @param maxScore        note maximale possible
 * @param summary         appréciation en une ou deux phrases
 * @param strengths       points forts relevés
 * @param weaknesses      points faibles relevés
 * @param recommendations pistes d'amélioration
 * @param findings        problèmes localisés qui appuient la note
 * @param evaluated       {@code false} si le critère n'a pas pu être évalué (voir {@link #failed})
 */
public record CriterionResult(
        String criterionId,
        String label,
        int score,
        int maxScore,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recommendations,
        List<Finding> findings,
        boolean evaluated) {

    public CriterionResult {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(label, "label");
        if (maxScore <= 0) {
            throw new IllegalArgumentException("maxScore doit être positif : " + maxScore);
        }
        if (score < 0 || score > maxScore) {
            throw new IllegalArgumentException("score hors [0," + maxScore + "] : " + score);
        }
        summary = summary == null ? "" : summary;
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * Résultat d'un critère qui n'a pas pu être évalué.
     *
     * <p>Le sujet demande une récupération partielle : si le modèle ne répond pas pour un
     * critère, les autres doivent quand même figurer dans le rapport. On produit donc une
     * ligne explicite plutôt que de faire disparaître le critère, ce qui laisserait croire
     * qu'il a été jugé.
     *
     * <p>La note est mise à zéro mais le critère est marqué non évalué : le calcul de la note
     * globale l'ignore, pour ne pas transformer une panne technique en mauvaise note.
     */
    public static CriterionResult failed(String criterionId, String label, int maxScore, String reason) {
        return new CriterionResult(criterionId, label, 0, maxScore,
                "Critère non évalué : " + reason,
                List.of(), List.of(), List.of(), List.of(), false);
    }

    /** Note ramenée sur 100, pour comparer des critères de barèmes différents. */
    public double percentage() {
        return 100.0 * score / maxScore;
    }
}
