package com.reviewerai.model;

import java.util.Set;

/**
 * Le graphe d'appel du repo : qui appelle quoi.
 *
 * <p>Produit par la brique Graphe, interrogé par la brique Contexte. L'implémentation
 * s'appuie sur JGraphT, mais cette interface reste volontairement minimale pour que la
 * brique Contexte n'en dépende pas.
 */
public interface CodeGraph {

    /** Toutes les méthodes connues du graphe. */
    Set<MethodRef> methods();

    /** Les méthodes appelées par {@code method} (successeurs sortants). */
    Set<MethodRef> callees(MethodRef method);

    /** Les méthodes qui appellent {@code method} (prédécesseurs entrants). */
    Set<MethodRef> callers(MethodRef method);

    /**
     * Retrouve une méthode du graphe à partir de sa signature.
     *
     * <p>Utile parce que le {@code MethodRef} venant du Diff porte des numéros de ligne
     * mais pas forcément les mêmes métadonnées que celui du Graphe.
     */
    java.util.Optional<MethodRef> findBySignature(String signature);

    /** Le code source d'une méthode, tel que lu lors de la construction du graphe. */
    java.util.Optional<String> sourceOf(MethodRef method);

    /** Graphe vide, utile pour les tests et pour dégrader proprement en cas d'échec. */
    static CodeGraph empty() {
        return new CodeGraph() {
            @Override public Set<MethodRef> methods() { return Set.of(); }
            @Override public Set<MethodRef> callees(MethodRef m) { return Set.of(); }
            @Override public Set<MethodRef> callers(MethodRef m) { return Set.of(); }
            @Override public java.util.Optional<MethodRef> findBySignature(String s) { return java.util.Optional.empty(); }
            @Override public java.util.Optional<String> sourceOf(MethodRef m) { return java.util.Optional.empty(); }
        };
    }
}
