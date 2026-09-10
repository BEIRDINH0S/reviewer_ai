package com.reviewerai.criteria;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.DirectoryProjectLoader;
import com.reviewerai.project.FileSelector;
import com.reviewerai.service.ProgressListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du critère de duplication.
 *
 * <p>Chaque projet jouet est écrit dans un {@code @TempDir} puis chargé par le vrai chargeur :
 * le critère lit de vrais fichiers, comme en production.
 */
class DuplicationCriterionTest {

    /** Une méthode de plus de six lignes normalisées : assez pour remplir une fenêtre. */
    private static final String LONG_METHOD = """
              int compute(int n) {
                int total = 0;
                for (int i = 0; i < n; i++) {
                  total += i * 2;
                  total -= 1;
                }
                return total;
              }
            """;

    private final DuplicationCriterion criterion = new DuplicationCriterion(1_000_000L);

    private static void writeMain(Path repo, String simpleName, String body) throws IOException {
        Path dir = repo.resolve("src/main/java/demo");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(simpleName + ".java"),
                "class " + simpleName + " {\n" + body + "}\n");
    }

    private CriterionResult evaluate(Path repo) {
        ProjectSnapshot project = DirectoryProjectLoader.standard().load(repo, FileSelector.all());
        return criterion.evaluate(project, ProgressListener.noop());
    }

    @Test
    @DisplayName("deux méthodes identiques dans deux fichiers sont détectées")
    void identicalMethodsAreDetected(@TempDir Path repo) throws IOException {
        writeMain(repo, "A", LONG_METHOD);
        writeMain(repo, "B", LONG_METHOD);

        CriterionResult result = evaluate(repo);

        assertFalse(result.findings().isEmpty(), "le bloc dupliqué doit être signalé");
        assertTrue(result.score() < 10, "la duplication doit coûter des points");
    }

    @Test
    @DisplayName("deux méthodes qui ne diffèrent que par l'indentation ou les commentaires le sont aussi")
    void differencesInLayoutStillCount(@TempDir Path repo) throws IOException {
        writeMain(repo, "A", LONG_METHOD);
        // Même corps, réindenté et commenté : après normalisation, il redevient identique.
        String reindented = """
                    int compute(int n) {
                            // accumulateur
                            int total = 0;
                            for (int i = 0; i < n; i++) {
                                total += i * 2;  /* pas de un */
                                total -= 1;
                            }
                            return total;
                    }
                """;
        writeMain(repo, "B", reindented);

        assertFalse(evaluate(repo).findings().isEmpty(),
                "indentation et commentaires ne doivent pas masquer la duplication");
    }

    @Test
    @DisplayName("deux getters courts et banals ne déclenchent pas de faux positif")
    void shortGettersAreNotFlagged(@TempDir Path repo) throws IOException {
        writeMain(repo, "A", "  int value() { return 0; }\n");
        writeMain(repo, "B", "  int value() { return 0; }\n");

        CriterionResult result = evaluate(repo);

        assertTrue(result.findings().isEmpty(), "un bloc trop court ne doit pas être considéré comme dupliqué");
        assertEquals(10, result.score());
    }

    @Test
    @DisplayName("un projet sans duplication obtient la note maximale")
    void noDuplicationScoresFullMark(@TempDir Path repo) throws IOException {
        writeMain(repo, "A", LONG_METHOD);
        writeMain(repo, "B", """
                  String describe(String who) {
                    StringBuilder out = new StringBuilder();
                    out.append("bonjour ");
                    out.append(who);
                    out.append(" !");
                    out.append(System.lineSeparator());
                    return out.toString();
                  }
                """);

        CriterionResult result = evaluate(repo);

        assertTrue(result.findings().isEmpty());
        assertEquals(10, result.score());
        assertTrue(result.evaluated());
    }
}
