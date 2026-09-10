package com.reviewerai.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Identité du projet évalué.
 *
 * <p>Le sujet impose que le rapport identifie le projet analysé : c'est cet objet qui porte
 * l'information, du chargement jusqu'au {@code .tex} final.
 *
 * @param name     nom affiché, en général le nom du répertoire racine
 * @param root     racine du projet sur le disque, après extraction s'il s'agissait d'une archive
 * @param origin   provenance déclarée par l'utilisateur
 * @param revision révision git, ou chaîne vide si la notion n'a pas de sens ici
 */
public record ProjectRef(String name, Path root, SourceKind origin, String revision) {

    public ProjectRef {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(origin, "origin");
        revision = revision == null ? "" : revision;
    }

    /** Projet sans révision, cas d'un répertoire ou d'une archive. */
    public static ProjectRef of(String name, Path root, SourceKind origin) {
        return new ProjectRef(name, root, origin, "");
    }

    /** Description courte pour le rapport, ex. {@code mon-projet (dépôt git, a1b2c3d)}. */
    public String describe() {
        return revision.isEmpty()
                ? name + " (" + origin.label() + ")"
                : name + " (" + origin.label() + ", " + shortRevision() + ")";
    }

    /** Les sept premiers caractères de la révision, comme le fait git. */
    public String shortRevision() {
        return revision.length() <= 7 ? revision : revision.substring(0, 7);
    }
}
