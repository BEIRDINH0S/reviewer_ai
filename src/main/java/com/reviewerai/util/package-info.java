/**
 * Utilitaires transverses.
 *
 * <p>{@link com.reviewerai.util.SafeFiles} centralise les précautions à prendre en lisant le
 * dépôt analysé : liens symboliques, chemins qui s'échappent de la racine, fichiers trop gros.
 * Les briques passent par lui plutôt que d'appeler {@code Files} directement, pour qu'un oubli
 * dans l'une d'elles ne devienne pas une faille.
 */
package com.reviewerai.util;
