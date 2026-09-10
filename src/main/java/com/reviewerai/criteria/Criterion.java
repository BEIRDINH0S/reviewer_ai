package com.reviewerai.criteria;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.service.ProgressListener;

/**
 * Un critère d'évaluation : il regarde un projet et lui met une note.
 *
 * <p><b>Patron de conception : Stratégie</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet exige qu'un nouveau critère puisse être ajouté sans modifier substantiellement
 *       les composants existants. Comment définir une famille d'analyses interchangeables —
 *       architecture, lisibilité, sécurité, présence de tests — que le moteur enchaîne sans
 *       savoir ce qu'elles font ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une interface commune. Le moteur reçoit une liste de {@code Criterion} et les exécute
 *       tous de la même façon. Ajouter un critère, c'est écrire une classe et l'inscrire dans
 *       {@link CriterionRegistry} — aucune classe existante ne change.</dd>
 *   <dt>Remarques</dt>
 *   <dd>C'est le point le plus important de l'architecture, celui qui répond à « comment
 *       ajoutez-vous un critère ? ». Deux familles cohabitent derrière cette interface :
 *       {@link AbstractLlmCriterion} pour les critères qui interrogent un modèle, et les
 *       critères déterministes qui comptent des fichiers. Le moteur ne fait pas la différence,
 *       ce qui est exactement ce qu'on veut : le sujet demande de combiner analyses
 *       déterministes et analyses par IA.</dd>
 * </dl>
 */
public interface Criterion {

    /** La carte d'identité du critère. */
    CriterionDescriptor descriptor();

    /**
     * Évalue le projet et renvoie une note argumentée.
     *
     * <p>Une implémentation ne doit <b>jamais</b> laisser remonter une exception pour un motif
     * ordinaire — modèle indisponible, réponse illisible, fichier manquant. Elle renvoie un
     * {@link CriterionResult#failed} : le sujet demande une récupération partielle, donc un
     * critère en panne ne doit pas emporter les autres.
     *
     * @param project  le projet évalué
     * @param listener destinataire des notifications d'avancement
     * @return la note et son argumentaire
     */
    CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener);

    /** Raccourci de lisibilité, très utilisé dans le moteur et les tests. */
    default String id() {
        return descriptor().id();
    }
}
