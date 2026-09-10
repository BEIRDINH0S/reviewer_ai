package com.reviewerai.project;

import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Charge un projet depuis un dépôt git déjà cloné sur le disque.
 *
 * <p>Provenance optionnelle au sens du sujet, mais celle qui apporte le plus : elle donne au
 * rapport une révision précise, donc une analyse reproductible. Deux évaluations du même
 * commit sont comparables ; deux évaluations d'un répertoire qui bouge ne le sont pas.
 *
 * <p>Deux modes. {@link #load} dresse l'inventaire du répertoire de travail, avec la révision
 * de {@code HEAD} notée dans le {@code ProjectRef}. {@link #loadAtRevision} lit l'arbre d'une
 * révision <b>donnée</b> via un {@code TreeWalk}, sans toucher au répertoire de travail : c'est
 * ce qui rend une analyse reproductible et sert de socle à la comparaison de deux versions.
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
     * Charge le projet tel qu'il était à une révision donnée, sans toucher au répertoire de travail.
     *
     * <p>L'arbre du commit est parcouru par un {@code TreeWalk} et le contenu lu par un
     * {@code ObjectLoader} : aucune extraction sur disque, aucun {@code checkout}. Le répertoire
     * de travail reste donc intact, et deux évaluations d'un même commit sont comparables là où
     * deux évaluations d'un répertoire qui bouge ne le seraient pas.
     *
     * <p>Un dépôt sans le moindre commit ne fait pas échouer le chargement — c'était le cas de ce
     * dépôt-ci le premier jour : on renvoie un inventaire vide, avec une révision vide. On perd la
     * reproductibilité, pas l'analyse.
     *
     * @param revision une révision au sens de git : {@code HEAD}, un nom de branche, un tag, un
     *                 SHA complet ou abrégé
     * @throws ProjectLoadException si la source n'est pas un dépôt git, ou si la révision demandée
     *                              est introuvable dans un dépôt qui a pourtant des commits
     */
    public ProjectSnapshot loadAtRevision(Path source, FileSelector selector, String revision) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(revision, "revision");
        if (!supports(source)) {
            throw new ProjectLoadException("Ce répertoire n'est pas un dépôt git : " + source);
        }
        Path root = source.toAbsolutePath().normalize();
        try (Repository repository = openRepository(root)) {
            ObjectId commitId = repository.resolve(revision);
            if (commitId == null) {
                if (repository.resolve(Constants.HEAD) == null) {
                    // Dépôt sans commit : aucune révision à trouver, on rend un inventaire vide.
                    return ProjectSnapshot.empty(new ProjectRef(nameOf(root), root, SourceKind.GIT, ""));
                }
                throw new ProjectLoadException("Révision introuvable dans le dépôt : " + revision);
            }
            return snapshotAt(repository, root, commitId, selector);
        } catch (MissingObjectException | IncorrectObjectTypeException e) {
            // Un SHA complet mais absent : resolve le rend sans vérifier, l'échec vient au parse.
            throw new ProjectLoadException("Révision introuvable dans le dépôt : " + revision, e);
        } catch (RevisionSyntaxException e) {
            throw new ProjectLoadException("Révision mal formée : " + revision, e);
        } catch (IOException e) {
            throw new ProjectLoadException("Dépôt git illisible : " + source, e);
        }
    }

    /**
     * Dresse l'inventaire des fichiers présents dans l'arbre d'un commit.
     *
     * <p>Chaque entrée vient d'un blob git, jamais du disque : les chemins sont ceux de git,
     * déjà séparés par des {@code /}, et le contenu est lu en mémoire par l'{@code ObjectLoader}.
     * Les fichiers trop gros sont ignorés, exactement comme au chargement d'un répertoire.
     */
    private ProjectSnapshot snapshotAt(Repository repository, Path root, ObjectId commitId,
                                       FileSelector selector) throws IOException {
        long maxSize = directoryLoader.maxFileSizeBytes();
        List<ProjectFile> files = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    ObjectLoader blob = repository.open(treeWalk.getObjectId(0), Constants.OBJ_BLOB);
                    long size = blob.getSize();
                    if (size > maxSize) {
                        continue;
                    }
                    FileKind kind = FileClassifier.classify(path);
                    int lines = kind.isTextual() ? countLines(blob) : 0;
                    ProjectFile file = new ProjectFile(path, kind, size, lines);
                    if (selector.accepts(file)) {
                        files.add(file);
                    }
                }
            }
            return new ProjectSnapshot(new ProjectRef(nameOf(root), root, SourceKind.GIT, commit.getName()), files);
        }
    }

    /**
     * Compte les lignes d'un blob texte.
     *
     * <p>Le blob est déjà borné en taille par l'appelant, donc {@code getBytes} ne peut pas faire
     * exploser la mémoire. Un contenu non-UTF-8 donne des caractères de remplacement plutôt
     * qu'une exception : le compte de lignes reste bon.
     */
    private static int countLines(ObjectLoader blob) throws IOException {
        String text = new String(blob.getBytes(), StandardCharsets.UTF_8);
        return text.isEmpty() ? 0 : (int) text.lines().count();
    }

    private static String nameOf(Path root) {
        Path fileName = root.getFileName();
        return fileName == null ? root.toString() : fileName.toString();
    }

    /**
     * La révision de {@code HEAD}, ou une chaîne vide si le dépôt n'a pas encore de commit.
     *
     * <p>Un dépôt fraîchement initialisé est un cas réel — c'est celui de ce projet au premier
     * jour. Il ne doit pas faire échouer le chargement : on perd la reproductibilité, pas
     * l'analyse.
     */
    private static String resolveHead(Path source) {
        try (Repository repository = openRepository(source.toAbsolutePath().normalize())) {
            ObjectId head = repository.resolve(Constants.HEAD);
            return head == null ? "" : head.getName();
        } catch (IOException e) {
            // Dépôt illisible : l'inventaire des fichiers reste possible, on continue sans révision.
            return "";
        }
    }

    private static Repository openRepository(Path root) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(root.resolve(".git").toFile())
                .readEnvironment()
                .build();
    }
}
