package com.reviewerai.project;

import com.reviewerai.model.ProjectFile;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Objects;

/**
 * Retient ou écarte des fichiers selon des motifs de chemin, façon {@code .gitignore}.
 *
 * <p>C'est la règle que l'utilisateur écrit lui-même : « n'évalue pas {@code **}{@code /generated/**} ».
 * Les motifs suivent la syntaxe glob du JDK, déjà connue de tout le monde et implémentée par
 * {@link java.nio.file.FileSystem#getPathMatcher} — inutile d'écrire un moteur de motifs.
 *
 * <p>Ordre d'évaluation, dans cet ordre précis :
 * <ol>
 *   <li>si un motif d'exclusion correspond, le fichier est écarté ;
 *   <li>sinon, s'il n'y a aucun motif d'inclusion, le fichier est retenu ;
 *   <li>sinon, le fichier est retenu seulement si un motif d'inclusion correspond.
 * </ol>
 * L'exclusion l'emporte donc toujours : c'est le comportement le moins surprenant, et le seul
 * qui permette de dire « tout le Java sauf ce répertoire-là ».
 */
public final class GlobFileSelector implements FileSelector {

    private final List<PathMatcher> includes;
    private final List<PathMatcher> excludes;

    private GlobFileSelector(List<PathMatcher> includes, List<PathMatcher> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    /**
     * @param includePatterns motifs d'inclusion ; liste vide signifie « tout »
     * @param excludePatterns motifs d'exclusion
     */
    public static GlobFileSelector of(List<String> includePatterns, List<String> excludePatterns) {
        return new GlobFileSelector(compile(includePatterns), compile(excludePatterns));
    }

    /** Raccourci : tout sauf les motifs donnés. */
    public static GlobFileSelector excluding(String... excludePatterns) {
        return of(List.of(), List.of(excludePatterns));
    }

    /** Raccourci : uniquement les motifs donnés. */
    public static GlobFileSelector including(String... includePatterns) {
        return of(List.of(includePatterns), List.of());
    }

    @Override
    public boolean accepts(ProjectFile file) {
        Path path = Path.of(file.path());
        if (matchesAny(excludes, path)) {
            return false;
        }
        return includes.isEmpty() || matchesAny(includes, path);
    }

    private static boolean matchesAny(List<PathMatcher> matchers, Path path) {
        return matchers.stream().anyMatch(m -> m.matches(path));
    }

    private static List<PathMatcher> compile(List<String> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        return patterns.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .toList();
    }
}
