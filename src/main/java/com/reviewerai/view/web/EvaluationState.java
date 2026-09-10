package com.reviewerai.view.web;

import com.reviewerai.model.EvaluationResult;

import java.util.List;

/**
 * L'état d'une évaluation à un instant donné, tel que le navigateur le voit.
 *
 * <p>Immuable : chaque changement produit un nouvel objet, échangé d'un coup dans
 * {@link EvaluationWebView}. Le fil qui évalue écrit, le fil qui répond aux requêtes lit, et
 * aucun des deux ne peut observer un état à moitié modifié.
 *
 * @param status        où en est l'évaluation
 * @param stage         étape en cours, affichée telle quelle
 * @param current       nombre de critères déjà évalués
 * @param total         nombre total de critères, {@code 0} tant qu'il est inconnu
 * @param currentLabel  libellé du critère en cours
 * @param warnings      incidents non bloquants, du plus ancien au plus récent
 * @param result        le résultat, présent uniquement quand {@code status} vaut {@code DONE}
 * @param report        le rapport rendu, présent en même temps que {@code result}
 * @param errorMessage  message d'erreur, présent uniquement quand {@code status} vaut {@code FAILED}
 */
public record EvaluationState(
        Status status,
        String stage,
        int current,
        int total,
        String currentLabel,
        List<String> warnings,
        EvaluationResult result,
        String report,
        String errorMessage) {

    /** Un projet problématique peut produire des centaines d'avertissements. */
    static final int MAX_WARNINGS = 50;

    public EvaluationState {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        stage = stage == null ? "" : stage;
        currentLabel = currentLabel == null ? "" : currentLabel;
        report = report == null ? "" : report;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public enum Status {
        /** Aucune évaluation n'a encore été lancée. */
        IDLE,
        /** Une évaluation est en cours. */
        RUNNING,
        /** L'évaluation est terminée, le résultat est disponible. */
        DONE,
        /** L'évaluation a échoué. */
        FAILED
    }

    /** État initial, avant toute évaluation. */
    public static EvaluationState idle() {
        return new EvaluationState(Status.IDLE, "", 0, 0, "", List.of(), null, "", "");
    }

    /** L'évaluation démarre : on repart d'un état propre, sans les avertissements précédents. */
    public EvaluationState started() {
        return new EvaluationState(Status.RUNNING, "Démarrage", 0, 0, "", List.of(), null, "", "");
    }

    public EvaluationState withStage(String newStage) {
        return new EvaluationState(status, newStage, current, total, currentLabel,
                warnings, result, report, errorMessage);
    }

    public EvaluationState withTotal(int newTotal) {
        return new EvaluationState(status, stage, current, newTotal, currentLabel,
                warnings, result, report, errorMessage);
    }

    public EvaluationState withProgress(int newCurrent, int newTotal, String label) {
        return new EvaluationState(status, stage, newCurrent, newTotal, label,
                warnings, result, report, errorMessage);
    }

    /** Ajoute un avertissement, en ne gardant que les plus récents. */
    public EvaluationState withWarning(String message) {
        List<String> updated = new java.util.ArrayList<>(warnings);
        updated.add(message);
        // Inutile de tous les garder en mémoire, et encore moins de tous les envoyer au
        // navigateur à chaque interrogation.
        int excess = updated.size() - MAX_WARNINGS;
        if (excess > 0) {
            updated = updated.subList(excess, updated.size());
        }
        return new EvaluationState(status, stage, current, total, currentLabel,
                updated, result, report, errorMessage);
    }

    public EvaluationState finished(EvaluationResult newResult, String newReport) {
        return new EvaluationState(Status.DONE, "Terminé", current, total, "",
                warnings, newResult, newReport, "");
    }

    public EvaluationState failed(String message) {
        return new EvaluationState(Status.FAILED, "Échec", current, total, "",
                warnings, null, "", message);
    }

    /** Vrai si une évaluation tourne actuellement. */
    public boolean isRunning() {
        return status == Status.RUNNING;
    }
}
