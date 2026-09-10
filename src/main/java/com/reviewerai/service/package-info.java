/**
 * Orchestration d'une évaluation.
 *
 * <p>{@link com.reviewerai.service.EvaluationService} est la porte d'entrée unique du métier :
 * l'interface demande une évaluation et reçoit un résultat, sans rien savoir des six briques
 * mobilisées.
 *
 * <p>{@link com.reviewerai.service.EvaluationServiceFactory} est le seul endroit du projet,
 * avec {@code Main}, où des classes concrètes sont nommées. C'est là que se lisent les
 * réponses à deux questions du sujet : changer de modèle, et ajouter un critère — une ligne
 * chacune.
 *
 * <p>{@link com.reviewerai.service.ProgressListener} fait le chemin inverse : le service
 * informe les vues sans les connaître.
 *
 */
package com.reviewerai.service;
