package com.reviewerai.criteria;

import java.util.Objects;

/**
 * La carte d'identité d'un critère d'évaluation.
 *
 * <p>Séparée du {@link Criterion} lui-même parce qu'elle circule là où le critère n'a rien à
 * faire : dans le prompt, dans le rapport, dans l'interface qui propose la liste à cocher, et
 * dans l'historique. Un descripteur est une donnée ; un critère est un comportement.
 *
 * @param id       identifiant technique, stable dans le temps ; sert de clé partout
 * @param label    libellé français affiché à l'utilisateur et dans le rapport
 * @param maxScore note maximale, presque toujours 10
 * @param guidance ce qu'on demande au modèle de regarder ; part tel quel dans le prompt
 * @param usesLlm  {@code true} si l'évaluation passe par un modèle, {@code false} si elle est
 *                 purement déterministe
 */
public record CriterionDescriptor(
        String id,
        String label,
        int maxScore,
        String guidance,
        boolean usesLlm) {

    /** Note maximale par défaut, alignée sur l'exemple du sujet. */
    public static final int DEFAULT_MAX_SCORE = 10;

    public CriterionDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (id.isBlank()) {
            throw new IllegalArgumentException("l'identifiant d'un critère ne peut pas être vide");
        }
        if (maxScore <= 0) {
            throw new IllegalArgumentException("maxScore doit être positif : " + maxScore);
        }
        guidance = guidance == null ? "" : guidance;
    }

    /** Critère évalué par le modèle, noté sur 10. */
    public static CriterionDescriptor llm(String id, String label, String guidance) {
        return new CriterionDescriptor(id, label, DEFAULT_MAX_SCORE, guidance, true);
    }

    /** Critère calculé sans modèle, noté sur 10. */
    public static CriterionDescriptor deterministic(String id, String label, String guidance) {
        return new CriterionDescriptor(id, label, DEFAULT_MAX_SCORE, guidance, false);
    }
}
