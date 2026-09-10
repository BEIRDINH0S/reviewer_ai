package com.reviewerai.service;

import com.reviewerai.model.EvaluationResult;

/**
 * Point d'entrée de la logique métier : évalue un projet de bout en bout.
 *
 * <p><b>Patron de conception : Façade</b> (structurel)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Une évaluation met en jeu six briques — chargement, sélection, contexte, modèle,
 *       vérification, historique — dont l'enchaînement et les dépendances mutuelles ne
 *       regardent pas l'appelant. Comment offrir à l'interface un accès simple à tout cela ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une interface à une seule méthode masque tout le sous-système. Le contrôleur demande
 *       une évaluation et reçoit un résultat.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Le cours signale qu'une façade devient vite une classe omnisciente. Le risque est
 *       écarté ici : l'implémentation n'enchaîne que des collaborateurs reçus par constructeur,
 *       elle ne contient aucune logique d'analyse. Le jour où elle en contiendrait, ce serait
 *       le signe qu'une brique manque.</dd>
 * </dl>
 *
 * <p>Le contrôleur dépend de cette interface, jamais d'une implémentation. C'est ce qui permet
 * de tester le contrôleur et les vues avec un service factice, sans projet ni modèle.
 */
public interface EvaluationService {

    /**
     * @param listener destinataire des notifications d'avancement ; utiliser
     *                 {@link ProgressListener#noop()} si l'appelant ne veut rien recevoir
     * @return le résultat de l'évaluation
     */
    EvaluationResult evaluate(ProgressListener listener);
}
