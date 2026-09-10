package com.reviewerai.history;

import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests de l'historique en mémoire. */
class InMemoryAnalysisHistoryTest {

    private static EvaluationResult result(String projectName, Instant when) {
        var project = ProjectRef.of(projectName, Path.of("/tmp/" + projectName), SourceKind.DIRECTORY);
        return new EvaluationResult(project, when, "modele-test", "config",
                List.of(), Duration.ofSeconds(3), 4, List.of());
    }

    @Test
    @DisplayName("une analyse enregistrée se retrouve par son identifiant")
    void recordedAnalysisIsRetrievable() {
        var history = new InMemoryAnalysisHistory();

        String id = history.record(result("demo", Instant.now()));

        assertTrue(history.find(id).isPresent());
        assertEquals("demo", history.find(id).orElseThrow().project().name());
    }

    @Test
    @DisplayName("la liste va de la plus récente à la plus ancienne")
    void listIsMostRecentFirst() {
        var history = new InMemoryAnalysisHistory();
        history.record(result("ancienne", Instant.parse("2026-01-01T10:00:00Z")));
        history.record(result("récente", Instant.parse("2026-09-01T10:00:00Z")));

        assertEquals("récente", history.list().getFirst().projectName());
    }

    @Test
    @DisplayName("un identifiant inconnu ne renvoie rien plutôt que null")
    void unknownIdReturnsEmpty() {
        assertTrue(new InMemoryAnalysisHistory().find("inexistant").isEmpty());
    }

    @Test
    @DisplayName("l'historique nul n'enregistre rien et ne casse rien")
    void noneHistoryIsSafe() {
        var history = AnalysisHistory.none();

        assertEquals("", history.record(result("demo", Instant.now())));
        assertTrue(history.list().isEmpty());
        assertTrue(history.find("x").isEmpty());
    }

    @Test
    @DisplayName("le résumé reprend la note et le modèle")
    void entrySummarisesTheResult() {
        var history = new InMemoryAnalysisHistory();
        String id = history.record(result("demo", Instant.now()));

        HistoryEntry entry = history.list().getFirst();

        assertEquals(id, entry.id());
        assertEquals("modele-test", entry.modelName());
    }
}
