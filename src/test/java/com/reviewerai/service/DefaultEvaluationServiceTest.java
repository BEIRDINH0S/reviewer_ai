package com.reviewerai.service;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.Criterion;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.criteria.CriterionRegistry;
import com.reviewerai.history.AnalysisHistory;
import com.reviewerai.history.HistoryEntry;
import com.reviewerai.history.InMemoryAnalysisHistory;
import com.reviewerai.llm.CountingLlmProvider;
import com.reviewerai.llm.LlmRequest;
import com.reviewerai.llm.StubLlmProvider;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.FileSelector;
import com.reviewerai.project.ProjectLoader;
import com.reviewerai.project.ProjectLoaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de l'orchestration d'une évaluation.
 *
 * <p>Toutes les briques sont remplacées par des doublures : ces tests vérifient l'enchaînement
 * et la gestion des erreurs, pas la qualité de l'évaluation. Ils tournent en quelques
 * millisecondes, sans modèle ni projet préexistant — le projet de test est créé dans un
 * {@code @TempDir}.
 */
class DefaultEvaluationServiceTest {

    @TempDir
    Path tempDir;

    private Path projectRoot;

    @BeforeEach
    void createProject() throws IOException {
        projectRoot = tempDir.resolve("projet-demo");
        Files.createDirectories(projectRoot.resolve("src/main/java/com/x"));
        Files.writeString(projectRoot.resolve("src/main/java/com/x/A.java"), "class A {\n  void run() {}\n}\n");
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>\n");
    }

    // --- doublures ---

    /** Critère qui rend toujours la même note. */
    private record FixedCriterion(String id, int score) implements Criterion {
        @Override
        public CriterionDescriptor descriptor() {
            return CriterionDescriptor.deterministic(id, "Critère " + id, "");
        }

        @Override
        public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
            return new CriterionResult(id, "Critère " + id, score, 10, "appréciation",
                    List.of(), List.of(), List.of(), List.of(), true);
        }
    }

    /** Critère qui échoue sans se rattraper lui-même : le pire cas pour l'orchestration. */
    private record ThrowingCriterion(String id) implements Criterion {
        @Override
        public CriterionDescriptor descriptor() {
            return CriterionDescriptor.deterministic(id, "Critère " + id, "");
        }

        @Override
        public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
            throw new IllegalStateException("panne interne du critère");
        }
    }

    /** Critère qui viole son contrat en ne renvoyant rien. */
    private record NullReturningCriterion(String id) implements Criterion {
        @Override
        public CriterionDescriptor descriptor() {
            return CriterionDescriptor.deterministic(id, "Critère " + id, "");
        }

        @Override
        public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
            return null;
        }
    }

    /** Critère qui interroge le modèle, pour vérifier le comptage des appels. */
    private record CallingCriterion(String id, CountingLlmProvider llm) implements Criterion {
        @Override
        public CriterionDescriptor descriptor() {
            return CriterionDescriptor.llm(id, "Critère " + id, "");
        }

        @Override
        public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
            llm.ask(new LlmRequest("système", "utilisateur", 100));
            return new CriterionResult(id, "Critère " + id, 7, 10, "",
                    List.of(), List.of(), List.of(), List.of(), true);
        }
    }

    /** Historique en panne, pour vérifier qu'il n'emporte pas l'évaluation. */
    private static final class FailingHistory implements AnalysisHistory {
        @Override
        public String record(EvaluationResult result) {
            throw new IllegalStateException("disque plein");
        }

        @Override
        public List<HistoryEntry> list() {
            return List.of();
        }

        @Override
        public Optional<EvaluationResult> find(String id) {
            return Optional.empty();
        }
    }

    /** Écoute et note tout ce qu'on lui envoie. */
    private static final class RecordingListener implements ProgressListener {
        final List<String> started = new ArrayList<>();
        final List<String> finished = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        int announcedTotal;

        @Override
        public void onTotalCriteria(int total) {
            announcedTotal = total;
        }

        @Override
        public void onCriterionStarted(String label, int index, int total) {
            started.add(index + "/" + total + " " + label);
        }

        @Override
        public void onCriterionFinished(CriterionResult result, int index, int total) {
            finished.add(index + "/" + total + " " + result.label());
        }

        @Override
        public void onWarning(String message) {
            warnings.add(message);
        }
    }

    // --- assemblage ---

    private EvaluationConfig config() {
        return EvaluationConfig.builder().projectSource(projectRoot).build();
    }

    private EvaluationService service(CriterionRegistry registry) {
        return service(registry, new InMemoryAnalysisHistory(), offlineCounter(), config());
    }

    private EvaluationService service(CriterionRegistry registry,
                                      AnalysisHistory history,
                                      CountingLlmProvider llm,
                                      EvaluationConfig config) {
        return new DefaultEvaluationService(
                config, ProjectLoaderFactory.standard(), FileSelector.all(), registry, history, llm);
    }

    private static CountingLlmProvider offlineCounter() {
        return CountingLlmProvider.silent(StubLlmProvider.silent());
    }

    /**
     * Doublure qui se présente comme un vrai fournisseur.
     *
     * <p>{@link StubLlmProvider} se déclare hors ligne, et le rapport ne compte alors aucun
     * appel — c'est voulu. Pour vérifier que le comptage fonctionne, il faut donc une doublure
     * qui réponde comme un modèle joignable.
     */
    private static CountingLlmProvider liveCounter() {
        return CountingLlmProvider.silent(new com.reviewerai.llm.LlmProvider() {
            @Override
            public com.reviewerai.llm.LlmResponse ask(LlmRequest request) {
                return new com.reviewerai.llm.LlmResponse("réponse", "modele-test",
                        java.time.Duration.ZERO);
            }

            @Override
            public String modelName() {
                return "modele-test";
            }
        });
    }

    // --- échecs fatals ---

    @Test
    @DisplayName("un projet introuvable fait échouer l'évaluation")
    void missingProjectIsFatal() {
        var config = EvaluationConfig.builder().projectSource(tempDir.resolve("absent")).build();
        var service = service(CriterionRegistry.of(new FixedCriterion("a", 8)),
                new InMemoryAnalysisHistory(), offlineCounter(), config);

        assertThrows(ProjectLoader.ProjectLoadException.class,
                () -> service.evaluate(ProgressListener.noop()));
    }

    @Test
    @DisplayName("un catalogue sans critère fait échouer l'évaluation")
    void emptyCatalogueIsFatal() {
        var service = service(CriterionRegistry.of());

        var exception = assertThrows(IllegalStateException.class,
                () -> service.evaluate(ProgressListener.noop()));
        assertTrue(exception.getMessage().contains("Aucun critère"));
    }

    @Test
    @DisplayName("un critère demandé mais inconnu est refusé")
    void unknownCriterionIsRejected() {
        var config = EvaluationConfig.builder()
                .projectSource(projectRoot).criterionIds(List.of("inexistant")).build();
        var service = service(CriterionRegistry.of(new FixedCriterion("a", 8)),
                new InMemoryAnalysisHistory(), offlineCounter(), config);

        assertThrows(IllegalArgumentException.class, () -> service.evaluate(ProgressListener.noop()));
    }

    // --- déroulé nominal ---

    @Test
    @DisplayName("tous les critères du catalogue sont évalués, dans l'ordre")
    void everyCriterionIsEvaluatedInOrder() {
        var service = service(CriterionRegistry.of(
                new FixedCriterion("a", 8), new FixedCriterion("b", 6), new FixedCriterion("c", 10)));

        var result = service.evaluate(ProgressListener.noop());

        assertEquals(List.of("a", "b", "c"),
                result.criteria().stream().map(CriterionResult::criterionId).toList());
    }

    @Test
    @DisplayName("seuls les critères demandés sont évalués")
    void onlyRequestedCriteriaAreEvaluated() {
        var config = EvaluationConfig.builder()
                .projectSource(projectRoot).criterionIds(List.of("c", "a")).build();
        var service = service(
                CriterionRegistry.of(new FixedCriterion("a", 8), new FixedCriterion("b", 6),
                        new FixedCriterion("c", 10)),
                new InMemoryAnalysisHistory(), offlineCounter(), config);

        var result = service.evaluate(ProgressListener.noop());

        assertEquals(List.of("a", "c"),
                result.criteria().stream().map(CriterionResult::criterionId).toList());
    }

    @Test
    @DisplayName("le résultat porte le projet et la configuration utilisés")
    void resultCarriesProjectAndConfiguration() {
        var service = service(CriterionRegistry.of(new FixedCriterion("a", 8)));

        var result = service.evaluate(ProgressListener.noop());

        assertEquals("projet-demo", result.project().name());
        assertFalse(result.configLabel().isBlank());
        assertFalse(result.duration().isNegative());
    }

    // --- récupération partielle ---

    @Test
    @DisplayName("un critère qui échoue n'empêche pas les suivants d'être évalués")
    void oneFailingCriterionDoesNotStopTheRun() {
        var service = service(CriterionRegistry.of(
                new FixedCriterion("a", 8), new ThrowingCriterion("b"), new FixedCriterion("c", 10)));

        var result = service.evaluate(ProgressListener.noop());

        assertEquals(3, result.criteria().size());
        assertEquals(2, result.evaluatedCriteria().size());
    }

    @Test
    @DisplayName("le critère en échec figure dans le résultat, marqué non évalué")
    void failingCriterionAppearsAsNotEvaluated() {
        var service = service(CriterionRegistry.of(new ThrowingCriterion("b")));

        var result = service.evaluate(ProgressListener.noop());
        CriterionResult failed = result.criteria().getFirst();

        assertFalse(failed.evaluated(), "le faire disparaître laisserait croire qu'il a été jugé");
        assertTrue(failed.summary().contains("panne interne du critère"));
        assertTrue(result.isPartial());
    }

    @Test
    @DisplayName("un critère qui ne renvoie rien est traité comme un échec")
    void nullResultIsTreatedAsFailure() {
        var service = service(CriterionRegistry.of(new NullReturningCriterion("b")));

        var result = service.evaluate(ProgressListener.noop());

        assertFalse(result.criteria().getFirst().evaluated());
    }

    @Test
    @DisplayName("un critère non évalué ne pèse pas dans la note globale")
    void failedCriterionDoesNotLowerTheOverallScore() {
        var withFailure = service(CriterionRegistry.of(
                new FixedCriterion("a", 10), new ThrowingCriterion("b")))
                .evaluate(ProgressListener.noop());
        var withoutFailure = service(CriterionRegistry.of(new FixedCriterion("a", 10)))
                .evaluate(ProgressListener.noop());

        assertEquals(withoutFailure.overallScore(), withFailure.overallScore(),
                "une panne technique ne doit pas se transformer en mauvaise note");
    }

    @Test
    @DisplayName("l'abandon d'un critère est signalé et conservé dans le résultat")
    void failureIsWarnedAndKept() {
        var listener = new RecordingListener();
        var service = service(CriterionRegistry.of(new ThrowingCriterion("b")));

        var result = service.evaluate(listener);

        assertEquals(1, listener.warnings.size());
        assertTrue(listener.warnings.getFirst().contains("Critère « Critère b » abandonné"));
        assertEquals(listener.warnings, result.warnings(),
                "la vue les affiche au fil de l'eau, le rapport les veut tous à la fin");
    }

    // --- avancement ---

    @Test
    @DisplayName("l'avancement est notifié une fois par critère, dans l'ordre")
    void progressIsReportedOncePerCriterion() {
        var listener = new RecordingListener();
        var service = service(CriterionRegistry.of(
                new FixedCriterion("a", 8), new FixedCriterion("b", 6)));

        service.evaluate(listener);

        assertEquals(2, listener.announcedTotal);
        assertEquals(List.of("1/2 Critère a", "2/2 Critère b"), listener.started);
        assertEquals(List.of("1/2 Critère a", "2/2 Critère b"), listener.finished);
    }

    @Test
    @DisplayName("un critère en échec est quand même notifié comme terminé")
    void failingCriterionIsStillReportedAsFinished() {
        var listener = new RecordingListener();
        var service = service(CriterionRegistry.of(new ThrowingCriterion("b")));

        service.evaluate(listener);

        assertEquals(1, listener.finished.size(), "sinon la barre de progression resterait bloquée");
    }

    // --- traçabilité ---

    @Test
    @DisplayName("le nombre d'appels au modèle est celui qu'a compté le décorateur")
    void llmCallsAreCounted() {
        var counter = liveCounter();
        var service = service(
                CriterionRegistry.of(new CallingCriterion("a", counter), new CallingCriterion("b", counter)),
                new InMemoryAnalysisHistory(), counter, config());

        var result = service.evaluate(ProgressListener.noop());

        assertEquals(2, result.llmCalls());
    }

    @Test
    @DisplayName("deux évaluations successives ne cumulent pas leurs appels")
    void callCountIsPerEvaluation() {
        var counter = liveCounter();
        var service = service(CriterionRegistry.of(new CallingCriterion("a", counter)),
                new InMemoryAnalysisHistory(), counter, config());

        service.evaluate(ProgressListener.noop());
        var second = service.evaluate(ProgressListener.noop());

        assertEquals(1, second.llmCalls(), "le compteur du fournisseur survit à une évaluation");
    }

    @Test
    @DisplayName("sans modèle interrogé, le rapport indique « hors ligne »")
    void offlineEvaluationSaysSo() {
        var service = service(CriterionRegistry.of(new FixedCriterion("a", 8)));

        assertEquals("hors ligne", service.evaluate(ProgressListener.noop()).modelName());
    }

    @Test
    @DisplayName("hors ligne, aucun appel au modèle n'est reporté")
    void offlineEvaluationReportsNoCall() {
        var counter = CountingLlmProvider.silent(StubLlmProvider.silent());
        var service = service(CriterionRegistry.of(new CallingCriterion("a", counter)),
                new InMemoryAnalysisHistory(), counter, config());

        var result = service.evaluate(ProgressListener.noop());

        assertEquals(0, result.llmCalls(),
                "sinon le rapport annoncerait des appels vers un modèle qu'il dit hors ligne");
    }

    // --- historique ---

    @Test
    @DisplayName("le résultat est conservé dans l'historique")
    void resultIsRecorded() {
        var history = new InMemoryAnalysisHistory();
        var service = service(CriterionRegistry.of(new FixedCriterion("a", 8)),
                history, offlineCounter(), config());

        service.evaluate(ProgressListener.noop());

        assertEquals(1, history.size());
        assertEquals("projet-demo", history.list().getFirst().projectName());
    }

    @Test
    @DisplayName("un historique en panne n'emporte pas une évaluation qui a abouti")
    void brokenHistoryDoesNotLoseTheResult() {
        var listener = new RecordingListener();
        var service = service(CriterionRegistry.of(new FixedCriterion("a", 8)),
                new FailingHistory(), offlineCounter(), config());

        var result = service.evaluate(listener);

        assertEquals(1, result.evaluatedCriteria().size());
        assertTrue(listener.warnings.getFirst().contains("historique"));
    }
}
