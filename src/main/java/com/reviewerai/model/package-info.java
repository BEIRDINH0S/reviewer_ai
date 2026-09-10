/**
 * Les données échangées entre les briques.
 *
 * <p>Tout est {@code record} immuable : une brique ne peut pas modifier ce qu'une autre a
 * produit, et deux critères qui tournent en parallèle ne peuvent pas se marcher dessus.
 *
 * <p>Ce package ne dépend de rien. Ni JGit, ni JavaParser, ni LangChain4j, ni Jackson n'y
 * apparaissent : c'est ce qui permet de changer n'importe laquelle de ces bibliothèques sans
 * toucher au vocabulaire du projet.
 *
 * <p>Trois familles :
 * <ul>
 *   <li><b>le projet évalué</b> — {@link com.reviewerai.model.ProjectRef},
 *       {@link com.reviewerai.model.ProjectFile}, {@link com.reviewerai.model.ProjectSnapshot} ;
 *   <li><b>ce qu'on envoie au modèle</b> — {@link com.reviewerai.model.CodeExcerpt},
 *       {@link com.reviewerai.model.EvaluationContext}, et le plus fin
 *       {@link com.reviewerai.model.MethodContext} appuyé sur
 *       {@link com.reviewerai.model.CodeGraph} ;
 *   <li><b>ce qui en ressort</b> — {@link com.reviewerai.model.Finding},
 *       {@link com.reviewerai.model.CriterionResult},
 *       {@link com.reviewerai.model.EvaluationResult}.
 * </ul>
 *
 * <p><b>Sécurité</b> : tout champ texte issu du modèle est une donnée non fiable. Le modèle
 * lit du code qui peut contenir une injection de prompt ; ce qu'il renvoie doit être échappé
 * au moment de l'affichage, selon le format de sortie.
 */
package com.reviewerai.model;
