package com.reviewerai.model;

import java.util.Objects;

/**
 * Un morceau de code envoyé au modèle, avec la raison de sa présence.
 *
 * <p>C'est l'unité de découpage réclamée par le sujet : un projet contient bien plus de code
 * qu'une requête ne peut en transporter, donc on n'envoie jamais « le projet », on envoie une
 * liste d'extraits choisis.
 *
 * <p>Le champ {@link #reason} n'est pas décoratif : il part dans le prompt. Dire au modèle
 * « voici la classe la plus appelée du projet » vaut mieux que lui livrer du code sans
 * explication, et cela rend le contexte relisible quand on met une analyse au point.
 *
 * @param filePath  chemin relatif du fichier d'origine
 * @param startLine première ligne de l'extrait (1-indexée)
 * @param endLine   dernière ligne de l'extrait (1-indexée, incluse)
 * @param content   le code lui-même
 * @param reason    pourquoi cet extrait a été retenu, en une ligne
 */
public record CodeExcerpt(String filePath, int startLine, int endLine, String content, String reason) {

    public CodeExcerpt {
        Objects.requireNonNull(filePath, "filePath");
        content = content == null ? "" : content;
        reason = reason == null ? "" : reason;
    }

    /** Extrait couvrant un fichier entier. */
    public static CodeExcerpt wholeFile(String filePath, String content, String reason) {
        int lines = content == null || content.isEmpty() ? 0 : (int) content.lines().count();
        return new CodeExcerpt(filePath, 1, Math.max(1, lines), content, reason);
    }

    /**
     * Estimation grossière du coût en jetons.
     *
     * <p>Quatre caractères par jeton : c'est l'ordre de grandeur admis pour du code, et la
     * précision d'un vrai découpeur n'apporterait rien puisque le budget est déjà une marge de
     * sécurité. Compter trop haut est sans danger, compter trop bas ferait tronquer la requête
     * par le serveur.
     */
    public int estimatedTokens() {
        return content.length() / 4 + 16;
    }

    /** Nombre de lignes de l'extrait. */
    public int lineCount() {
        return Math.max(0, endLine - startLine + 1);
    }
}
