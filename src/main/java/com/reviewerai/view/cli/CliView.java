package com.reviewerai.view.cli;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.service.ProgressListener;
import com.reviewerai.view.EvaluationView;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Vue en ligne de commande : affiche l'avancement puis le tableau des notes.
 *
 * <p>Les flux de sortie sont injectés plutôt que codés en dur, pour qu'un test puisse vérifier
 * l'affichage sans capturer {@code System.out}.
 *
 * <p>Les avertissements partent sur la sortie d'erreur, jamais sur la sortie standard : cela
 * permet de rediriger le résultat vers un fichier tout en continuant à voir les incidents.
 */
public final class CliView implements EvaluationView {

    private final PrintStream out;
    private final PrintStream err;

    public CliView(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    /** Vue branchée sur les flux standard. */
    public static CliView standard() {
        return new CliView(System.out, System.err);
    }

    @Override
    public void onEvaluationStarted(EvaluationConfig config) {
        out.printf("Évaluation de %s%n", config.projectSource());
        out.printf("Configuration : %s%n%n", config.describe());
    }

    @Override
    public void onEvaluationFinished(EvaluationResult result, String renderedReport) {
        out.printf("%n%-38s %6s%n", "Critère", "Note");
        out.println("-".repeat(46));
        for (CriterionResult criterion : result.criteria()) {
            if (criterion.evaluated()) {
                out.printf("%-38s %3d/%-2d%n",
                        truncate(criterion.label()), criterion.score(), criterion.maxScore());
            } else {
                out.printf("%-38s %6s%n", truncate(criterion.label()), "n/a");
            }
        }
        out.println("-".repeat(46));
        out.printf("%-38s %4.1f/20%n", "Note globale", result.overallScore());
        out.printf("%n%d appel(s) au modèle (%s) en %d s%n",
                result.llmCalls(), result.modelName(), result.duration().toSeconds());
        if (result.isPartial()) {
            err.println("Attention : certains critères n'ont pas pu être évalués.");
        }
    }

    @Override
    public void onEvaluationFailed(Exception error) {
        err.println("Échec de l'évaluation : " + error.getMessage());
    }

    @Override
    public void onWarning(String message) {
        err.println("Avertissement : " + message);
    }

    @Override
    public ProgressListener asProgressListener() {
        return new ProgressListener() {
            @Override
            public void onStage(String stage) {
                out.println("→ " + stage);
            }

            @Override
            public void onProjectLoaded(ProjectSnapshot project) {
                out.printf("  %d fichier(s), %d lignes de Java%n",
                        project.files().size(), project.mainJavaLines());
            }

            @Override
            public void onCriterionStarted(String label, int index, int total) {
                out.printf("  [%d/%d] %s…%n", index, total, label);
            }

            @Override
            public void onCriterionFinished(CriterionResult result, int index, int total) {
                if (!result.evaluated()) {
                    err.printf("  [%d/%d] %s : non évalué%n", index, total, result.label());
                }
            }

            @Override
            public void onWarning(String message) {
                CliView.this.onWarning(message);
            }
        };
    }

    /** Coupe un libellé trop long pour que la colonne du tableau reste alignée. */
    private static String truncate(String label) {
        return label.length() <= 38 ? label : label.substring(0, 37) + "…";
    }
}
