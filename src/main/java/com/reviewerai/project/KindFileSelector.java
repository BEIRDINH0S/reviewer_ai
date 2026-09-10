package com.reviewerai.project;

import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;

import java.util.EnumSet;
import java.util.Set;

/**
 * Retient les fichiers d'une ou plusieurs natures.
 *
 * <p>La règle la plus utilisée du projet : chaque critère déclare ce qu'il veut voir. Le
 * critère « présence de tests » demande {@code JAVA_TEST} et {@code JAVA_MAIN}, le critère
 * « qualité du Dockerfile » demande {@code DOCKER}.
 */
public final class KindFileSelector implements FileSelector {

    private final Set<FileKind> accepted;

    private KindFileSelector(Set<FileKind> accepted) {
        this.accepted = accepted;
    }

    /** Ne retient que les natures données. */
    public static KindFileSelector only(FileKind... kinds) {
        if (kinds.length == 0) {
            return new KindFileSelector(EnumSet.noneOf(FileKind.class));
        }
        return new KindFileSelector(EnumSet.copyOf(Set.of(kinds)));
    }

    /** Retient tout sauf les natures données. */
    public static KindFileSelector except(FileKind... kinds) {
        EnumSet<FileKind> set = EnumSet.allOf(FileKind.class);
        set.removeAll(Set.of(kinds));
        return new KindFileSelector(set);
    }

    @Override
    public boolean accepts(ProjectFile file) {
        return accepted.contains(file.kind());
    }
}
