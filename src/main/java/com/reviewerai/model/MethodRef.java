package com.reviewerai.model;

import java.util.Objects;

/**
 * Identifiant stable d'une méthode dans le repo.
 *
 * <p>C'est la clé partagée entre toutes les briques : le Diff en produit, le Graphe les relie
 * entre elles, le Contexte les résout en code source. Deux {@code MethodRef} égaux doivent
 * désigner la même méthode, donc {@link #signature()} doit être normalisée de la même façon
 * partout (types de paramètres pleinement qualifiés, sans nom de paramètre, sans espace).
 *
 * <p>Exemple : {@code com.example.OrderService#applyDiscount(java.lang.String,int)}
 *
 * @param filePath  chemin du fichier relatif à la racine du repo (séparateur {@code /})
 * @param className nom pleinement qualifié de la classe déclarante
 * @param signature signature normalisée, telle que produite par {@link #of}
 * @param startLine première ligne de la déclaration (1-indexée)
 * @param endLine   dernière ligne de la déclaration (1-indexée, incluse)
 */
public record MethodRef(
        String filePath,
        String className,
        String signature,
        int startLine,
        int endLine) {

    public MethodRef {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(signature, "signature");
    }

    /**
     * Construit la signature normalisée à partir de ses composants.
     *
     * <p>À utiliser par TOUTES les briques : c'est ce qui garantit que le {@code MethodRef}
     * produit par le Diff est {@code equals} à celui produit par le Graphe.
     *
     * @param className      nom pleinement qualifié de la classe
     * @param methodName     nom simple de la méthode
     * @param parameterTypes types des paramètres, pleinement qualifiés si résolus
     */
    public static String signatureOf(String className, String methodName, java.util.List<String> parameterTypes) {
        return className + "#" + methodName + "(" + String.join(",", parameterTypes) + ")";
    }

    public static MethodRef of(String filePath, String className, String methodName,
                               java.util.List<String> parameterTypes, int startLine, int endLine) {
        return new MethodRef(filePath, className,
                signatureOf(className, methodName, parameterTypes), startLine, endLine);
    }

    /** Nom court pour l'affichage dans le rapport, ex. {@code OrderService#applyDiscount}. */
    public String displayName() {
        int dot = className.lastIndexOf('.');
        String simpleClass = dot >= 0 ? className.substring(dot + 1) : className;
        int hash = signature.indexOf('#');
        int paren = signature.indexOf('(', hash < 0 ? 0 : hash);
        String methodName = hash >= 0 && paren > hash ? signature.substring(hash + 1, paren) : signature;
        return simpleClass + "#" + methodName;
    }

    /** L'égalité ne porte que sur la signature : les lignes bougent d'un commit à l'autre. */
    @Override
    public boolean equals(Object o) {
        return o instanceof MethodRef other && signature.equals(other.signature);
    }

    @Override
    public int hashCode() {
        return signature.hashCode();
    }
}
