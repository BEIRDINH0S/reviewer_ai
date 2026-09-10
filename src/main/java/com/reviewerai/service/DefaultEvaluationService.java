package com.reviewerai.service;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.Criterion;
import com.reviewerai.criteria.CriterionRegistry;
import com.reviewerai.history.AnalysisHistory;
import com.reviewerai.llm.CountingLlmProvider;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.project.FileSelector;
import com.reviewerai.project.ProjectLoaderFactory;

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
        throw new UnsupportedOperationException("DefaultEvaluationService : à implémenter");
    }

    /** Les critères qui seront évalués, sans lancer l'analyse. Utilisé par l'interface. */
    public java.util.List<Criterion> selectedCriteria() {
        return registry.select(config.criterionIds());
    }
}
