package com.reviewerai.criteria;

import com.reviewerai.context.ContextBuilder;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.model.FileKind;
import com.reviewerai.project.FileSelector;
import com.reviewerai.verify.FindingVerifier;

/**
 * Juge la robustesse : exceptions, cas limites, ressources, dégradation.
 *
 * <p>Critère évalué par le modèle. Tout le déroulé — contexte, prompt, appel, validation,
 * filtrage — est hérité de {@link AbstractLlmCriterion} ; cette classe ne décide que de deux
 * choses : ce qu'elle demande, et ce qu'elle regarde.
 */
public final class ErrorHandlingCriterion extends AbstractLlmCriterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "error-handling";

    public ErrorHandlingCriterion(ContextBuilder contextBuilder,
                                  LlmProvider llm,
                                  CriterionResponseParser parser,
                                  FindingVerifier verifier,
                                  int maxResponseTokens) {
        super(contextBuilder, llm, parser, verifier, maxResponseTokens);
    }

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.llm(ID, "Gestion des erreurs", """
                Juge la gestion des erreurs. Signale les exceptions avalées sans traitement, les blocs
                qui attrapent Exception ou Throwable sans raison, les ressources qui ne sont
                pas fermées, les valeurs nulles qui circulent librement, et les cas limites
                ignorés — entrée vide, fichier illisible, service externe indisponible. Regarde
                aussi si un échec local fait tomber tout le traitement.""");
    }

    @Override
    protected FileSelector fileSelector() {
        return FileSelector.kinds(FileKind.JAVA_MAIN);
    }
}
