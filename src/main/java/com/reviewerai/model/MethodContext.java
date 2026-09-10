package com.reviewerai.model;

import java.util.List;

/**
 * Une méthode et son voisinage dans le graphe d'appel.
 *
 * <p>Anciennement {@code ReviewContext}, du temps où l'outil relisait une pull request. Ce
 * n'est plus la porte d'entrée du modèle — c'est {@link EvaluationContext} qui l'est — mais
 * la structure reste au cœur de la stratégie de contexte la plus fine du projet : pour les
 * critères qui raisonnent au niveau du code, envoyer une méthode avec ses appelants et ses
 * appelées vaut bien mieux qu'envoyer un fichier entier.
 *
 * <p>Produit par la brique Contexte à partir du {@link CodeGraph}, converti ensuite en
 * {@link CodeExcerpt} pour entrer dans un {@link EvaluationContext}.
 *
 * @param target          la méthode au centre du contexte
 * @param neighbors       les méthodes voisines retenues, les plus pertinentes d'abord
 * @param estimatedTokens estimation du coût du contexte
 */
public record MethodContext(
        MethodRef target,
        List<Neighbor> neighbors,
        int estimatedTokens) {

    public MethodContext {
        neighbors = neighbors == null ? List.of() : List.copyOf(neighbors);
    }

    /**
     * Une méthode voisine incluse dans le contexte.
     *
     * @param method   la méthode voisine
     * @param relation son lien avec la méthode centrale
     * @param source   son code source, éventuellement réduit à sa signature
     * @param full     {@code true} si {@code source} est le corps complet, {@code false} si c'est
     *                 seulement la signature (mode dégradé quand le budget de jetons est serré)
     */
    public record Neighbor(MethodRef method, Relation relation, String source, boolean full) {}

    public enum Relation {
        /** La méthode centrale appelle ce voisin. */
        CALLEE,
        /** Ce voisin appelle la méthode centrale. */
        CALLER
    }
}
