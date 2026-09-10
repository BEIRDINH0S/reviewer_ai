/**
 * Brique Vérification — écarte les signalements non justifiés.
 *
 * <p>Un petit modèle se trompe souvent. Sans ce filtre, le rapport contiendrait assez de faux
 * positifs pour que personne ne le lise, et l'outil perdrait tout intérêt.
 *
 * <p>Chaque règle est une classe indépendante qui implémente
 * {@link com.reviewerai.verify.FindingVerifier} ;
 * {@link com.reviewerai.verify.CompositeFindingVerifier} les enchaîne. Ajouter une règle ne
 * demande donc de modifier aucune classe existante.
 *
 * <p>{@link com.reviewerai.verify.KnownLocationVerifier} joue un rôle particulier : en
 * vérifiant que le modèle ne cite que des fichiers qu'on lui a réellement montrés, il détecte
 * l'extrapolation — et, accessoirement, une injection de prompt qui aurait porté ses fruits.
 *
 */
package com.reviewerai.verify;
