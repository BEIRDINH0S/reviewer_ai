package com.reviewerai.llm;

import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.Severity;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verrouille le couplage entre le prompt et le parseur.
 *
 * <p>Le prompt {@code system.md} et {@link JsonCriterionResponseParser} partagent un format JSON
 * qui n'est écrit nulle part en dur : renommer un champ dans {@code system.md} sans toucher au
 * parseur casserait l'analyse en silence — aucune compilation ne rougirait, mais les critères
 * reviendraient non évalués.
 *
 * <p>Ce test prend l'exemple de réponse <b>tel qu'il figure dans {@code system.md}</b> (lu depuis
 * le classpath, jamais recopié ici — un exemple recopié divergerait au premier changement) et
 * vérifie que le parseur l'accepte et en lit les bonnes valeurs. Si un nom de champ change dans
 * le prompt, ce test échoue : c'est précisément ce qu'on veut. <b>Vérifié à la main une fois</b>
 * en renommant {@code "score"} : le test rougit.
 */
class PromptParserCouplingTest {

    /** Le fichier cité par le {@code finding} de l'exemple, pour qu'il survive à la validation. */
    private static final String EXAMPLE_FILE = "src/main/java/com/exemple/A.java";

    @Test
    @DisplayName("l'exemple JSON de system.md est accepté par le parseur, champ pour champ")
    void promptExampleMatchesParser() throws IOException {
        String jsonBlock = jsonExampleFromSystemPrompt();

        ProjectSnapshot project = new ProjectSnapshot(
                ProjectRef.of("demo", Path.of("/tmp/demo"), SourceKind.DIRECTORY),
                List.of(new ProjectFile(EXAMPLE_FILE, FileKind.JAVA_MAIN, 1000, 100)));
        CriterionDescriptor descriptor =
                CriterionDescriptor.llm("architecture", "Architecture", "juge l'architecture");

        CriterionResult result =
                new JsonCriterionResponseParser().parse(jsonBlock, descriptor, project);

        assertEquals(7, result.score(), "le champ score doit être lu");
        assertEquals("appréciation en une ou deux phrases", result.summary());
        assertEquals(List.of("point fort appuyé sur un fichier précis"), result.strengths());
        assertEquals(List.of("point faible appuyé sur un fichier précis"), result.weaknesses());
        assertEquals(List.of("action concrète à mener"), result.recommendations());

        assertEquals(1, result.findings().size(), "le finding de l'exemple doit être lu et conservé");
        Finding finding = result.findings().get(0);
        assertEquals(EXAMPLE_FILE, finding.location().filePath());
        assertEquals(42, finding.location().line());
        assertEquals(Severity.HIGH, finding.severity());
        assertEquals(0.8, finding.confidence());
    }

    /**
     * Extrait le bloc JSON du prompt système, lu depuis le classpath.
     *
     * <p>{@code system.md} contient exactement une barrière de code triple : le texte entre la
     * première et la seconde est l'exemple de réponse attendu du modèle.
     */
    private static String jsonExampleFromSystemPrompt() throws IOException {
        String prompt;
        try (InputStream in = PromptParserCouplingTest.class.getResourceAsStream("/prompts/system.md")) {
            assertNotNull(in, "system.md doit être présent sur le classpath");
            prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String[] parts = prompt.split("```", -1);
        assertTrue(parts.length >= 3, "system.md doit contenir une barrière de code encadrant l'exemple");
        String block = parts[1].trim();
        assertFalse(block.isEmpty(), "le bloc d'exemple ne doit pas être vide");
        assertTrue(block.contains("{"), "le bloc encadré doit être l'exemple JSON");
        return block;
    }
}
