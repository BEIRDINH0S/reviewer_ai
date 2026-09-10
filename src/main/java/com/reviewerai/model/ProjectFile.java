package com.reviewerai.model;

import java.util.Objects;

/**
 * Un fichier du projet évalué, tel que le chargement l'a vu.
 *
 * <p>Ne porte pas le contenu : un projet de plusieurs centaines de fichiers tiendrait mal en
 * mémoire, et la plupart des fichiers ne seront jamais lus. Le contenu est relu à la demande
 * par la brique Contexte, via {@code SafeFiles}.
 *
 * @param path      chemin relatif à la racine du projet, toujours avec des {@code /}
 * @param kind      nature du fichier, déterminée par le chemin
 * @param sizeBytes taille en octets
 * @param lineCount nombre de lignes, ou {@code 0} pour un fichier non textuel
 */
public record ProjectFile(String path, FileKind kind, long sizeBytes, int lineCount) {

    public ProjectFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("taille négative : " + sizeBytes);
        }
    }

    /** Nom du fichier, sans son répertoire. */
    public String fileName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** Répertoire parent relatif, chaîne vide à la racine. */
    public String directory() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /** Extension en minuscules sans le point, chaîne vide s'il n'y en a pas. */
    public String extension() {
        String name = fileName();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
