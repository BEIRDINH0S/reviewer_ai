package com.reviewerai.model;

import java.util.List;

/**
 * Une méthode modifiée entre les deux commits analysés.
 *
 * <p>Produit par la brique Diff, consommé par la brique Contexte.
 *
 * @param method       la méthode concernée, telle qu'elle existe dans le commit HEAD
 * @param changeType   nature du changement
 * @param changedLines lignes modifiées à l'intérieur de la méthode (numéros côté HEAD, 1-indexés).
 *                     Sert à dire au LLM « concentre-toi sur ces lignes-là ».
 * @param sourceAtHead code source complet de la méthode dans le commit HEAD
 */
public record ChangedMethod(
        MethodRef method,
        ChangeType changeType,
        List<Integer> changedLines,
        String sourceAtHead) {

    public ChangedMethod {
        changedLines = changedLines == null ? List.of() : List.copyOf(changedLines);
    }

    public enum ChangeType {
        /** La méthode n'existait pas dans le commit de base. */
        ADDED,
        /** Le corps ou la signature a changé. */
        MODIFIED
        // Les méthodes supprimées ne sont pas relues : il n'y a plus de code à analyser.
    }
}
