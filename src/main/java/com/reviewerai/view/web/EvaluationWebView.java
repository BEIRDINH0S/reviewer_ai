package com.reviewerai.view.web;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.service.ProgressListener;
import com.reviewerai.view.EvaluationView;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Vue web : au lieu d'afficher, elle mémorise l'état pour que le navigateur vienne le demander.
 *
 * <p>C'est la différence avec la vue en ligne de commande, qui écrit au fil de l'eau. Ici
 * personne n'écoute au moment où l'évaluation progresse : le navigateur interroge
 * {@code /api/status} régulièrement et récupère le dernier état connu.
 *
 * <p>Deux fils d'exécution se croisent : celui qui évalue écrit l'état, ceux qui répondent aux
 * requêtes HTTP le lisent. L'état étant un {@link EvaluationState} immuable rangé dans un
 * {@link AtomicReference}, une lecture renvoie toujours un état cohérent — jamais un objet
 * à moitié modifié.
 */
public final class EvaluationWebView implements EvaluationView {

    private final AtomicReference<EvaluationState> state = new AtomicReference<>(EvaluationState.idle());

    /** L'état courant, à sérialiser vers le navigateur. */
    public EvaluationState currentState() {
        return state.get();
    }

    /**
     * Marque le début d'une évaluation, si aucune autre n'est en cours.
     *
     * @return {@code true} si l'évaluation peut démarrer, {@code false} s'il y en a déjà une
     */
    public boolean tryStart() {
        // compareAndSet plutôt qu'un test suivi d'une écriture : deux requêtes simultanées ne
        // doivent pas pouvoir lancer deux évaluations.
        EvaluationState current = state.get();
        return !current.isRunning() && state.compareAndSet(current, current.started());
    }

    @Override
    public void onEvaluationStarted(EvaluationConfig config) {
        state.updateAndGet(s -> s.withStage("Évaluation de " + config.projectSource()));
    }

    @Override
    public void onEvaluationFinished(EvaluationResult result, String renderedReport) {
        state.updateAndGet(s -> s.finished(result, renderedReport));
    }

    @Override
    public void onEvaluationFailed(Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        state.updateAndGet(s -> s.failed(message));
    }

    @Override
    public void onWarning(String message) {
        state.updateAndGet(s -> s.withWarning(message));
    }

    @Override
    public ProgressListener asProgressListener() {
        return new ProgressListener() {
            @Override
            public void onStage(String stage) {
                state.updateAndGet(s -> s.withStage(stage));
            }

            @Override
            public void onProjectLoaded(ProjectSnapshot project) {
                state.updateAndGet(s -> s.withStage(
                        "Projet chargé : %d fichier(s)".formatted(project.files().size())));
            }

            @Override
            public void onTotalCriteria(int total) {
                state.updateAndGet(s -> s.withTotal(total));
            }

            @Override
            public void onCriterionStarted(String label, int index, int total) {
                state.updateAndGet(s -> s.withProgress(index - 1, total, label));
            }

            @Override
            public void onCriterionFinished(CriterionResult result, int index, int total) {
                state.updateAndGet(s -> s.withProgress(index, total, result.label()));
            }

            @Override
            public void onWarning(String message) {
                EvaluationWebView.this.onWarning(message);
            }
        };
    }
}
