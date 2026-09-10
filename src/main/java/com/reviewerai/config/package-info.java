/**
 * Réglages de l'application.
 *
 * <p>Deux objets, séparés parce qu'ils n'ont ni le même cycle de vie ni la même origine :
 * {@link com.reviewerai.config.EvaluationConfig} décrit une analyse et arrive du formulaire ou
 * de la ligne de commande à chaque lancement ; {@link com.reviewerai.config.ServerConfig}
 * décrit le serveur web et est fixé une fois au démarrage.
 *
 * <p>Aucun état statique modifiable ici, et aucun secret : le sujet interdit qu'un jeton
 * figure dans le dépôt, et le choix d'un modèle local fait qu'il n'y en a aucun à protéger.
 */
package com.reviewerai.config;
