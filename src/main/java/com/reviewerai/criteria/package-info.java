/**
 * Les critères d'évaluation : ce que l'outil sait juger.
 *
 * <p>C'est le coeur du sujet. Un critère regarde un projet et lui met une note argumentée ;
 * le moteur en enchaîne autant que l'utilisateur en a coché.
 *
 * <p><b>Ajouter un critère</b> — écrire une classe qui implémente
 * {@link com.reviewerai.criteria.Criterion}, l'inscrire dans le catalogue construit par
 * {@code EvaluationServiceFactory}. Rien d'autre ne change : ni le moteur, ni le rapport, ni
 * les vues, ni l'historique. C'est l'exigence d'extensibilité du sujet, vérifiable en
 * comptant les fichiers modifiés.
 *
 * <p>Deux familles derrière la même interface, ce qui est précisément ce que le sujet demande
 * en parlant de combiner analyses déterministes et analyses par IA :
 * <ul>
 *   <li><b>confiés au modèle</b> — architecture, lisibilité, SOLID, patrons, gestion des
 *       erreurs, sécurité. Ils héritent de {@link com.reviewerai.criteria.AbstractLlmCriterion},
 *       qui fixe le déroulé une fois pour toutes ;
 *   <li><b>déterministes</b> — présence de tests, documentation, organisation du projet. Ils
 *       comptent des fichiers. Exacts, gratuits, instantanés, et ils fonctionnent quand aucun
 *       modèle ne tourne.
 * </ul>
 *
 * <p>Le moteur ne distingue pas les deux familles : il ne voit que des
 * {@link com.reviewerai.criteria.Criterion}.
 *
 */
package com.reviewerai.criteria;
