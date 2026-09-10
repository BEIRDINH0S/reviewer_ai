package com.reviewerai.model;

import java.util.Objects;

/**
 * Où se situe un problème dans le projet évalué.
 *
 * <p>Remplace l'usage direct d'un {@link MethodRef} dans un {@link Finding} : tous les
 * critères ne raisonnent pas au niveau de la méthode. Un critère « organisation du projet »
 * désigne un répertoire, un critère « gestion des exceptions » désigne une ligne précise.
 *
 * @param filePath chemin relatif du fichier, ou chaîne vide si le problème vise le projet entier
 * @param symbol   classe ou méthode concernée, pour l'affichage ; peut être vide
 * @param line     ligne visée (1-indexée), ou {@code -1} si le problème ne vise pas une ligne
 */
public record CodeLocation(String filePath, String symbol, int line) {

    /** Position inconnue : le problème porte sur le projet dans son ensemble. */
    public static final CodeLocation PROJECT_WIDE = new CodeLocation("", "", -1);

    public CodeLocation {
        filePath = filePath == null ? "" : filePath;
        symbol = symbol == null ? "" : symbol;
    }

    /** Position dans un fichier, sans ligne précise. */
    public static CodeLocation ofFile(String filePath) {
        return new CodeLocation(filePath, "", -1);
    }

    /** Position à une ligne donnée d'un fichier. */
    public static CodeLocation ofLine(String filePath, int line) {
        return new CodeLocation(filePath, "", line);
    }

    /** Position déduite d'une méthode connue du graphe d'appel. */
    public static CodeLocation ofMethod(MethodRef method, int line) {
        Objects.requireNonNull(method, "method");
        return new CodeLocation(method.filePath(), method.displayName(), line);
    }

    /** Vrai si la position désigne un fichier. */
    public boolean hasFile() {
        return !filePath.isEmpty();
    }

    /** Vrai si la position désigne une ligne. */
    public boolean hasLine() {
        return line > 0;
    }

    /** Description courte pour le rapport, ex. {@code src/App.java:42 (App#run)}. */
    public String describe() {
        if (!hasFile()) {
            return "projet";
        }
        String base = hasLine() ? filePath + ":" + line : filePath;
        return symbol.isEmpty() ? base : base + " (" + symbol + ")";
    }
}
