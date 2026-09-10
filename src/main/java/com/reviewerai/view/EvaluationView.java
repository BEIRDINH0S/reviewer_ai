package com.reviewerai.view;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.service.ProgressListener;

/**
 * Vue MVC : affiche l'état et le résultat d'une évaluation.
 *
 * <p>Le contrôleur ne connaît que cette interface. Ajouter une vue ne demande donc aucune
 * modification du contrôleur ni du service — c'est la séparation entre interface graphique et
 * logique métier que le sujet annonce évaluer avec une attention particulière.
 *
 * <p>Une vue ne calcule rien et ne décide rien : elle reçoit un {@link EvaluationResult} déjà
 * constitué et l'affiche. Elle n'a accès ni aux critères, ni au modèle, ni au projet chargé.
 */
public interface EvaluationView {

    /** L'évaluation démarre. */
    void onEvaluationStarted(EvaluationConfig config);

    /** L'évaluation s'est terminée normalement. */
    void onEvaluationFinished(EvaluationResult result, String renderedReport);

    /** L'évaluation a échoué et aucun résultat n'est disponible. */
    void onEvaluationFailed(Exception error);

    /** Incident non bloquant : le sujet demande que l'interface affiche les erreurs rencontrées. */
    void onWarning(String message);

    /**
     * Adapte cette vue en {@link ProgressListener} pour la passer au service.
     *
     * <p>Par défaut seuls les avertissements sont relayés. Une vue qui veut afficher une barre
     * de progression redéfinit cette méthode.
     */
    default ProgressListener asProgressListener() {
        return new ProgressListener() {
            @Override
            public void onWarning(String message) {
                EvaluationView.this.onWarning(message);
            }
        };
    }
}
