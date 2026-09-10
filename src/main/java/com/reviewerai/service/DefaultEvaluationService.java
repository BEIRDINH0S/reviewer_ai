package com.reviewerai.service;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.Criterion;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.criteria.CriterionRegistry;
import com.reviewerai.history.AnalysisHistory;
import com.reviewerai.llm.CountingLlmProvider;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.FileSelector;
import com.reviewerai.project.ProjectLoaderFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestre une évaluation complète.
 *
 * <p>Cette classe ne contient aucune logique d'analyse : elle enchaîne des collaborateurs
 * reçus par son constructeur. Chacun d'eux est une interface, donc le service se teste
 * entièrement avec des doublures, sans projet réel ni modèle (inversion des dépendances).
 *
 * <p>Déroulé attendu :
 * <ol>
 *   <li>charger le projet via {@code loaderFactory}, en appliquant les règles d'inclusion et
 *       d'exclusion de la configuration ; notifier {@code onProjectLoaded} ;
 *   <li>sélectionner les critères demandés dans le {@code registry} ; notifier
 *       {@code onTotalCriteria} ;
 *   <li>évaluer chaque critère l'un après l'autre, en notifiant avant et après ;
 *   <li>agréger les résultats dans un {@link EvaluationResult}, avec la durée totale, le nom
 *       du modèle, le nombre d'appels lu sur {@code llmCounter} et les avertissements ;
 *   <li>enregistrer le résultat dans {@code history}.
 * </ol>
 *
 * <p><b>Robustesse</b> — c'est le point que le sujet appelle « récupération partielle ». Une
 * exception levée sur UN critère est signalée via {@link ProgressListener#onWarning} et
 * l'évaluation continue avec le suivant. Perdre un critère est acceptable ; perdre le rapport
 * entier ne l'est pas. Les critères eux-mêmes rattrapent déjà leurs échecs et renvoient un
 * {@code CriterionResult.failed} ; ce niveau-ci est le filet de sécurité.
 *
 * <p>Seuls deux échecs sont fatals, et pour la même raison — il n'y a plus rien à évaluer :
 * un projet introuvable, et une liste de critères vide.
 */
public final class DefaultEvaluationService implements EvaluationService {

    private final EvaluationConfig config;
    private final ProjectLoaderFactory loaderFactory;
    private final FileSelector fileSelector;
    private final CriterionRegistry registry;
    private final AnalysisHistory history;
    private final CountingLlmProvider llmCounter;

    /**
     * @param config        les réglages de l'analyse
     * @param loaderFactory choisit le chargeur adapté à la source
     * @param fileSelector  les règles d'inclusion et d'exclusion appliquées au chargement
     * @param registry      le catalogue des critères disponibles
     * @param history       où consigner le résultat ; {@link AnalysisHistory#none()} pour ne rien garder
     * @param llmCounter    le décorateur qui compte les appels au modèle, pour le rapport
     */
    public DefaultEvaluationService(EvaluationConfig config,
                                    ProjectLoaderFactory loaderFactory,
                                    FileSelector fileSelector,
                                    CriterionRegistry registry,
                                    AnalysisHistory history,
                                    CountingLlmProvider llmCounter) {
        this.config = Objects.requireNonNull(config, "config");
        this.loaderFactory = Objects.requireNonNull(loaderFactory, "loaderFactory");
        this.fileSelector = Objects.requireNonNull(fileSelector, "fileSelector");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.history = Objects.requireNonNull(history, "history");
        this.llmCounter = Objects.requireNonNull(llmCounter, "llmCounter");
    }

    @Override
    public EvaluationResult evaluate(ProgressListener listener) {
        Objects.requireNonNull(listener, "listener");

        // Le compteur d'appels appartient au fournisseur, qui peut survivre à une évaluation.
        // On mesure donc un écart plutôt qu'un total, pour que deux évaluations successives ne
        // s'additionnent pas dans le rapport.
        int callsBefore = llmCounter.callCount();
        long startedAt = System.nanoTime();

        WarningCollector collector = new WarningCollector(listener);
        ProjectSnapshot project = loadProject(collector);
        List<Criterion> criteria = selectCriteria();
        collector.onTotalCriteria(criteria.size());

        List<CriterionResult> results = evaluateAll(project, criteria, collector);
        collector.onStage("Rédaction du rapport");

        EvaluationResult result = new EvaluationResult(
                project.project(),
                Instant.now(),
                modelLabel(),
                config.describe(),
                results,
                Duration.ofNanos(System.nanoTime() - startedAt),
                llmCallCount(callsBefore),
                collector.warnings());

        record(result, collector);
        return result;
    }

    /** Les critères qui seront évalués, sans lancer l'analyse. Utilisé par l'interface. */
    public List<Criterion> selectedCriteria() {
        return registry.select(config.criterionIds());
    }

    /**
     * Charge le projet et en dresse l'inventaire.
     *
     * <p>Échec fatal assumé : sans projet, il n'y a rien à évaluer, et produire un rapport vide
     * serait plus trompeur qu'utile. L'exception du chargeur remonte telle quelle, son message
     * étant déjà destiné à l'utilisateur.
     */
    private ProjectSnapshot loadProject(ProgressListener listener) {
        listener.onStage("Chargement du projet");
        ProjectSnapshot project = loaderFactory.load(config.projectSource(), fileSelector);
        listener.onProjectLoaded(project);
        return project;
    }

    /**
     * Les critères demandés par la configuration.
     *
     * <p>Second échec fatal, pour la même raison que le premier. Un identifiant inconnu est
     * refusé par le catalogue : mieux vaut s'arrêter que produire un rapport auquel il manque
     * silencieusement un critère que l'utilisateur croyait avoir demandé.
     */
    private List<Criterion> selectCriteria() {
        List<Criterion> criteria = registry.select(config.criterionIds());
        if (criteria.isEmpty()) {
            throw new IllegalStateException("Aucun critère à évaluer : le catalogue est vide");
        }
        return criteria;
    }

    /** Évalue les critères l'un après l'autre, en notifiant avant et après chacun. */
    private List<CriterionResult> evaluateAll(ProjectSnapshot project,
                                              List<Criterion> criteria,
                                              ProgressListener listener) {
        listener.onStage("Évaluation des critères");
        int total = criteria.size();
        List<CriterionResult> results = new ArrayList<>(total);

        for (int index = 0; index < total; index++) {
            Criterion criterion = criteria.get(index);
            int position = index + 1;

            listener.onCriterionStarted(criterion.descriptor().label(), position, total);
            CriterionResult result = evaluateOne(criterion, project, listener);
            results.add(result);
            listener.onCriterionFinished(result, position, total);
        }
        return List.copyOf(results);
    }

    /**
     * Évalue un critère, en garantissant qu'il rende toujours un résultat.
     *
     * <p>C'est le filet de sécurité de la récupération partielle. {@code AbstractLlmCriterion}
     * rattrape déjà ses propres échecs, mais rien n'oblige un critère écrit ailleurs à en faire
     * autant. Perdre un critère est acceptable ; perdre les huit autres ne l'est pas.
     */
    private CriterionResult evaluateOne(Criterion criterion,
                                        ProjectSnapshot project,
                                        ProgressListener listener) {
        CriterionDescriptor descriptor = criterion.descriptor();
        try {
            CriterionResult result = criterion.evaluate(project, listener);
            if (result == null) {
                // Le contrat l'interdit, mais un null qui circule donnerait plus loin une erreur
                // sans rapport avec sa cause. On le transforme ici, où on sait encore d'où il vient.
                return failed(descriptor, listener, "le critère n'a rien renvoyé");
            }
            return result;
        } catch (RuntimeException e) {
            return failed(descriptor, listener, describe(e));
        }
    }

    /** Signale l'abandon d'un critère et produit la ligne qui le dira dans le rapport. */
    private static CriterionResult failed(CriterionDescriptor descriptor,
                                          ProgressListener listener,
                                          String reason) {
        listener.onWarning("Critère « %s » abandonné : %s".formatted(descriptor.label(), reason));
        return CriterionResult.failed(descriptor.id(), descriptor.label(), descriptor.maxScore(), reason);
    }

    /**
     * Consigne le résultat dans l'historique.
     *
     * <p>Un historique en panne ne doit pas emporter une évaluation qui vient d'aboutir : le
     * rapport est déjà constitué, et le perdre pour un fichier non écrit serait absurde.
     */
    private void record(EvaluationResult result, ProgressListener listener) {
        try {
            history.record(result);
        } catch (RuntimeException e) {
            listener.onWarning("Évaluation non conservée dans l'historique : " + describe(e));
        }
    }

    /**
     * Le nombre d'appels au modèle à faire figurer dans le rapport.
     *
     * <p>Hors ligne, la doublure est bien sollicitée une fois par critère, mais aucun modèle
     * n'est joint. Reporter ces sollicitations donnerait un rapport qui se contredit :
     * « modèle interrogé : hors ligne » au-dessus de « appels au modèle : 6 ».
     */
    private int llmCallCount(int callsBefore) {
        return llmCounter.isLive() ? llmCounter.callCount() - callsBefore : 0;
    }

    /**
     * Le modèle à afficher dans le rapport.
     *
     * <p>Une évaluation hors ligne reste une évaluation valide — les critères déterministes
     * fonctionnent — mais le lecteur doit savoir qu'aucun modèle n'a été interrogé.
     */
    private String modelLabel() {
        return llmCounter.isLive() ? llmCounter.modelName() : "hors ligne";
    }

    /** Message lisible d'une exception, y compris quand elle n'en porte aucun. */
    private static String describe(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * Relaie les notifications à la vue tout en gardant les avertissements de côté.
     *
     * <p>Les avertissements ont deux destinataires aux besoins opposés : la vue veut les
     * afficher au fil de l'eau, le rapport veut la liste complète à la fin. Sans cette classe,
     * il faudrait soit demander à chaque vue de les mémoriser, soit passer une liste mutable à
     * travers tout le déroulé.
     *
     * <p>Même technique que les décorateurs de {@code com.reviewerai.llm} : on implémente
     * l'interface qu'on enveloppe, on ajoute un comportement, on délègue le reste.
     *
     * <p>Non synchronisée : les critères sont évalués séquentiellement, dans le seul fil
     * d'exécution de l'analyse.
     */
    private static final class WarningCollector implements ProgressListener {

        /**
         * Au-delà, on cesse de conserver. Un projet illisible peut en produire des centaines,
         * et un rapport qui les listerait toutes ne serait plus lu.
         */
        private static final int MAX_WARNINGS = 100;

        private final ProgressListener delegate;
        private final List<String> warnings = new ArrayList<>();

        WarningCollector(ProgressListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onStage(String stage) {
            delegate.onStage(stage);
        }

        @Override
        public void onProjectLoaded(ProjectSnapshot project) {
            delegate.onProjectLoaded(project);
        }

        @Override
        public void onTotalCriteria(int total) {
            delegate.onTotalCriteria(total);
        }

        @Override
        public void onCriterionStarted(String label, int index, int total) {
            delegate.onCriterionStarted(label, index, total);
        }

        @Override
        public void onCriterionFinished(CriterionResult result, int index, int total) {
            delegate.onCriterionFinished(result, index, total);
        }

        @Override
        public void onLlmCall(String criterionLabel, int estimatedTokens) {
            delegate.onLlmCall(criterionLabel, estimatedTokens);
        }

        @Override
        public void onWarning(String message) {
            if (warnings.size() < MAX_WARNINGS) {
                warnings.add(message);
            }
            delegate.onWarning(message);
        }

        /** Les avertissements retenus, du plus ancien au plus récent. */
        List<String> warnings() {
            return List.copyOf(warnings);
        }
    }
}
