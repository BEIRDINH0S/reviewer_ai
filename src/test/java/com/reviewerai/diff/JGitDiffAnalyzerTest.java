package com.reviewerai.diff;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.ChangedMethod;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la brique Diff.
 *
 * <p>Chaque dépôt est créé dans un {@code @TempDir} avec l'API JGit : aucun dépôt figé, chaque
 * test part d'un état propre, aucune commande git externe.
 */
class JGitDiffAnalyzerTest {

    /** Classe à deux méthodes ; seul le corps de {@code compute} varie d'un test à l'autre. */
    private static String classA(String computeBody) {
        return "class A {\n"
                + "    int compute(int n) {\n"
                + computeBody
                + "        return n;\n"
                + "    }\n"
                + "    int untouched() {\n"
                + "        return 42;\n"
                + "    }\n"
                + "}\n";
    }

    private static final String BASE_BODY = "        int total = 0;\n        total += n;\n";
    private static final String CHANGED_BODY = "        int total = 1000;\n        total += n * 3;\n";

    private JGitDiffAnalyzer analyzer(Path repo) {
        return new JGitDiffAnalyzer(EvaluationConfig.builder().projectSource(repo).build());
    }

    private static RevCommit commit(Git git, Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(relative)
                .setAuthor("Test", "test@exemple.invalide")
                .setCommitter("Test", "test@exemple.invalide")
                .call();
    }

    private static boolean mentions(List<ChangedMethod> changed, String method) {
        return changed.stream().anyMatch(c -> c.method().signature().contains("#" + method + "("));
    }

    @Test
    @DisplayName("une méthode dont le corps change est signalée comme modifiée")
    void detectsModifiedMethod(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            RevCommit base = commit(git, repo, "src/A.java", classA(BASE_BODY));
            RevCommit head = commit(git, repo, "src/A.java", classA(CHANGED_BODY));

            List<ChangedMethod> changed = analyzer(repo).findChangedMethods(repo, base.getName(), head.getName());

            assertTrue(mentions(changed, "compute"), "compute a changé de corps");
            ChangedMethod compute = changed.stream()
                    .filter(c -> c.method().signature().contains("#compute(")).findFirst().orElseThrow();
            assertEquals(ChangedMethod.ChangeType.MODIFIED, compute.changeType());
            assertFalse(compute.changedLines().isEmpty(), "les lignes modifiées doivent être connues");
        }
    }

    @Test
    @DisplayName("une méthode ajoutée est signalée comme ajoutée")
    void detectsAddedMethod(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            RevCommit base = commit(git, repo, "src/A.java", classA(BASE_BODY));
            RevCommit head = commit(git, repo, "src/New.java",
                    "class New {\n  void created() {\n    int x = 1;\n    x = x + 1;\n  }\n}\n");

            List<ChangedMethod> changed = analyzer(repo).findChangedMethods(repo, base.getName(), head.getName());

            ChangedMethod created = changed.stream()
                    .filter(c -> c.method().signature().contains("#created(")).findFirst().orElseThrow();
            assertEquals(ChangedMethod.ChangeType.ADDED, created.changeType());
        }
    }

    @Test
    @DisplayName("une méthode non touchée du même fichier n'est pas signalée")
    void ignoresUntouchedMethodInSameFile(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            RevCommit base = commit(git, repo, "src/A.java", classA(BASE_BODY));
            RevCommit head = commit(git, repo, "src/A.java", classA(CHANGED_BODY));

            List<ChangedMethod> changed = analyzer(repo).findChangedMethods(repo, base.getName(), head.getName());

            assertFalse(mentions(changed, "untouched"), "untouched n'a pas bougé, elle ne doit pas ressortir");
        }
    }

    @Test
    @DisplayName("une méthode supprimée n'apparaît pas : il n'y a plus de code à évaluer")
    void deletedMethodIsNotReported(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            RevCommit base = commit(git, repo, "src/A.java", classA(BASE_BODY));
            RevCommit head = commit(git, repo, "src/A.java",
                    "class A {\n    int untouched() {\n        return 42;\n    }\n}\n");

            List<ChangedMethod> changed = analyzer(repo).findChangedMethods(repo, base.getName(), head.getName());

            assertFalse(mentions(changed, "compute"), "compute a disparu de HEAD, il n'y a rien à en dire");
        }
    }

    @Test
    @DisplayName("un fichier qui ne compile pas ne fait pas échouer l'analyse")
    void malformedFileDoesNotBreakAnalysis(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            RevCommit base = commit(git, repo, "src/A.java", classA(BASE_BODY));
            Files.writeString(repo.resolve("src/A.java"), classA(CHANGED_BODY));
            Files.writeString(repo.resolve("src/Casse.java"), "ceci n'est pas du Java {{{ (((");
            git.add().addFilepattern(".").call();
            RevCommit head = git.commit().setMessage("mix")
                    .setAuthor("T", "t@e").setCommitter("T", "t@e").call();

            List<ChangedMethod> changed = analyzer(repo).findChangedMethods(repo, base.getName(), head.getName());

            assertTrue(mentions(changed, "compute"), "le fichier valide est analysé malgré le fichier cassé");
        }
    }

    @Test
    @DisplayName("les fichiers qui ne sont pas du Java sont ignorés")
    void ignoresNonJavaFiles(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            commit(git, repo, "src/A.java", classA(BASE_BODY));
            RevCommit base = commit(git, repo, "notes.txt", "version 1\n");
            RevCommit head = commit(git, repo, "notes.txt", "version 2, tout a changé\n");

            List<ChangedMethod> changed = analyzer(repo).findChangedMethods(repo, base.getName(), head.getName());

            assertTrue(changed.isEmpty(), "un fichier texte n'apporte aucune méthode");
        }
    }

    @Test
    @DisplayName("deux commits identiques ne donnent aucune méthode modifiée")
    void identicalCommitsYieldNothing(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            RevCommit only = commit(git, repo, "src/A.java", classA(BASE_BODY));

            assertTrue(analyzer(repo).findChangedMethods(repo, only.getName(), only.getName()).isEmpty());
        }
    }

    @Test
    @DisplayName("un renommage de fichier ne produit pas un faux « tout a changé »")
    void renameDoesNotFlagEverything(@TempDir Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            RevCommit base = commit(git, repo, "src/A.java", classA(BASE_BODY));

            git.rm().addFilepattern("src/A.java").call(); // retire A du disque et de l'index
            Files.createDirectories(repo.resolve("src")); // git rm a pu retirer le répertoire vidé
            Files.writeString(repo.resolve("src/B.java"), classA(BASE_BODY)); // même contenu, autre nom
            git.add().addFilepattern(".").call();
            RevCommit head = git.commit().setMessage("rename")
                    .setAuthor("T", "t@e").setCommitter("T", "t@e").call();

            List<ChangedMethod> changed = analyzer(repo).findChangedMethods(repo, base.getName(), head.getName());

            assertTrue(changed.isEmpty(), "un simple renommage sans changement de corps ne modifie aucune méthode");
        }
    }
}
