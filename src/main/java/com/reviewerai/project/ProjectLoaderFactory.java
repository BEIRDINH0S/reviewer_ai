package com.reviewerai.project;

import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Choisit le chargeur adapté à la source désignée par l'utilisateur.
 *
 * <p><b>Patron de conception : Fabrique</b> (création)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>L'utilisateur donne un chemin. Il peut désigner un répertoire ordinaire, un dépôt git
 *       ou une archive. Comment obtenir le bon {@link ProjectLoader} sans que l'appelant ait à
 *       reconnaître lui-même la nature de la source ?</dd>
 *   <dt>Solution</dt>
 *   <dd>La fabrique interroge chaque chargeur par {@link ProjectLoader#supports} et renvoie le
 *       premier qui accepte. L'appelant ne nomme aucune classe concrète.</dd>
 *   <dt>Remarques</dt>
 *   <dd>L'ordre de la liste compte, et c'est délibéré : un dépôt git est aussi un répertoire,
 *       donc {@link GitProjectLoader} doit être interrogé avant
 *       {@link DirectoryProjectLoader}, faute de quoi on perdrait la révision. Le plus
 *       spécifique d'abord, le plus général en dernier.</dd>
 * </dl>
 *
 * <p>Ajouter une provenance — une URL de dépôt distant, un tar.gz — se fait en écrivant une
 * classe et en l'insérant dans cette liste. Aucune autre classe du projet ne change.
 */
public final class ProjectLoaderFactory {

    private final List<ProjectLoader> loaders;

    public ProjectLoaderFactory(List<ProjectLoader> loaders) {
        this.loaders = List.copyOf(Objects.requireNonNull(loaders, "loaders"));
    }

    /** Assemblage standard : git, puis archive, puis répertoire simple. */
    public static ProjectLoaderFactory standard() {
        return new ProjectLoaderFactory(List.of(
                GitProjectLoader.standard(),
                ArchiveProjectLoader.standard(),
                DirectoryProjectLoader.standard()));
    }

    /** Assemblage respectant une taille maximale de fichier imposée par la configuration. */
    public static ProjectLoaderFactory withMaxFileSize(long maxFileSizeBytes) {
        DirectoryProjectLoader directory = new DirectoryProjectLoader(maxFileSizeBytes);
        return new ProjectLoaderFactory(List.of(
                new GitProjectLoader(directory),
                new ArchiveProjectLoader(directory, com.reviewerai.util.SafeFiles.MAX_ARCHIVE_TOTAL_BYTES),
                directory));
    }

    /**
     * @param source la source désignée par l'utilisateur
     * @return le chargeur capable de la traiter
     * @throws ProjectLoader.ProjectLoadException si aucun chargeur ne la reconnaît
     */
    public ProjectLoader loaderFor(Path source) {
        Objects.requireNonNull(source, "source");
        return loaders.stream()
                .filter(loader -> loader.supports(source))
                .findFirst()
                .orElseThrow(() -> new ProjectLoader.ProjectLoadException(
                        "Source non reconnue : " + source
                                + " (attendu : un répertoire, un dépôt git ou une archive .zip)"));
    }

    /** Raccourci : choisit le chargeur et charge dans la foulée. */
    public ProjectSnapshot load(Path source, FileSelector selector) {
        return loaderFor(source).load(source, selector);
    }

    /** La provenance qui sera retenue pour cette source, sans la charger. */
    public SourceKind originOf(Path source) {
        ProjectLoader loader = loaderFor(source);
        if (loader instanceof GitProjectLoader) {
            return SourceKind.GIT;
        }
        return loader instanceof ArchiveProjectLoader ? SourceKind.ARCHIVE : SourceKind.DIRECTORY;
    }
}
