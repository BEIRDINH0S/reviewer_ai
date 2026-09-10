package com.reviewerai.llm;

import java.time.Duration;
import java.util.function.Function;

/**
 * Fournisseur sans modèle, pour les tests et le mode hors ligne.
 *
 * <p>Le sujet exige que l'application soit testable sans appeler un LLM à chaque test. C'est
 * cette classe qui le permet : tout le reste du pipeline — chargement, sélection, contexte,
 * validation, notation, rapport LaTeX — tourne à l'identique, en quelques millisecondes, sans
 * qu'Ollama soit installé.
 *
 * <p>Trois comportements, qui couvrent les trois situations à tester :
 * {@link #silent()} pour vérifier que la chaîne tient sans réponse,
 * {@link #returning} pour une réponse maîtrisée,
 * {@link #failing} pour vérifier la résilience.
 */
public final class StubLlmProvider implements LlmProvider {

    private final Function<LlmRequest, LlmResponse> behaviour;
    private final String modelName;

    private StubLlmProvider(String modelName, Function<LlmRequest, LlmResponse> behaviour) {
        this.modelName = modelName;
        this.behaviour = behaviour;
    }

    /** Renvoie toujours une réponse vide. Le rapport se construit, sans contenu du modèle. */
    public static StubLlmProvider silent() {
        return new StubLlmProvider("hors ligne", request -> LlmResponse.empty("hors ligne"));
    }

    /** Renvoie toujours le même texte. */
    public static StubLlmProvider returning(String text) {
        return new StubLlmProvider("stub", request -> new LlmResponse(text, "stub", Duration.ZERO));
    }

    /** Réponse calculée à partir de la requête, pour un test précis. */
    public static StubLlmProvider answering(Function<LlmRequest, String> answer) {
        return new StubLlmProvider("stub",
                request -> new LlmResponse(answer.apply(request), "stub", Duration.ZERO));
    }

    /** Échoue systématiquement, pour vérifier la récupération partielle. */
    public static StubLlmProvider failing(boolean retryable) {
        return new StubLlmProvider("stub", request -> {
            throw new LlmException("panne simulée", retryable);
        });
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        return behaviour.apply(request);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public boolean isLive() {
        return false;
    }
}
