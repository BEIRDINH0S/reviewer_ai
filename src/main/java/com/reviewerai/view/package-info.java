/**
 * Couche présentation : deux interfaces pour le même contrôleur.
 *
 * <p>{@link com.reviewerai.view.cli} pour la ligne de commande,
 * {@link com.reviewerai.view.web} pour le navigateur. Les deux implémentent
 * {@link com.reviewerai.view.EvaluationView} ; le contrôleur ne connaît que cette interface.
 *
 * <p>Une vue affiche, elle ne calcule pas. Toute logique qu'on serait tenté d'écrire ici a sa
 * place dans {@link com.reviewerai.service} ou dans {@link com.reviewerai.model}.
 */
package com.reviewerai.view;
