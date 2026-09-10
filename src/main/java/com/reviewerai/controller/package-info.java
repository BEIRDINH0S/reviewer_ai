/**
 * Contrôleur MVC — fait le lien entre les vues et le métier.
 *
 * <p>{@link com.reviewerai.controller.EvaluationController} reçoit une demande, appelle la
 * façade métier, écrit le rapport et rend le résultat à la vue. Il n'évalue rien et n'affiche
 * rien.
 *
 * <p>Les deux vues du projet partagent ce contrôleur unique. C'est la vérification concrète
 * que la séparation interface / logique métier tient : si une vue avait besoin d'un contrôleur
 * à elle, c'est qu'elle contiendrait du métier.
 *
 */
package com.reviewerai.controller;
