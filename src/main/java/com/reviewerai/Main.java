package com.reviewerai;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.config.ServerConfig;
import com.reviewerai.controller.EvaluationController;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.history.AnalysisHistory;
import com.reviewerai.history.JsonFileAnalysisHistory;
import com.reviewerai.llm.StubLlmProvider;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.CompositeFileSelector;
import com.reviewerai.project.FileSelector;
import com.reviewerai.project.GlobFileSelector;
import com.reviewerai.project.ProjectLoaderFactory;
import com.reviewerai.report.LatexReportWriter;
import com.reviewerai.report.MarkdownReportWriter;
import com.reviewerai.report.ReportWriter;
import com.reviewerai.service.EvaluationService;
import com.reviewerai.service.EvaluationServiceFactory;
import com.reviewerai.view.cli.CliArguments;
import com.reviewerai.view.cli.CliView;
import com.reviewerai.view.web.WebServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Point d'entrée de l'application.
 *
 * <p>Rôle unique : lire les arguments, assembler les objets, choisir la vue. Toute la logique
 * est ailleurs. C'est, avec {@code EvaluationServiceFactory}, le seul endroit du projet où des
 * classes concrètes sont nommées.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || CliArguments.wantsHelp(args)) {
            System.out.println(CliArguments.usage());
            System.exit(args.length == 0 ? 1 : 0);
        }

        try {
            if (CliArguments.wantsCriteriaList(args)) {
                printCriteria();
            } else if (CliArguments.wantsServe(args)) {
                runServer(args);
            } else {
                runOnce(args);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(CliArguments.usage());
            System.exit(2);
        }
    }

    /** Affiche les critères disponibles, pour que l'utilisateur sache quoi passer à {@code --criteria}. */
    private static void printCriteria() {
        System.out.println("Critères disponibles :");
        for (CriterionDescriptor descriptor : availableCriteria()) {
            System.out.printf("  %-18s %-38s /%d %s%n",
                    descriptor.id(), descriptor.label(), descriptor.maxScore(),
                    descriptor.usesLlm() ? "(modèle)" : "(déterministe)");
        }
    }

    /** Mode serveur : les paramètres de l'évaluation viennent du formulaire. */
    private static void runServer(String[] args) throws IOException {
        ServerConfig serverConfig = CliArguments.parseServer(args);
        Path reportFile = CliArguments.reportFile(args);
        boolean offline = CliArguments.isOffline(args);
        boolean markdown = CliArguments.wantsMarkdown(args);

        // Un historique partagé : le service y consigne chaque analyse, les routes /api/history
        // le relisent. Sur disque, il survit au redémarrage du serveur.
        AnalysisHistory history = new JsonFileAnalysisHistory(Path.of("historique"));

        var server = new WebServer(
                serverConfig,
                reportFile,
                availableCriteria(),
                Main::loadProject,
                config -> newController(config, offline, markdown, history),
                history);
        server.start();

        System.out.println("Interface disponible sur " + serverConfig.url());
        System.out.println("Ctrl+C pour arrêter.");

        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        awaitShutdown();
    }

    /** Mode ligne de commande : une évaluation, puis on sort. */
    private static void runOnce(String[] args) {
        EvaluationConfig config = CliArguments.parse(args);
        EvaluationController controller =
                newController(config, CliArguments.isOffline(args), CliArguments.wantsMarkdown(args));
        controller.runEvaluation(CliView.standard());
    }

    /**
     * Assemble un contrôleur.
     *
     * <p>Le mode hors ligne remplace le fournisseur de modèle par une doublure : le pipeline
     * complet tourne, les critères déterministes produisent de vraies notes, et les critères
     * confiés au modèle apparaissent comme non évalués.
     */
    private static EvaluationController newController(EvaluationConfig config,
                                                      boolean offline,
                                                      boolean markdown) {
        EvaluationService service = offline
                ? EvaluationServiceFactory.create(config, StubLlmProvider.silent(), System.err::println)
                : EvaluationServiceFactory.create(config);
        ReportWriter writer = markdown ? new MarkdownReportWriter() : new LatexReportWriter();
        return new EvaluationController(config, service, writer);
    }

    /**
     * Comme {@link #newController}, mais en consignant dans un historique partagé.
     *
     * <p>C'est ce qui permet au serveur web de relire, via {@code /api/history}, les analyses que
     * ce contrôleur vient d'écrire.
     */
    private static EvaluationController newController(EvaluationConfig config, boolean offline,
                                                      boolean markdown, AnalysisHistory history) {
        EvaluationService service = offline
                ? EvaluationServiceFactory.create(config, StubLlmProvider.silent(), System.err::println, history)
                : EvaluationServiceFactory.create(config, history);
        ReportWriter writer = markdown ? new MarkdownReportWriter() : new LatexReportWriter();
        return new EvaluationController(config, service, writer);
    }

    /** Charge un projet pour en afficher l'arborescence, sans lancer d'évaluation. */
    private static ProjectSnapshot loadProject(Path source) {
        FileSelector selector = CompositeFileSelector.of(
                FileSelector.textualOnly(),
                GlobFileSelector.of(List.of(), List.of()));
        return ProjectLoaderFactory.standard().load(source, selector);
    }

    /**
     * Les critères proposés par l'application.
     *
     * <p>Construits avec une doublure de modèle : on ne veut ici que leurs cartes d'identité,
     * pas leur capacité à évaluer. Cela évite d'exiger qu'un modèle tourne pour afficher la
     * liste des critères.
     */
    private static List<CriterionDescriptor> availableCriteria() {
        EvaluationConfig probe = EvaluationConfig.builder().projectSource(Path.of(".")).build();
        EvaluationService service =
                EvaluationServiceFactory.create(probe, StubLlmProvider.silent(), message -> { });
        return ((com.reviewerai.service.DefaultEvaluationService) service).selectedCriteria().stream()
                .map(com.reviewerai.criteria.Criterion::descriptor)
                .toList();
    }

    /**
     * Bloque jusqu'à l'arrêt du programme.
     *
     * <p>Le serveur travaille dans ses propres fils d'exécution ; il ne reste plus rien à faire
     * ici, mais rendre la main afficherait une invite de commande alors que le serveur tourne
     * toujours.
     */
    private static void awaitShutdown() {
        try {
            new java.util.concurrent.CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
