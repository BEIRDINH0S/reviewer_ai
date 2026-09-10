package com.reviewerai.llm;

import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.ProjectSnapshot;

/**
 * Transforme la réponse brute du modèle en {@link CriterionResult} validé.
 *
 * <p><b>C'est la frontière de confiance du projet.</b> Tout ce qui franchit cette interface
 * est propre ; tout ce qui arrive dedans est suspect, parce que le modèle a lu du code qui
 * peut contenir une injection de prompt.
 *
 * <p>Classe séparée du fournisseur pour deux raisons : elle se teste sans modèle — on lui
 * donne une chaîne, on vérifie le résultat — et elle reste valable quel que soit le
 * fournisseur choisi.
 *
 * <p>Règles de validation attendues de toute implémentation :
 * <ul>
 *   <li>extraire le premier objet JSON de la réponse : les petits modèles encadrent volontiers
 *       leur réponse de texte ou de barrières de code ;
 *   <li>ramener la note dans {@code [0, maxScore]} plutôt que d'accepter un 15/10 ;
 *   <li>ignorer un élément dont un champ obligatoire manque, sans inventer de valeur ;
 *   <li>tronquer les textes trop longs — un modèle poussé peut recracher un fichier entier ;
 *   <li>ne jamais faire confiance au nom de critère renvoyé : celui qui fait autorité est
 *       celui du {@link CriterionDescriptor} passé en entrée ;
 *   <li>vérifier que les chemins de fichiers cités existent réellement dans le projet, et
 *       écarter le signalement sinon.
 * </ul>
 *
 */
public interface CriterionResponseParser {

    /**
     * @param rawResponse la réponse brute du modèle, non fiable
     * @param descriptor  le critère réellement demandé, qui fait autorité
     * @param project     le projet évalué, pour vérifier que les fichiers cités existent
     * @return le résultat validé
     * @throws InvalidResponseException si la réponse est inexploitable
     */
    CriterionResult parse(String rawResponse, CriterionDescriptor descriptor, ProjectSnapshot project);

    /** La réponse du modèle ne respecte pas le format demandé. */
    class InvalidResponseException extends RuntimeException {
        public InvalidResponseException(String message) {
            super(message);
        }
    }
}
