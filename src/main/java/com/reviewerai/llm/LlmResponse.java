package com.reviewerai.llm;

import java.time.Duration;
import java.util.Objects;

/**
 * Ce qu'un modèle a renvoyé.
 *
 * <p>Le texte brut, jamais interprété à ce niveau : l'analyse du contenu est le travail de
 * {@link CriterionResponseParser}. Séparer les deux permet de tester le parsing sans modèle,
 * et de changer de fournisseur sans retoucher la validation.
 *
 * <p>Les compteurs servent la traçabilité demandée par le sujet : durée, modèle, volume. Ils
 * remontent jusqu'au rapport.
 *
 * @param text      la réponse brute — <b>donnée non fiable</b>, à valider avant tout usage
 * @param modelName le modèle qui a répondu
 * @param elapsed   temps d'aller-retour
 */
public record LlmResponse(String text, String modelName, Duration elapsed) {

    public LlmResponse {
        text = text == null ? "" : text;
        modelName = modelName == null ? "" : modelName;
        elapsed = elapsed == null ? Duration.ZERO : elapsed;
    }

    /** Réponse vide, produite par le mode hors ligne. */
    public static LlmResponse empty(String modelName) {
        return new LlmResponse("", modelName, Duration.ZERO);
    }

    /** Vrai si le modèle n'a rien renvoyé d'exploitable. */
    public boolean isBlank() {
        return text.isBlank();
    }

    /** Estimation grossière du coût de la réponse. */
    public int estimatedTokens() {
        return text.length() / 4;
    }

    /** Les premiers caractères, pour un message de journal qui ne recopie pas tout. */
    public String preview(int limit) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= limit ? flat : flat.substring(0, limit) + "…";
    }
}
