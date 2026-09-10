package com.reviewerai.criteria;

import com.reviewerai.context.ContextBuilder;
import com.reviewerai.llm.CriterionResponseParser;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.model.FileKind;
import com.reviewerai.project.FileSelector;
import com.reviewerai.verify.FindingVerifier;

/**
 * Juge la sécurité : secrets, entrées non validées, exécution de code, configuration du conteneur.
 *
 * <p>Critère évalué par le modèle. Tout le déroulé — contexte, prompt, appel, validation,
 * filtrage — est hérité de {@link AbstractLlmCriterion} ; cette classe ne décide que de deux
 * choses : ce qu'elle demande, et ce qu'elle regarde.
 */
public final class SecurityCriterion extends AbstractLlmCriterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "security";

    public SecurityCriterion(ContextBuilder contextBuilder,
                             LlmProvider llm,
                             CriterionResponseParser parser,
                             FindingVerifier verifier,
                             int maxResponseTokens) {
        super(contextBuilder, llm, parser, verifier, maxResponseTokens);
    }

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.llm(ID, "Sécurité", """
                Juge la sécurité. Cherche des secrets ou des jetons écrits dans le code, des entrées
                utilisées sans validation, des chemins de fichiers construits sans contrôle,
                l'exécution de commandes système, une désérialisation non maîtrisée, et une
                configuration Docker trop permissive — conteneur lancé en root, système de
                fichiers accessible en écriture, réseau ouvert sans raison.""");
    }

    @Override
    protected FileSelector fileSelector() {
        return FileSelector.kinds(FileKind.JAVA_MAIN, FileKind.CONFIG, FileKind.DOCKER);
    }
}
