package com.reviewerai.context;

import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.FileSelector;

/**
 * Choisit ce qui part au modèle pour un critère donné.
 *
 * <p><b>Patron de conception : Stratégie</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet pose le problème sans imposer de réponse : un projet contient bien plus de
 *       code qu'une requête ne peut en transporter. Il faut donc sélectionner, découper,
 *       éventuellement résumer — et il existe plusieurs façons raisonnables de le faire, dont
 *       aucune n'est la bonne pour tous les critères. Comment garder ce choix ouvert ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une interface, une implémentation par méthode de sélection. Les critères ne savent pas
 *       comment leur contexte a été constitué ; changer de stratégie se fait d'une ligne dans
 *       la fabrique, et se mesure en comparant deux rapports.</dd>
 *   <dt>Remarques</dt>
 *   <dd>C'est le point d'extension le plus intéressant du projet, parce que la qualité de
 *       l'évaluation en dépend directement : à modèle égal, un bon contexte donne un bon
 *       rapport. {@link RepresentativeFileContextBuilder} tient le rôle par défaut ;
 *       {@link CallGraphContextBuilder} est la variante fine, appuyée sur le graphe d'appel.</dd>
 * </dl>
 *
 * <p>Toute implémentation doit respecter deux règles :
 * <ul>
 *   <li><b>tenir le budget de jetons</b> — un contexte trop gros est tronqué par le serveur,
 *       en silence, et c'est la fin de l'extrait qui saute : celle qui contient souvent
 *       l'essentiel ;
 *   <li><b>être déterministe</b> — deux exécutions sur le même projet doivent produire le même
 *       contexte, sans quoi le rapport n'est pas reproductible.
 * </ul>
 */
@FunctionalInterface
public interface ContextBuilder {

    /**
     * @param project    le projet évalué
     * @param descriptor le critère à évaluer
     * @param selector   les fichiers que ce critère veut voir
     * @return le contexte à envoyer ; vide si rien de pertinent n'a été trouvé
     */
    EvaluationContext build(ProjectSnapshot project, CriterionDescriptor descriptor, FileSelector selector);
}
