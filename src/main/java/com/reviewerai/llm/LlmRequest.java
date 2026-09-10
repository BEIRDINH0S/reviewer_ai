package com.reviewerai.llm;

import java.util.Objects;

/**
 * Une requête adressée à un modèle de langage.
 *
 * <p>Volontairement pauvre : deux textes et un plafond. C'est ce qui permet à
 * {@link LlmProvider} de rester indépendant du fournisseur — Ollama, Mistral et DeepSeek
 * savent tous traiter cela, chacun avec sa propre plomberie HTTP.
 *
 * <p>Le prompt système et le prompt utilisateur sont séparés parce que la frontière de
 * confiance passe exactement entre les deux : le système contient nos consignes, l'utilisateur
 * contient le code évalué, qui est une donnée non fiable. Les mélanger reviendrait à laisser
 * le code évalué réécrire nos consignes.
 *
 * @param systemPrompt le rôle et les règles données au modèle ; écrit par nous
 * @param userPrompt   le critère et les extraits de code ; contient de la donnée non fiable
 * @param maxTokens    plafond de la réponse attendue
 */
public record LlmRequest(String systemPrompt, String userPrompt, int maxTokens) {

    public LlmRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens doit être positif : " + maxTokens);
        }
    }

    /** Estimation grossière du coût de la requête, quatre caractères par jeton. */
    public int estimatedPromptTokens() {
        return (systemPrompt.length() + userPrompt.length()) / 4;
    }
}
