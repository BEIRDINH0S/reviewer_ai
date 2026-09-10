package com.reviewerai.criteria;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;
import com.reviewerai.service.ProgressListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests des critères qui n'interrogent aucun modèle.
 *
 * <p>Ce sont eux qui garantissent qu'une évaluation produit un résultat même sans Ollama, et
 * ils sont exacts par construction : leurs notes doivent donc être vérifiables au chiffre près.
 */
class DeterministicCriteriaTest {

    private static ProjectSnapshot snapshot(List<ProjectFile> files) {
        var project = ProjectRef.of("demo", Path.of("/tmp/demo"), SourceKind.DIRECTORY);
        return new ProjectSnapshot(project, files);
    }

    private static ProjectFile java(String path, int lines) {
        return new ProjectFile(path, FileKind.JAVA_MAIN, lines * 30L, lines);
    }

    private static ProjectFile test(String path) {
        return new ProjectFile(path, FileKind.JAVA_TEST, 500, 20);
    }

    private static CriterionResult evaluate(Criterion criterion, ProjectSnapshot project) {
        return criterion.evaluate(project, ProgressListener.noop());
    }

    // --- présence de tests ---

    @Test
    @DisplayName("sans aucun test, la note de couverture est nulle")
    void noTestsScoresZero() {
        var result = evaluate(new TestPresenceCriterion(),
                snapshot(List.of(java("src/main/java/A.java", 50))));

        assertEquals(0, result.score());
        assertTrue(result.evaluated());
        assertFalse(result.weaknesses().isEmpty());
    }

    @Test
    @DisplayName("un test pour deux classes vaut la note maximale")
    void halfRatioScoresFullMark() {
        var result = evaluate(new TestPresenceCriterion(), snapshot(List.of(
                java("src/main/java/A.java", 50),
                java("src/main/java/B.java", 50),
                test("src/test/java/ATest.java"))));

        assertEquals(10, result.score());
    }

    @Test
    @DisplayName("sans classe de production, le critère se déclare non évaluable")
    void noProductionCodeMeansNotEvaluated() {
        var result = evaluate(new TestPresenceCriterion(), snapshot(List.of(test("t/ATest.java"))));

        assertFalse(result.evaluated());
    }

    // --- documentation ---

    @Test
    @DisplayName("un README à la racine rapporte des points")
    void readmeIsRewarded() {
        var withoutReadme = evaluate(new DocumentationCriterion(),
                snapshot(List.of(java("src/main/java/A.java", 10))));
        var withReadme = evaluate(new DocumentationCriterion(), snapshot(List.of(
                java("src/main/java/A.java", 10),
                new ProjectFile("README.md", FileKind.DOCUMENTATION, 100, 5))));

        assertTrue(withReadme.score() > withoutReadme.score());
    }

    @Test
    @DisplayName("un README dans un sous-répertoire ne compte pas comme celui de la racine")
    void nestedReadmeIsNotTheRootOne() {
        var result = evaluate(new DocumentationCriterion(), snapshot(List.of(
                java("src/main/java/A.java", 10),
                new ProjectFile("docs/README.md", FileKind.DOCUMENTATION, 100, 5))));

        assertTrue(result.weaknesses().stream().anyMatch(w -> w.contains("racine")));
    }

    // --- organisation du projet ---

    @Test
    @DisplayName("un projet bien rangé obtient une bonne note d'organisation")
    void wellOrganisedProjectScoresWell() {
        var result = evaluate(new ProjectStructureCriterion(), snapshot(List.of(
                java("src/main/java/com/x/A.java", 100),
                new ProjectFile("pom.xml", FileKind.BUILD, 800, 40),
                new ProjectFile("Dockerfile", FileKind.DOCKER, 300, 15))));

        assertEquals(10, result.score());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    @DisplayName("un fichier très long produit un signalement localisé")
    void oversizedFileIsReported() {
        var result = evaluate(new ProjectStructureCriterion(), snapshot(List.of(
                java("src/main/java/com/x/Enorme.java", 1200),
                new ProjectFile("pom.xml", FileKind.BUILD, 800, 40))));

        assertEquals(1, result.findings().size());
        assertEquals("src/main/java/com/x/Enorme.java", result.findings().getFirst().location().filePath());
        assertEquals(1.0, result.findings().getFirst().confidence(),
                "une mesure exacte n'est pas une estimation");
    }

    @Test
    @DisplayName("l'avancement des critères déterministes ne notifie aucun appel au modèle")
    void deterministicCriteriaDoNotCallTheModel() {
        List<String> calls = new ArrayList<>();
        ProgressListener spy = new ProgressListener() {
            @Override
            public void onLlmCall(String criterionLabel, int estimatedTokens) {
                calls.add(criterionLabel);
            }
        };

        new TestPresenceCriterion().evaluate(snapshot(List.of(java("src/main/java/A.java", 10))), spy);

        assertTrue(calls.isEmpty());
    }
}
