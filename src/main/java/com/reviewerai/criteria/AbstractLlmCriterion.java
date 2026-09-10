package com.reviewerai.criteria;

import com.reviewerai.context.ContextBuilder;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.LlmException;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.llm.LlmRequest;
import com.reviewerai.llm.LlmResponse;
import com.reviewerai.llm.PromptTemplates;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.FileSelector;
import com.reviewerai.service.ProgressListener;
import com.reviewerai.verify.FindingVerifier;

import java.util.List;
import java.util.Objects;

/**
 * Squelette commun à tous les critères évalués par un modèle.
 *
 * <p><b>Patron de conception : Patron de méthode</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Les six critères confiés au modèle suivent exactement le même déroulé : choisir les
 *       fichiers, construire le contexte, écrire le prompt, appeler, valider, filtrer. Seuls
 *       deux points varient : quels fichiers regarder, et quoi demander. Recopier six fois le
 *       déroulé garantirait six occasions de le faire différemment — et six endroits à
 *       corriger le jour où la validation change.</dd>
 *   <dt>Solution</dt>
 *   <dd>{@link #evaluate} fixe le squelette de l'algorithme et le déclare {@code final}. Les
 *       étapes qui varient sont deux méthodes abstraites, {@link #descriptor} et
 *       {@link #fileSelector}, redéfinies par chaque sous-classe. Une sous-classe tient en
 *       vingt lignes et ne peut pas se tromper d'enchaînement.</dd>
 *   <dt>Remarques</dt>
 *   <dd>C'est le patron du cours dans sa forme canonique : classe abstraite, méthode
 *       squelette non redéfinissable, étapes déléguées. Le {@code final} sur {@link #evaluate}
 *       n'est pas décoratif — c'est lui qui garantit qu'aucun critère ne pourra contourner la
 *       validation de la réponse du modèle, qui est une mesure de sécurité.</dd>
 * </dl>
 *
 * <p>Les collaborateurs arrivent par le constructeur : le critère ne construit rien lui-même
 * et se teste donc avec un {@code StubLlmProvider}, sans qu'aucun modèle ne tourne.
 */
public abstract class AbstractLlmCriterion implements Criterion {

    private final ContextBuilder contextBuilder;
    private final LlmProvider llm;
    private final CriterionResponseParser parser;
    private final FindingVerifier verifier;
    private final int maxResponseTokens;

    protected AbstractLlmCriterion(ContextBuilder contextBuilder,
                                   LlmProvider llm,
                                   CriterionResponseParser parser,
                                   FindingVerifier verifier,
                                   int maxResponseTokens) {
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder");
        this.llm = Objects.requireNonNull(llm, "llm");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.maxResponseTokens = maxResponseTokens;
    }

    /**
     * Le déroulé, identique pour tous les critères confiés au modèle.
     *
     * <p>Déclaré {@code final} : une sous-classe personnalise ce qu'elle regarde, jamais la
     * façon dont la réponse est validée.
     *
     * <p>Chaque échec possible est rattrapé et transformé en critère non évalué. C'est la
     * récupération partielle demandée par le sujet : le rapport dira « critère non évalué :
     * délai dépassé » plutôt que de disparaître ou de faire échouer toute l'analyse.
     */
    @Override
    public final CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
        CriterionDescriptor descriptor = descriptor();
        try {
            EvaluationContext context = contextBuilder.build(project, descriptor, fileSelector());
            if (context.isEmpty()) {
                return CriterionResult.failed(descriptor.id(), descriptor.label(), descriptor.maxScore(),
                        "aucun fichier pertinent dans ce projet");
            }

            listener.onLlmCall(descriptor.label(), context.estimatedTokens());
            LlmResponse response = llm.ask(new LlmRequest(
                    PromptTemplates.renderSystemPrompt(),
                    PromptTemplates.renderCriterionPrompt(context, descriptor),
                    maxResponseTokens));

            CriterionResult raw = parser.parse(response.text(), descriptor, project);
            List<Finding> kept = verifier.verify(raw.findings(), context, project);
            return withFindings(raw, kept);

        } catch (LlmException e) {
            return CriterionResult.failed(descriptor.id(), descriptor.label(), descriptor.maxScore(),
                    e.getMessage());
        } catch (CriterionResponseParser.InvalidResponseException e) {
            return CriterionResult.failed(descriptor.id(), descriptor.label(), descriptor.maxScore(),
                    "réponse du modèle inexploitable — " + e.getMessage());
        } catch (RuntimeException e) {
            // Filet de sécurité : un critère ne fait jamais tomber l'analyse entière.
            listener.onWarning("Critère « " + descriptor.label() + " » abandonné : " + e);
            return CriterionResult.failed(descriptor.id(), descriptor.label(), descriptor.maxScore(),
                    "erreur inattendue");
        }
    }

    /** Les fichiers que ce critère veut voir. Redéfini par chaque sous-classe. */
    protected abstract FileSelector fileSelector();

    /** Remplace les signalements par ceux qu'a retenus la vérification. */
    private static CriterionResult withFindings(CriterionResult result, List<Finding> findings) {
        return new CriterionResult(
                result.criterionId(), result.label(), result.score(), result.maxScore(),
                result.summary(), result.strengths(), result.weaknesses(), result.recommendations(),
                findings, result.evaluated());
    }
}
