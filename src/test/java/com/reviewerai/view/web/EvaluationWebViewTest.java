package com.reviewerai.view.web;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests de l'état exposé au navigateur. */
class EvaluationWebViewTest {

    private static EvaluationConfig config() {
        return EvaluationConfig.builder().projectSource(Path.of("/tmp/demo")).build();
    }

    private static EvaluationResult emptyResult() {
        return EvaluationResult.empty(ProjectRef.of("demo", Path.of("/tmp/demo"), SourceKind.DIRECTORY));
    }

    @Test
    @DisplayName("au départ, aucune évaluation n'est en cours")
    void startsIdle() {
        var view = new EvaluationWebView();

        assertEquals(EvaluationState.Status.IDLE, view.currentState().status());
        assertFalse(view.currentState().isRunning());
    }

    @Test
    @DisplayName("une deuxième évaluation est refusée tant que la première tourne")
    void refusesConcurrentEvaluation() {
        var view = new EvaluationWebView();

        assertTrue(view.tryStart());
        assertFalse(view.tryStart());
    }

    @Test
    @DisplayName("une nouvelle évaluation est possible une fois la précédente terminée")
    void allowsRestartAfterCompletion() {
        var view = new EvaluationWebView();
        view.tryStart();
        view.onEvaluationFinished(emptyResult(), "# rapport");

        assertTrue(view.tryStart());
    }

    @Test
    @DisplayName("la progression par critère est reportée dans l'état")
    void progressIsRecorded() {
        var view = new EvaluationWebView();
        view.tryStart();
        var listener = view.asProgressListener();

        listener.onTotalCriteria(9);
        listener.onCriterionStarted("Architecture", 3, 9);

        assertEquals(2, view.currentState().current());
        assertEquals(9, view.currentState().total());
        assertEquals("Architecture", view.currentState().currentLabel());
    }

    @Test
    @DisplayName("un échec est visible avec son message")
    void failureIsRecorded() {
        var view = new EvaluationWebView();
        view.tryStart();

        view.onEvaluationFailed(new IllegalStateException("projet illisible"));

        assertEquals(EvaluationState.Status.FAILED, view.currentState().status());
        assertEquals("projet illisible", view.currentState().errorMessage());
    }

    @Test
    @DisplayName("une exception sans message reste affichable")
    void failureWithoutMessage() {
        var view = new EvaluationWebView();
        view.tryStart();

        view.onEvaluationFailed(new IllegalStateException());

        assertFalse(view.currentState().errorMessage().isBlank());
    }

    @Test
    @DisplayName("le démarrage efface les avertissements de l'évaluation précédente")
    void startClearsPreviousWarnings() {
        var view = new EvaluationWebView();
        view.tryStart();
        view.onWarning("ancien problème");
        view.onEvaluationFinished(emptyResult(), "");

        view.tryStart();

        assertTrue(view.currentState().warnings().isEmpty());
    }

    @Test
    @DisplayName("le nombre d'avertissements conservés est plafonné")
    void warningsAreCapped() {
        var view = new EvaluationWebView();
        view.tryStart();

        for (int i = 0; i < EvaluationState.MAX_WARNINGS + 20; i++) {
            view.onWarning("problème " + i);
        }

        List<String> warnings = view.currentState().warnings();
        assertEquals(EvaluationState.MAX_WARNINGS, warnings.size());
        assertTrue(warnings.getLast().endsWith(String.valueOf(EvaluationState.MAX_WARNINGS + 19)),
                "ce sont les plus récents qui sont conservés");
    }

    @Test
    @DisplayName("le début d'évaluation affiche le projet concerné")
    void stageMentionsProject() {
        var view = new EvaluationWebView();
        view.tryStart();

        view.onEvaluationStarted(config());

        assertTrue(view.currentState().stage().contains("/tmp/demo"));
    }
}
