package com.reviewerai.project;

import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Charge un projet depuis un dépôt git déjà cloné sur le disque.
 *
 * <p>Provenance optionnelle au sens du sujet, mais celle qui apporte le plus : elle donne au
 * rapport une révision précise, donc une analyse reproductible. Deux évaluations du même
 * commit sont comparables ; deux évaluations d'un répertoire qui bouge ne le sont pas.
 *
 * <p>L'inventaire est celui du répertoire de travail, avec la révision courante notée dans le
 * {@code ProjectRef}. Lire l'arbre d'un commit arbitraire sans toucher au répertoire de
 * travail demande un {@code TreeWalk} : c'est le prolongement naturel de la brique Diff, et
 * c'est laissé à la personne qui en a la charge.
 *
 * <p><b>Sécurité</b> : on ouvre le dépôt en lecture, on ne lance aucune commande git, aucun
 * hook, aucun script du dépôt. JGit lit les fichiers lui-même, sans passer par un shell.
 */
public final class GitProjectLoader implements ProjectLoader {

    private final DirectoryProjectLoader directoryLoader;

    public GitProjectLoader(DirectoryProjectLoader directoryLoader) {
        this.directoryLoader = Objects.requireNonNull(directoryLoader, "directoryLoader");
    }

    /** Chargeur avec les réglages par défaut. */
    public static GitProjectLoader standard() {
        return new GitProjectLoader(DirectoryProjectLoader.standard());
    }

    @Override
    public boolean supports(Path source) {
        return Files.isDirectory(source) && Files.isDirectory(source.resolve(".git"));
    }

    @Override
    public ProjectSnapshot load(Path source, FileSelector selector) {
        if (!supports(source)) {
            throw new ProjectLoadException("Ce répertoire n'est pas un dépôt git : " + source);
        }
        return directoryLoader.load(source, selector, SourceKind.GIT, resolveHead(source));
    }

    /**
     * La révision de {@code HEAD}, ou une chaîne vide si le dépôt n'a pas encore de commit.
     *
     * <p>Un dépôt fraîchement initialisé est un cas réel — c'est celui de ce projet au premier
     * jour. Il ne doit pas faire échouer le chargement : on perd la reproductibilité, pas
     * l'analyse.
     */
    private static String resolveHead(Path source) {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(source.resolve(".git").toFile())
                .readEnvironment()
                .build()) {
            ObjectId head = repository.resolve("HEAD");
            return head == null ? "" : head.getName();
        } catch (IOException e) {
            // Dépôt illisible : l'inventaire des fichiers reste possible, on continue sans révision.
            return "";
        }
    }
}
