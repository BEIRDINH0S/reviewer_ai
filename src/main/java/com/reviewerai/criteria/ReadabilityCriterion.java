package com.reviewerai.criteria;

import com.reviewerai.context.ContextBuilder;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.model.FileKind;
import com.reviewerai.project.FileSelector;
import com.reviewerai.verify.FindingVerifier;

/**
 * Juge si le code se lit sans effort : nommage, longueur des méthodes, niveaux d'abstraction.
 *
 * <p>Critère évalué par le modèle. Tout le déroulé — contexte, prompt, appel, validation,
 * filtrage — est hérité de {@link AbstractLlmCriterion} ; cette classe ne décide que de deux
 * choses : ce qu'elle demande, et ce qu'elle regarde.
 */
public final class ReadabilityCriterion extends AbstractLlmCriterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "readability";

    public ReadabilityCriterion(ContextBuilder contextBuilder,
                                LlmProvider llm,
                                CriterionResponseParser parser,
                                FindingVerifier verifier,
                                int maxResponseTokens) {
        super(contextBuilder, llm, parser, verifier, maxResponseTokens);
    }

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.llm(ID, "Lisibilité du code", """
                Juge la lisibilité. Regarde le nommage des classes, des méthodes et des variables, la
                longueur des méthodes, la profondeur d'imbrication, et la présence d'un seul
                niveau d'abstraction par méthode. Signale le code mort, les valeurs magiques et
                les commentaires qui paraphrasent la ligne suivante au lieu d'expliquer
                pourquoi elle existe.""");
    }

    @Override
    protected FileSelector fileSelector() {
        return FileSelector.kinds(FileKind.JAVA_MAIN);
    }
}
