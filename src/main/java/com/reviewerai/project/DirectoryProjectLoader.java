package com.reviewerai.project;

import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;
import com.reviewerai.util.SafeFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Charge un projet depuis un répertoire déjà présent sur le disque.
 *
 * <p>C'est le cas le plus simple, et celui dont les deux autres se servent : une archive est
 * extraite puis chargée par cette classe, un dépôt git est identifié puis chargé par cette
 * classe. Toute la logique d'inventaire tient donc à un seul endroit.
 *
 * @param maxFileSizeBytes taille maximale d'un fichier retenu ; au-delà, le fichier est ignoré
 *                         plutôt que de faire échouer le chargement
 */
public record DirectoryProjectLoader(long maxFileSizeBytes) implements ProjectLoader {

    /** Taille par défaut : au-delà, un fichier n'est plus du code écrit à la main. */
    public static final long DEFAULT_MAX_FILE_SIZE = 1_000_000L;

    public DirectoryProjectLoader {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes doit être positif");
        }
    }

    /** Chargeur avec la taille maximale par défaut. */
    public static DirectoryProjectLoader standard() {
        return new DirectoryProjectLoader(DEFAULT_MAX_FILE_SIZE);
    }

    @Override
    public boolean supports(Path source) {
        return Files.isDirectory(source);
    }

    @Override
    public ProjectSnapshot load(Path source, FileSelector selector) {
        return load(source, selector, SourceKind.DIRECTORY, "");
    }

    /**
     * Charge en imposant la provenance déclarée.
     *
     * <p>Utilisée par {@link ArchiveProjectLoader} et {@link GitProjectLoader}, qui ont déjà
     * fait le travail propre à leur mode et veulent que le rapport garde la bonne origine
     * plutôt que d'afficher « répertoire ».
     */
    ProjectSnapshot load(Path source, FileSelector selector, SourceKind origin, String revision) {
        Path root = source.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new ProjectLoadException("Le chemin n'est pas un répertoire : " + source);
        }

        ProjectRef project = new ProjectRef(nameOf(root), root, origin, revision);
        List<ProjectFile> files = new ArrayList<>();

        for (Path path : SafeFiles.listFiles(root, maxFileSizeBytes)) {
            String relative = SafeFiles.toRepoRelative(root, path);
            FileKind kind = FileClassifier.classify(relative);
            long size = SafeFiles.sizeOf(path);
            if (size < 0) {
                continue; // fichier devenu illisible entre le parcours et la lecture
            }
            // Les lignes ne sont comptées que pour du texte : ouvrir un binaire n'apprendrait
            // rien et coûterait une lecture complète.
            int lines = kind.isTextual() ? SafeFiles.countLines(root, path, maxFileSizeBytes) : 0;
            ProjectFile file = new ProjectFile(relative, kind, size, lines);
            if (selector.accepts(file)) {
                files.add(file);
            }
        }

        if (files.isEmpty()) {
            throw new ProjectLoadException("Aucun fichier analysable dans " + source);
        }
        return new ProjectSnapshot(project, files);
    }

    private static String nameOf(Path root) {
        Path fileName = root.getFileName();
        return fileName == null ? root.toString() : fileName.toString();
    }
}
