/**
 * Interface web : le programme sert une page, le navigateur l'affiche.
 *
 * <p>Contrairement à une fenêtre native, cette vue fonctionne à l'identique sur Windows,
 * macOS et dans un conteneur Docker, puisque le rendu est fait par le navigateur.
 *
 * <p>Le serveur utilisé est celui du JDK ({@code com.sun.net.httpserver}) : aucune
 * dépendance supplémentaire.
 *
 * <p>Trois particularités par rapport à la vue en ligne de commande :
 * <ul>
 *   <li>une analyse dure plusieurs minutes, donc la requête qui la lance répond aussitôt et
 *       la page vient ensuite demander l'avancement toutes les deux secondes ;
 *   <li>l'état est partagé entre le fil qui analyse et celui qui répond aux requêtes, d'où un
 *       {@link com.reviewerai.view.web.EvaluationState} immuable rangé dans une référence atomique ;
 *   <li>les textes affichés viennent du modèle, donc du code analysé : la page les insère avec
 *       {@code textContent} et le serveur interdit les scripts en ligne par un en-tête
 *       {@code Content-Security-Policy}.
 * </ul>
 */
package com.reviewerai.view.web;
