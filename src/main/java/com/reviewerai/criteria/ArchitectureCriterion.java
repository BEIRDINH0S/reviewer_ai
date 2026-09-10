package com.reviewerai.criteria;

import com.reviewerai.context.ContextBuilder;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.model.FileKind;
import com.reviewerai.project.FileSelector;
import com.reviewerai.verify.FindingVerifier;

/**
 * Juge le découpage du projet en modules et la qualité des frontières entre eux.
 *
 * <p>Critère évalué par le modèle. Tout le déroulé — contexte, prompt, appel, validation,
 * filtrage — est hérité de {@link AbstractLlmCriterion} ; cette classe ne décide que de deux
 * choses : ce qu'elle demande, et ce qu'elle regarde.
 */
public final class ArchitectureCriterion extends AbstractLlmCriterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "architecture";

    public ArchitectureCriterion(ContextBuilder contextBuilder,
                                 LlmProvider llm,
                                 CriterionResponseParser parser,
                                 FindingVerifier verifier,
                                 int maxResponseTokens) {
        super(contextBuilder, llm, parser, verifier, maxResponseTokens);
    }

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.llm(ID, "Architecture et modularité", """
                Juge le découpage en paquets et en classes. Regarde si les responsabilités sont
                séparées, si le sens des dépendances est cohérent, et s'il existe des
                dépendances circulaires. Signale toute classe qui concentre l'essentiel du
                traitement, ainsi que toute logique métier présente dans l'interface
                utilisateur. Une architecture est bonne quand on peut remplacer un module sans
                toucher aux autres.""");
    }

    @Override
    protected FileSelector fileSelector() {
        return FileSelector.kinds(FileKind.JAVA_MAIN);
    }
}
