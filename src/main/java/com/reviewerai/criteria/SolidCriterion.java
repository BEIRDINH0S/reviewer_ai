package com.reviewerai.criteria;

import com.reviewerai.context.ContextBuilder;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.model.FileKind;
import com.reviewerai.project.FileSelector;
import com.reviewerai.verify.FindingVerifier;

/**
 * Juge le respect des cinq principes SOLID, un par un, preuves à l'appui.
 *
 * <p>Critère évalué par le modèle. Tout le déroulé — contexte, prompt, appel, validation,
 * filtrage — est hérité de {@link AbstractLlmCriterion} ; cette classe ne décide que de deux
 * choses : ce qu'elle demande, et ce qu'elle regarde.
 */
public final class SolidCriterion extends AbstractLlmCriterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "solid";

    public SolidCriterion(ContextBuilder contextBuilder,
                          LlmProvider llm,
                          CriterionResponseParser parser,
                          FindingVerifier verifier,
                          int maxResponseTokens) {
        super(contextBuilder, llm, parser, verifier, maxResponseTokens);
    }

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.llm(ID, "Respect des principes SOLID", """
                Juge le respect des cinq principes SOLID. Pour chacun, appuie-toi sur un exemple précis
                du code fourni plutôt que sur une impression générale : une classe qui a
                plusieurs raisons de changer, une extension qui oblige à modifier l'existant,
                une sous-classe qui viole le contrat de sa classe mère, une interface que ses
                clients n'utilisent qu'à moitié, une classe qui construit elle-même ses
                dépendances au lieu de les recevoir.""");
    }

    @Override
    protected FileSelector fileSelector() {
        return FileSelector.kinds(FileKind.JAVA_MAIN);
    }
}
