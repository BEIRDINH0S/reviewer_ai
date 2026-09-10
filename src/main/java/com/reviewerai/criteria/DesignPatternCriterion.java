package com.reviewerai.criteria;

import com.reviewerai.context.ContextBuilder;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.model.FileKind;
import com.reviewerai.project.FileSelector;
import com.reviewerai.verify.FindingVerifier;

/**
 * Juge si les patrons employés résolvent un vrai problème, et s'ils sont correctement mis en oeuvre.
 *
 * <p>Critère évalué par le modèle. Tout le déroulé — contexte, prompt, appel, validation,
 * filtrage — est hérité de {@link AbstractLlmCriterion} ; cette classe ne décide que de deux
 * choses : ce qu'elle demande, et ce qu'elle regarde.
 */
public final class DesignPatternCriterion extends AbstractLlmCriterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "design-patterns";

    public DesignPatternCriterion(ContextBuilder contextBuilder,
                                  LlmProvider llm,
                                  CriterionResponseParser parser,
                                  FindingVerifier verifier,
                                  int maxResponseTokens) {
        super(contextBuilder, llm, parser, verifier, maxResponseTokens);
    }

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.llm(ID, "Pertinence des patrons de conception", """
                Repère les patrons de conception employés et juge-les sur trois points : le patron
                résout-il un problème réel du projet, est-il correctement mis en oeuvre, et son
                nom correspond-il à sa définition. Signale sévèrement un patron ajouté sans
                nécessité, et une classe nommée d'après un patron qui n'en est pas un. Un
                projet sobre et juste vaut mieux qu'un projet qui empile les patrons.""");
    }

    @Override
    protected FileSelector fileSelector() {
        return FileSelector.kinds(FileKind.JAVA_MAIN);
    }
}
