package com.reviewerai.llm;

import com.reviewerai.config.EvaluationConfig;

import java.util.Objects;

/**
 * Interroge un modèle servi localement par Ollama.
 *
 * <p><b>Patron de conception : Adaptateur</b> (structurel)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>LangChain4j expose ses propres types ({@code ChatModel}, {@code ChatResponse}, ses
 *       exceptions). Les laisser remonter dans le métier reviendrait à ne plus pouvoir en
 *       changer, et rendrait tout le projet dépendant d'une bibliothèque tierce.</dd>
 *   <dt>Solution</dt>
 *   <dd>Cette classe est le seul endroit du projet où LangChain4j est importé. Elle traduit
 *       {@link LlmRequest} vers l'API de la bibliothèque, et sa réponse — ou son exception —
 *       vers {@link LlmResponse} et {@link LlmException}.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Adaptateur d'objet, pas de classe : on encapsule le {@code ChatModel} plutôt que d'en
 *       hériter. Java n'autorisant qu'un héritage, l'adaptateur de classe du cours coûterait
 *       la seule extension disponible pour un gain nul. Vérification concrète que
 *       l'encapsulation est étanche : aucun {@code import dev.langchain4j} ne doit exister
 *       ailleurs que dans ce fichier.</dd>
 * </dl>
 *
 * <p>Le choix d'un modèle local répond à deux exigences du sujet : aucune clé d'API ne peut
 * fuiter puisqu'il n'y en a pas, et le conteneur d'analyse peut tourner sans accès à Internet.
 *
 * <p><b>Sécurité</b> : Ollama n'écoute que sur la boucle locale, et le modèle ne reçoit aucun
 * outil. Il lit du texte, il écrit du texte.
 *
 */
public final class OllamaLlmProvider implements LlmProvider {

    private final EvaluationConfig config;

    public OllamaLlmProvider(EvaluationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Interroge le modèle et renvoie sa réponse brute.
     *
     * <p>Marche à suivre pour l'implémentation :
     * <ol>
     *   <li>construire une fois pour toutes un {@code OllamaChatModel} — le reconstruire à
     *       chaque appel rouvrirait une connexion à chaque critère —, en lui passant
     *       {@code config.ollamaBaseUrl()}, {@code config.modelName()} et
     *       {@code config.llmTimeout()} ;
     *   <li>demander un format de sortie JSON si le modèle le prend en charge : cela réduit
     *       nettement le nombre de réponses inexploitables ;
     *   <li>mesurer la durée de l'aller-retour et la reporter dans {@link LlmResponse} ;
     *   <li>traduire les échecs : délai dépassé, connexion refusée et erreur 5xx sont des
     *       {@link LlmException#transientFailure} — réessayer a un sens ; modèle inconnu et
     *       erreur 4xx sont des {@link LlmException#permanentFailure} — réessayer ne changera
     *       rien.
     * </ol>
     *
     * <p>Ne jamais laisser remonter une exception de LangChain4j telle quelle : ce serait la
     * fuite d'abstraction que cet adaptateur existe précisément pour empêcher.
     */
    @Override
    public LlmResponse ask(LlmRequest request) {
        throw new UnsupportedOperationException("OllamaLlmProvider : à implémenter");
    }

    @Override
    public String modelName() {
        return config.modelName();
    }
}
