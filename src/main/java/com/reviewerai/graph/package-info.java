/**
 * Brique Graphe — construit le graphe d'appel du dépôt : qui appelle quoi.
 *
 * <p>C'est ce graphe qui permet de trouver le voisinage d'une méthode modifiée, et donc de se
 * passer d'envoyer tout le code au modèle.
 *
 * <p>Le parseur travaille sans classpath : les appels vers des bibliothèques externes ne sont
 * pas résolus et l'arête correspondante est simplement absente. C'est le prix à payer pour ne
 * jamais compiler le code analysé.
 *
 */
package com.reviewerai.graph;
