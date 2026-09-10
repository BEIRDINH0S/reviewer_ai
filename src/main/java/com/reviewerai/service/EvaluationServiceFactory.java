package com.reviewerai.service;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.context.ContextBuilder;
import com.reviewerai.context.RepresentativeFileContextBuilder;
import com.reviewerai.criteria.ArchitectureCriterion;
import com.reviewerai.criteria.Criterion;
import com.reviewerai.criteria.CriterionRegistry;
import com.reviewerai.criteria.DesignPatternCriterion;
import com.reviewerai.criteria.DocumentationCriterion;
import com.reviewerai.criteria.ErrorHandlingCriterion;
import com.reviewerai.criteria.ProjectStructureCriterion;
import com.reviewerai.criteria.ReadabilityCriterion;
import com.reviewerai.criteria.SecurityCriterion;
import com.reviewerai.criteria.SolidCriterion;
import com.reviewerai.criteria.TestPresenceCriterion;
import com.reviewerai.history.AnalysisHistory;
import com.reviewerai.history.JsonFileAnalysisHistory;
import com.reviewerai.llm.CountingLlmProvider;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.JsonCriterionResponseParser;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.llm.OllamaLlmProvider;
import com.reviewerai.llm.RetryingLlmProvider;
import com.reviewerai.project.CompositeFileSelector;
import com.reviewerai.project.FileSelector;
import com.reviewerai.project.GlobFileSelector;
import com.reviewerai.project.ProjectLoaderFactory;
import com.reviewerai.verify.CompositeFindingVerifier;
import com.reviewerai.verify.ConfidenceVerifier;
import com.reviewerai.verify.DuplicateVerifier;
import com.reviewerai.verify.FindingVerifier;
import com.reviewerai.verify.KnownLocationVerifier;

import java.util.List;
import java.util.function.Consumer;

/**
 * Assemble un {@link EvaluationService} complet à partir d'une configuration.
 *
 * <p><b>Patron de conception : Fabrique</b> (création)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Une évaluation met en jeu une quinzaine d'objets, dont trois décorateurs empilés et
 *       neuf critères. Comment construire tout cela sans que l'appelant connaisse une seule
 *       classe concrète, et sans disperser ce câblage dans le programme ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une classe dédiée assemble le tout et ne renvoie que l'interface
 *       {@link EvaluationService}. C'est le seul endroit du projet, avec {@code Main}, où des
 *       implémentations concrètes sont nommées.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Fabrique statique, et non la {@code Factory} du cours avec sa méthode {@code build()}
 *       publique et sa méthode {@code howToBuild()} protégée : il n'existe qu'une façon
 *       d'assembler le service, et introduire une hiérarchie de fabriques pour une seule
 *       variante serait de la complexité gratuite. La surcharge acceptant un
 *       {@link LlmProvider} couvre le seul besoin réel de variation — les tests et le mode
 *       hors ligne.</dd>
 * </dl>
 *
 * <p>Le projet n'utilise pas de conteneur d'injection de dépendances : le câblage est fait à
 * la main, à un seul endroit, et se lit.
 *
 * <p>C'est ici que se lisent les réponses à deux questions du sujet :
 * <ul>
 *   <li><b>changer de modèle</b> — une ligne, dans {@link #llmProvider} ;
 *   <li><b>ajouter un critère</b> — une ligne, dans {@link #criterionRegistry}.
 * </ul>
 */
public final class EvaluationServiceFactory {

    private EvaluationServiceFactory() {
    }

    /** Assemblage de production : le modèle est réellement interrogé. */
    public static EvaluationService create(EvaluationConfig config) {
        return create(config, new OllamaLlmProvider(config), message -> { });
    }

    /**
     * Assemblage avec un fournisseur de modèle imposé.
     *
     * <p>Sert aux tests et au mode hors ligne : passer un
     * {@link com.reviewerai.llm.StubLlmProvider} fait tourner tout le pipeline sans qu'aucun
     * modèle ne soit installé. Les critères déterministes produisent alors de vraies notes,
     * les critères confiés au modèle sont marqués non évalués.
     *
     * @param config   les réglages
     * @param provider le fournisseur brut, avant décoration
     * @param journal  destinataire des lignes de traçabilité
     */
    public static EvaluationService create(EvaluationConfig config,
                                           LlmProvider provider,
                                           Consumer<String> journal) {
        CountingLlmProvider counting = llmProvider(config, provider, journal);
        ContextBuilder contextBuilder = new RepresentativeFileContextBuilder(config);
        CriterionResponseParser parser = new JsonCriterionResponseParser();
        FindingVerifier verifier = findingVerifier(config);

        return new DefaultEvaluationService(
                config,
                ProjectLoaderFactory.withMaxFileSize(config.maxFileSizeBytes()),
                fileSelector(config),
                criterionRegistry(contextBuilder, counting, parser, verifier, config),
                history(config),
                counting);
    }

    /**
     * Empile les décorateurs autour du fournisseur brut.
     *
     * <p>L'ordre compte. Le compteur est <b>à l'extérieur</b> pour qu'un appel réessayé trois
     * fois ne soit compté qu'une fois : c'est le nombre d'appels métier qui intéresse le
     * rapport, pas le nombre de requêtes HTTP.
     *
     * <p>Changer de fournisseur de modèle ne touche qu'à la ligne d'appel de cette méthode :
     * écrire une classe qui implémente {@link LlmProvider}, la passer ici, et la résilience
     * comme la traçabilité s'appliquent sans un mot de plus.
     */
    private static CountingLlmProvider llmProvider(EvaluationConfig config,
                                                   LlmProvider provider,
                                                   Consumer<String> journal) {
        LlmProvider resilient = new RetryingLlmProvider(provider, config.maxAttempts(), 1000);
        return new CountingLlmProvider(resilient, journal);
    }

    /**
     * Le catalogue des critères.
     *
     * <p>L'ordre est celui du rapport. Les critères déterministes viennent en premier : ils
     * sont instantanés, donc l'utilisateur voit des résultats avant même le premier appel au
     * modèle.
     *
     * <p><b>Ajouter un critère</b> : écrire la classe, ajouter une ligne ici. Rien d'autre ne
     * change — ni le moteur, ni le rapport, ni les vues, ni l'historique.
     */
    private static CriterionRegistry criterionRegistry(ContextBuilder contextBuilder,
                                                       LlmProvider llm,
                                                       CriterionResponseParser parser,
                                                       FindingVerifier verifier,
                                                       EvaluationConfig config) {
        int tokens = config.maxResponseTokens();
        List<Criterion> criteria = List.of(
                new ProjectStructureCriterion(),
                new TestPresenceCriterion(),
                new DocumentationCriterion(),
                new ArchitectureCriterion(contextBuilder, llm, parser, verifier, tokens),
                new ReadabilityCriterion(contextBuilder, llm, parser, verifier, tokens),
                new SolidCriterion(contextBuilder, llm, parser, verifier, tokens),
                new DesignPatternCriterion(contextBuilder, llm, parser, verifier, tokens),
                new ErrorHandlingCriterion(contextBuilder, llm, parser, verifier, tokens),
                new SecurityCriterion(contextBuilder, llm, parser, verifier, tokens));
        return new CriterionRegistry(criteria);
    }

    /**
     * Les règles d'inclusion et d'exclusion appliquées au chargement.
     *
     * <p>Deux règles composées : on ne retient que ce qui est textuel — envoyer une image à un
     * modèle de code n'a aucun sens — puis on applique les motifs de l'utilisateur.
     */
    private static FileSelector fileSelector(EvaluationConfig config) {
        return CompositeFileSelector.of(
                FileSelector.textualOnly(),
                GlobFileSelector.of(config.includePatterns(), config.excludePatterns()));
    }

    /**
     * L'enchaînement des règles de vérification.
     *
     * <p>L'ordre va du filtre le plus large au plus étroit : on écarte d'abord ce qui désigne
     * un endroit inexistant, puis les répétitions, puis ce dont le modèle doute lui-même.
     * Chaque étape reçoit ainsi moins de travail que la précédente.
     */
    private static FindingVerifier findingVerifier(EvaluationConfig config) {
        return CompositeFindingVerifier.of(
                new KnownLocationVerifier(),
                new DuplicateVerifier(),
                new ConfidenceVerifier(config.minConfidence()));
    }

    /** L'historique, absent si la configuration n'indique aucun répertoire. */
    private static AnalysisHistory history(EvaluationConfig config) {
        return config.history()
                .<AnalysisHistory>map(JsonFileAnalysisHistory::new)
                .orElseGet(AnalysisHistory::none);
    }
}
