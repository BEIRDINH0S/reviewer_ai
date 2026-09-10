package com.reviewerai.history;

import com.reviewerai.model.CodeLocation;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.Severity;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de l'historique sur disque.
 *
 * <p>Chaque test travaille dans un {@code @TempDir} : rien n'est écrit hors du répertoire de
 * test, et deux exécutions ne se marchent pas dessus.
 */
class JsonFileAnalysisHistoryTest {

    private static EvaluationResult result(String projectName, Instant when) {
        var project = ProjectRef.of(projectName, Path.of("/tmp/" + projectName), SourceKind.DIRECTORY);
        var finding = new Finding(new CodeLocation("src/A.java", "A#run", 42),
                Severity.HIGH, "Ressource non fermée", "Le flux reste ouvert.", 0.8);
        var evaluated = new CriterionResult("arch", "Architecture", 8, 10, "Découpage clair.",
                List.of("bonne séparation"), List.of("un couplage"), List.of("extraire une interface"),
                List.of(finding), true);
        var failed = CriterionResult.failed("solid", "SOLID", 10, "délai dépassé");
        return new EvaluationResult(project, when, "modele-test", "budget 6000",
                List.of(evaluated, failed), Duration.ofSeconds(3), 4, List.of("Fichier ignoré : x"));
    }

    @Test
    @DisplayName("une analyse enregistrée se relit après redémarrage")
    void recordedAnalysisSurvivesRestart(@TempDir Path dir) {
        String id = new JsonFileAnalysisHistory(dir).record(result("demo", Instant.now()));

        // Une nouvelle instance sur le même répertoire simule un redémarrage du programme.
        EvaluationResult reloaded = new JsonFileAnalysisHistory(dir).find(id).orElseThrow();

        assertEquals("demo", reloaded.project().name());
        assertEquals("modele-test", reloaded.modelName());
        assertEquals(2, reloaded.criteria().size());
        assertEquals(8, reloaded.criteria().getFirst().score());
        assertEquals("Ressource non fermée", reloaded.criteria().getFirst().findings().getFirst().title());
        assertEquals(Severity.HIGH, reloaded.criteria().getFirst().findings().getFirst().severity());
        assertFalse(reloaded.criteria().get(1).evaluated(), "le critère en échec doit rester non évalué");
        assertEquals(List.of("Fichier ignoré : x"), reloaded.warnings());
    }

    @Test
    @DisplayName("la liste va de la plus récente à la plus ancienne")
    void listIsMostRecentFirst(@TempDir Path dir) {
        var history = new JsonFileAnalysisHistory(dir);
        history.record(result("ancienne", Instant.parse("2026-01-01T10:00:00Z")));
        history.record(result("récente", Instant.parse("2026-09-01T10:00:00Z")));

        List<HistoryEntry> entries = history.list();

        assertEquals(2, entries.size());
        assertEquals("récente", entries.getFirst().projectName());
        assertEquals("modele-test", entries.getFirst().modelName());
    }

    @Test
    @DisplayName("le répertoire est créé au premier enregistrement s'il n'existe pas")
    void directoryIsCreatedOnFirstRecord(@TempDir Path dir) {
        Path missing = dir.resolve("pas/encore/la");
        assertFalse(Files.exists(missing));

        new JsonFileAnalysisHistory(missing).record(result("demo", Instant.now()));

        assertTrue(Files.isDirectory(missing));
    }

    @Test
    @DisplayName("un fichier corrompu est ignoré, il ne fait pas échouer la liste")
    void corruptedFileIsIgnored(@TempDir Path dir) throws IOException {
        var history = new JsonFileAnalysisHistory(dir);
        history.record(result("bonne", Instant.now()));
        Files.writeString(dir.resolve("20260101T000000000.json"), "{ ceci n'est pas du json");

        List<HistoryEntry> entries = history.list();

        assertEquals(1, entries.size(), "seule l'analyse valide doit apparaître");
        assertTrue(history.find("20260101T000000000").isEmpty(), "un fichier corrompu se lit comme absent");
    }

    @Test
    @DisplayName("un identifiant inconnu ne renvoie rien plutôt que null")
    void unknownIdReturnsEmpty(@TempDir Path dir) {
        assertTrue(new JsonFileAnalysisHistory(dir).find("inexistant").isEmpty());
    }

    @Test
    @DisplayName("un identifiant qui tente de sortir du répertoire est refusé")
    void unsafeIdIsRejected(@TempDir Path dir) {
        var history = new JsonFileAnalysisHistory(dir);

        assertTrue(history.find("../secret").isEmpty(), "un identifiant ne doit jamais servir à remonter l'arborescence");
        assertTrue(history.find("a/b").isEmpty());
    }

    @Test
    @DisplayName("la liste est vide, sans erreur, quand le répertoire n'existe pas encore")
    void listIsEmptyWhenDirectoryAbsent(@TempDir Path dir) {
        assertTrue(new JsonFileAnalysisHistory(dir.resolve("jamais-créé")).list().isEmpty());
    }

    @Test
    @DisplayName("le fichier écrit ne contient aucun contenu de fichier source")
    void noSourceCodeIsStored(@TempDir Path dir) throws IOException {
        String id = new JsonFileAnalysisHistory(dir).record(result("demo", Instant.now()));

        String onDisk = Files.readString(dir.resolve(id + ".json"));

        // On garde des notes, des durées et des compteurs — jamais le code d'autrui. Un
        // EvaluationResult n'en porte pas ; ce test verrouille cette garantie.
        assertFalse(onDisk.contains("class "), "aucune source Java ne doit se retrouver dans l'historique");
        assertFalse(onDisk.contains("password"), "aucun secret ne doit être enregistré");
        assertTrue(onDisk.contains("modele-test"), "les métadonnées de traçabilité, elles, sont bien là");
    }
}
