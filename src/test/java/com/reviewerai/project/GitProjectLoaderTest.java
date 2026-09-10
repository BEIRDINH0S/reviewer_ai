package com.reviewerai.project;

import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du chargement d'un dépôt git à une révision donnée.
 *
 * <p>Chaque test crée son dépôt dans un {@code @TempDir} avec l'API JGit : aucun dépôt
 * préexistant, aucun accès réseau, aucune commande git externe.
 */
class GitProjectLoaderTest {

    private final GitProjectLoader loader = GitProjectLoader.standard();

    /** Crée un fichier, l'ajoute à l'index et le valide, en fixant une identité de test. */
    private static RevCommit commit(Git git, Path root, String relative, String content)
            throws IOException, GitAPIException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent() == null ? root : file.getParent());
        Files.writeString(file, content);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(relative)
                .setAuthor("Test", "test@exemple.invalide")
                .setCommitter("Test", "test@exemple.invalide")
                .call();
    }

    @Test
    @DisplayName("charger à une révision ancienne renvoie l'état de cette révision, pas HEAD")
    void loadsHistoricalRevision(@TempDir Path dir) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            RevCommit first = commit(git, dir, "A.java", "class A {}\n");
            commit(git, dir, "B.java", "class B {}\n");

            ProjectSnapshot snapshot = loader.loadAtRevision(dir, FileSelector.all(), first.getName());

            assertTrue(snapshot.byPath("A.java").isPresent(), "A.java existait à cette révision");
            assertTrue(snapshot.byPath("B.java").isEmpty(), "B.java n'a été ajouté qu'au commit suivant");
            assertEquals(first.getName(), snapshot.project().revision());
            assertEquals(SourceKind.GIT, snapshot.project().origin());
        }
    }

    @Test
    @DisplayName("le répertoire de travail n'est pas modifié par le chargement")
    void leavesWorkingTreeUntouched(@TempDir Path dir) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            RevCommit first = commit(git, dir, "A.java", "class A {}\n");
            commit(git, dir, "B.java", "class B {}\n");

            loader.loadAtRevision(dir, FileSelector.all(), first.getName());

            // Charger une révision ancienne ne doit pas défaire le fichier ajouté ensuite.
            assertTrue(Files.exists(dir.resolve("B.java")),
                    "le répertoire de travail doit rester à HEAD, pas revenir à la révision chargée");
        }
    }

    @Test
    @DisplayName("les chemins renvoyés utilisent des barres obliques, même sous Windows")
    void pathsUseForwardSlashes(@TempDir Path dir) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            RevCommit head = commit(git, dir, "src/main/java/com/x/A.java", "class A {}\n");

            ProjectSnapshot snapshot = loader.loadAtRevision(dir, FileSelector.all(), head.getName());

            assertTrue(snapshot.byPath("src/main/java/com/x/A.java").isPresent(),
                    "JGit renvoie des chemins en '/' : un antislash casserait la correspondance");
        }
    }

    @Test
    @DisplayName("une révision inexistante donne un message clair")
    void unknownRevisionIsRejected(@TempDir Path dir) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            commit(git, dir, "A.java", "class A {}\n");

            var exception = assertThrows(ProjectLoader.ProjectLoadException.class,
                    () -> loader.loadAtRevision(dir, FileSelector.all(),
                            "0123456789abcdef0123456789abcdef01234567"));

            assertTrue(exception.getMessage().contains("introuvable"),
                    "le message doit dire clairement que la révision n'existe pas");
        }
    }

    @Test
    @DisplayName("un dépôt sans commit charge quand même, avec une révision vide")
    void emptyRepositoryLoadsWithoutRevision(@TempDir Path dir) throws Exception {
        try (Git ignored = Git.init().setDirectory(dir.toFile()).call()) {
            ProjectSnapshot snapshot = loader.loadAtRevision(dir, FileSelector.all(), "HEAD");

            assertTrue(snapshot.project().revision().isEmpty(), "aucun commit : aucune révision");
            assertTrue(snapshot.isEmpty(), "aucun commit : aucun fichier à inventorier");
        }
    }

    @Test
    @DisplayName("un répertoire qui n'est pas un dépôt git est refusé")
    void nonGitDirectoryIsRejected(@TempDir Path dir) {
        assertThrows(ProjectLoader.ProjectLoadException.class,
                () -> loader.loadAtRevision(dir, FileSelector.all(), "HEAD"));
    }

    @Test
    @DisplayName("les règles de sélection s'appliquent aussi au chargement d'une révision")
    void selectorIsApplied(@TempDir Path dir) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            commit(git, dir, "A.java", "class A {}\n");
            RevCommit head = commit(git, dir, "notes.md", "# notes\n");

            ProjectSnapshot snapshot = loader.loadAtRevision(dir,
                    KindFileSelector.only(com.reviewerai.model.FileKind.JAVA_MAIN), head.getName());

            assertTrue(snapshot.byPath("A.java").isPresent());
            assertFalse(snapshot.byPath("notes.md").isPresent(), "la documentation est exclue par le sélecteur");
        }
    }
}
