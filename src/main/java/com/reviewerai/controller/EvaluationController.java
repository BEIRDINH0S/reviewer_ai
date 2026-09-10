package com.reviewerai.controller;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.report.PdfCompiler;
import com.reviewerai.report.ReportWriter;
import com.reviewerai.service.EvaluationService;
import com.reviewerai.service.ProgressListener;
import com.reviewerai.view.EvaluationView;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Contrôleur MVC : reçoit une demande de la vue, appelle le service, lui renvoie le résultat.
 *
 * <p>Il ne contient aucune logique d'évaluation et n'affiche rien lui-même. Les deux vues —
 * ligne de commande et navigateur — utilisent ce même contrôleur, sans une ligne de code
 * dupliquée.
 *
 * <p>Sa seule responsabilité propre est l'écriture du rapport sur disque, parce que c'est une
 * décision d'application et non de métier : le service produit un résultat, le format et la
 * destination du document ne le regardent pas.
 */
public final class EvaluationController {

    private final EvaluationConfig config;
    private final EvaluationService service;
    private final ReportWriter reportWriter;
    private final PdfCompiler pdfCompiler;

    /**
     * @param config       les réglages de l'analyse
     * @param service      la façade métier
     * @param reportWriter le format de rapport à produire
     * @param pdfCompiler  la compilation en PDF ; {@link PdfCompiler#none()} pour ne pas compiler
     */
    public EvaluationController(EvaluationConfig config,
                                EvaluationService service,
                                ReportWriter reportWriter,
                                PdfCompiler pdfCompiler) {
        this.config = Objects.requireNonNull(config, "config");
        this.service = Objects.requireNonNull(service, "service");
        this.reportWriter = Objects.requireNonNull(reportWriter, "reportWriter");
        this.pdfCompiler = Objects.requireNonNull(pdfCompiler, "pdfCompiler");
    }

    /** Contrôleur sans compilation PDF, le cas courant. */
    public EvaluationController(EvaluationConfig config, EvaluationService service, ReportWriter reportWriter) {
        this(config, service, reportWriter, PdfCompiler.none());
    }

    /**
     * Lance l'évaluation et met la vue à jour au fur et à mesure.
     *
     * <p>Bloque jusqu'à la fin. Le serveur web l'appelle donc depuis un fil dédié : une requête
     * HTTP qui durerait plusieurs minutes serait abandonnée par le navigateur bien avant.
     *
     * @param view la vue à notifier
     */
    public void runEvaluation(EvaluationView view) {
        Objects.requireNonNull(view, "view");
        try {
            view.onEvaluationStarted(config);
            EvaluationResult result = service.evaluate(view.asProgressListener());
            String report = reportWriter.render(result);
            writeReport(report, view);
            view.onEvaluationFinished(result, report);
        } catch (RuntimeException e) {
            view.onEvaluationFailed(e);
        }
    }

    /** Évaluation sans vue, pour les tests et un usage en intégration continue. */
    public EvaluationResult runHeadless() {
        return service.evaluate(ProgressListener.noop());
    }

    /**
     * Écrit le rapport, puis tente la compilation PDF si elle est configurée.
     *
     * <p>Un échec d'écriture n'interrompt pas l'analyse : le rapport reste affichable dans la
     * vue, et perdre le fichier vaut mieux que perdre le travail des dernières minutes.
     */
    private void writeReport(String report, EvaluationView view) {
        try {
            Files.writeString(config.reportFile(), report);
        } catch (IOException e) {
            view.onWarning("Rapport non écrit sur disque : " + e.getMessage());
            return;
        }
        if ("tex".equals(reportWriter.fileExtension())) {
            pdfCompiler.compile(config.reportFile())
                    .ifPresent(pdf -> view.onWarning("PDF produit : " + pdf));
        }
    }
}
