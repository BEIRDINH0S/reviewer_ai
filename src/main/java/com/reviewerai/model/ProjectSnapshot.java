package com.reviewerai.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Le projet chargé : son identité et l'inventaire de ses fichiers.
 *
 * <p>C'est l'objet que produit la brique Chargement et que consomment tous les critères.
 * Immuable : un critère ne peut pas modifier ce que verra le suivant.
 *
 * <p>Aucun contenu de fichier n'est stocké ici. Un snapshot d'un projet de mille fichiers
 * pèse quelques dizaines de kilo-octets, ce qui permet de le garder dans l'historique.
 *
 * @param project le projet concerné
 * @param files   tous les fichiers retenus au chargement, triés par chemin
 */
public record ProjectSnapshot(ProjectRef project, List<ProjectFile> files) {

    public ProjectSnapshot {
        Objects.requireNonNull(project, "project");
        files = files == null ? List.of() : files.stream()
                .sorted(Comparator.comparing(ProjectFile::path))
                .toList();
    }

    /** Snapshot vide, pour les tests et pour dégrader proprement si le chargement échoue. */
    public static ProjectSnapshot empty(ProjectRef project) {
        return new ProjectSnapshot(project, List.of());
    }

    /** Les fichiers d'une nature donnée. */
    public List<ProjectFile> ofKind(FileKind kind) {
        return files.stream().filter(f -> f.kind() == kind).toList();
    }

    /** Les fichiers d'une des natures données, dans l'ordre du snapshot. */
    public List<ProjectFile> ofKinds(FileKind... kinds) {
        List<FileKind> wanted = List.of(kinds);
        return files.stream().filter(f -> wanted.contains(f.kind())).toList();
    }

    /** Retrouve un fichier par son chemin relatif. */
    public Optional<ProjectFile> byPath(String path) {
        return files.stream().filter(f -> f.path().equals(path)).findFirst();
    }

    /** Combien de fichiers de chaque nature, dans l'ordre de l'énumération. */
    public Map<FileKind, Integer> countByKind() {
        Map<FileKind, Integer> counts = new LinkedHashMap<>();
        for (FileKind kind : FileKind.values()) {
            int n = ofKind(kind).size();
            if (n > 0) {
                counts.put(kind, n);
            }
        }
        return Map.copyOf(counts);
    }

    /**
     * L'arborescence, regroupée par répertoire.
     *
     * <p>Utilisée par l'interface pour afficher la structure du projet importé, une exigence
     * explicite du sujet. La {@link TreeMap} garantit un ordre alphabétique stable, donc un
     * affichage identique d'une exécution à l'autre.
     */
    public Map<String, List<ProjectFile>> tree() {
        Map<String, List<ProjectFile>> byDirectory = new TreeMap<>();
        for (ProjectFile file : files) {
            byDirectory.computeIfAbsent(file.directory(), k -> new java.util.ArrayList<>()).add(file);
        }
        byDirectory.replaceAll((dir, content) -> List.copyOf(content));
        return Map.copyOf(byDirectory);
    }

    /** Nombre total de lignes de code Java de production. */
    public int mainJavaLines() {
        return ofKind(FileKind.JAVA_MAIN).stream().mapToInt(ProjectFile::lineCount).sum();
    }

    /** Vrai si le projet ne contient aucun fichier exploitable. */
    public boolean isEmpty() {
        return files.isEmpty();
    }
}
