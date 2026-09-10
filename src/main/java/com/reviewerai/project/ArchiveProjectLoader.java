package com.reviewerai.project;

import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;
import com.reviewerai.util.SafeFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Charge un projet livré sous forme d'archive ZIP.
 *
 * <p>L'archive est extraite dans un répertoire temporaire, puis confiée au
 * {@link DirectoryProjectLoader}. Le répertoire est marqué pour suppression à l'arrêt de la
 * JVM : une archive évaluée ne laisse pas de trace.
 *
 * <p><b>Sécurité</b> — c'est le point d'entrée le plus exposé du projet, puisque l'archive
 * vient d'un tiers. Deux attaques sont bloquées dans {@link SafeFiles#extractZip} :
 * <ul>
 *   <li><i>zip slip</i> : une entrée nommée {@code ../../..} qui écrirait hors du répertoire
 *       temporaire ;
 *   <li><i>zip bomb</i> : quelques kilo-octets compressés qui en donnent des gigaoctets.
 * </ul>
 * L'extraction ne rend jamais un fichier exécutable et le contenu n'est jamais lancé.
 */
public final class ArchiveProjectLoader implements ProjectLoader {

    private final DirectoryProjectLoader directoryLoader;
    private final long maxTotalBytes;

    public ArchiveProjectLoader(DirectoryProjectLoader directoryLoader, long maxTotalBytes) {
        this.directoryLoader = Objects.requireNonNull(directoryLoader, "directoryLoader");
        this.maxTotalBytes = maxTotalBytes;
    }

    /** Chargeur avec les plafonds par défaut. */
    public static ArchiveProjectLoader standard() {
        return new ArchiveProjectLoader(DirectoryProjectLoader.standard(), SafeFiles.MAX_ARCHIVE_TOTAL_BYTES);
    }

    @Override
    public boolean supports(Path source) {
        if (!Files.isRegularFile(source)) {
            return false;
        }
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    @Override
    public ProjectSnapshot load(Path source, FileSelector selector) {
        if (!supports(source)) {
            throw new ProjectLoadException("Ce fichier n'est pas une archive ZIP : " + source);
        }
        try {
            Path extracted = Files.createTempDirectory("reviewer-ai-archive-");
            extracted.toFile().deleteOnExit();
            SafeFiles.extractZip(source, extracted, maxTotalBytes);

            Path root = singleRootOrSelf(extracted);
            return directoryLoader.load(root, selector, SourceKind.ARCHIVE, "");
        } catch (IOException e) {
            throw new ProjectLoadException("Archive illisible ou refusée : " + e.getMessage(), e);
        }
    }

    /**
     * Descend d'un cran si l'archive ne contient qu'un répertoire.
     *
     * <p>Une archive produite par « exporter le dépôt » range tout sous {@code mon-projet-1.0/}.
     * Sans ce détour, tous les chemins du rapport commenceraient par ce préfixe inutile, et les
     * règles d'inclusion écrites par l'utilisateur ne correspondraient à rien.
     */
    private static Path singleRootOrSelf(Path extracted) throws IOException {
        try (var entries = Files.list(extracted)) {
            var found = entries.toList();
            if (found.size() == 1 && Files.isDirectory(found.getFirst())) {
                return found.getFirst();
            }
            return extracted;
        }
    }
}
