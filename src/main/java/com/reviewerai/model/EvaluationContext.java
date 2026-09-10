package com.reviewerai.model;

import java.util.List;
import java.util.Objects;

/**
 * Ce qui est envoyé au modèle pour évaluer UN critère.
 *
 * <p>Produit par la brique Contexte, consommé par la brique LLM. C'est la réponse du projet à
 * la question « comment analyser un projet plus gros que ce qu'une requête peut contenir » :
 * chaque critère reçoit sa propre sélection d'extraits, sous un budget de jetons fixé.
 *
 * @param criterionId     identifiant du critère évalué
 * @param project         le projet concerné, pour que le prompt puisse le nommer
 * @param excerpts        les extraits retenus, les plus pertinents d'abord
 * @param inventory       résumé chiffré du projet (nombre de fichiers par nature), toujours
 *                        envoyé : il coûte quelques jetons et donne au modèle la vue d'ensemble
 *                        que les extraits seuls ne donnent pas
 * @param estimatedTokens coût estimé de l'ensemble, budget de sortie compris
 */
public record EvaluationContext(
        String criterionId,
        ProjectRef project,
        List<CodeExcerpt> excerpts,
        String inventory,
        int estimatedTokens) {

    public EvaluationContext {
        Objects.requireNonNull(criterionId, "criterionId");
        Objects.requireNonNull(project, "project");
        excerpts = excerpts == null ? List.of() : List.copyOf(excerpts);
        inventory = inventory == null ? "" : inventory;
    }

    /** Vrai si aucun extrait n'a pu être retenu : inutile d'appeler le modèle. */
    public boolean isEmpty() {
        return excerpts.isEmpty();
    }

    /** Les chemins des fichiers présents dans le contexte, sans doublon. */
    public List<String> filePaths() {
        return excerpts.stream().map(CodeExcerpt::filePath).distinct().toList();
    }
}
