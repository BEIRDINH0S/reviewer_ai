package com.reviewerai.service;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.ProjectSnapshot;

/**
 * Reçoit l'avancement d'une évaluation pendant son déroulement.
 *
 * <p><b>Patron de conception : Observateur</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Une évaluation dure plusieurs minutes, essentiellement à cause des appels au modèle.
 *       Sans retour intermédiaire, la page web paraîtrait figée et la console muette — or le
 *       sujet exige que l'interface affiche la progression et les erreurs rencontrées. Comment
 *       informer l'affichage sans que le métier en dépende ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Les vues implémentent cette interface et se font passer au service, qui les notifie au
 *       fil de l'analyse. Le lien ne va que dans un sens : le service ne sait pas qui l'écoute,
 *       ni même s'il y a quelqu'un.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Le cours prévoit une classe abstraite {@code Observable} gérant une liste
 *       d'observateurs. Ici un seul observateur suffit et Java ne permet qu'un héritage : une
 *       interface laisse la vue web être à la fois une vue et un observateur. Les corps par
 *       défaut évitent aux vues d'implémenter ce qui ne les intéresse pas — c'est la
 *       ségrégation des interfaces sans multiplier les interfaces.</dd>
 * </dl>
 *
 * <p>Attention : ces méthodes sont appelées depuis le fil d'exécution de l'analyse, pas depuis
 * celui de l'interface. Une implémentation qui touche à un composant graphique doit repasser
 * par le mécanisme prévu par sa bibliothèque.
 */
public interface ProgressListener {

    /** Une étape majeure commence : chargement, sélection des critères, rédaction du rapport. */
    default void onStage(String stage) {
    }

    /** Le projet vient d'être chargé ; son inventaire est connu. */
    default void onProjectLoaded(ProjectSnapshot project) {
    }

    /** Le nombre total de critères à évaluer est connu. */
    default void onTotalCriteria(int total) {
    }

    /** L'évaluation d'un critère commence. */
    default void onCriterionStarted(String label, int index, int total) {
    }

    /** L'évaluation d'un critère vient de se terminer, réussie ou non. */
    default void onCriterionFinished(CriterionResult result, int index, int total) {
    }

    /**
     * Un appel au modèle va être passé.
     *
     * <p>Notifié séparément parce que c'est de loin l'opération la plus longue : sans ce
     * signal, l'interface semble bloquée pendant toute la durée de l'appel.
     */
    default void onLlmCall(String criterionLabel, int estimatedTokens) {
    }

    /** Un incident non bloquant s'est produit (fichier illisible, critère abandonné…). */
    default void onWarning(String message) {
    }

    /** Ne fait rien. Évite aux appelants de tester {@code null} avant chaque notification. */
    static ProgressListener noop() {
        return new ProgressListener() {
        };
    }
}
