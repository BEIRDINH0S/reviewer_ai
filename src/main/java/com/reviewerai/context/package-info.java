/**
 * Brique Contexte — décide ce qui part au modèle.
 *
 * <p>Répond à la question centrale du sujet : un projet contient bien plus de code qu'une
 * requête ne peut en transporter. La réponse du projet tient en trois décisions :
 * <ul>
 *   <li><b>sélectionner</b> — chaque critère déclare les fichiers qui le concernent, via un
 *       {@link com.reviewerai.project.FileSelector} ;
 *   <li><b>classer</b> — parmi ces fichiers, on envoie d'abord ceux qui portent le plus
 *       d'information par jeton dépensé ;
 *   <li><b>plafonner</b> — on s'arrête au budget, sans jamais tronquer un fichier au milieu.
 * </ul>
 *
 * <p>Deux stratégies, la seconde étant le prolongement ambitieux de la première :
 * {@link com.reviewerai.context.RepresentativeFileContextBuilder} envoie des fichiers entiers,
 * {@link com.reviewerai.context.CallGraphContextBuilder} envoie des méthodes avec leur
 * voisinage dans le graphe d'appel — indispensable pour juger couplage et cohésion.
 *
 * <p>Un même résumé chiffré du projet ({@link com.reviewerai.context.ProjectInventory})
 * accompagne tous les contextes : sans lui, le modèle ne sait pas s'il voit tout le projet ou
 * un centième.
 *
 */
package com.reviewerai.context;
