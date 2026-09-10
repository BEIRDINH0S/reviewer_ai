package com.reviewerai.graph;

import com.reviewerai.model.CodeGraph;

import java.nio.file.Path;

/**
 * Brique Graphe — construit le graphe d'appel du repo (qui appelle quoi).
 *
 *
 * <p>Contrat : parsing des sources uniquement. Jamais de compilation, jamais de résolution
 * de dépendances depuis un dépôt distant (cf. section sécurité).
 */
public interface CodeGraphBuilder {

    /**
     * @param repo racine du repo à indexer (état HEAD sur disque)
     * @return le graphe d'appel ; jamais {@code null}, éventuellement partiel si certains
     *         fichiers n'ont pas pu être parsés
     */
    CodeGraph build(Path repo);
}
