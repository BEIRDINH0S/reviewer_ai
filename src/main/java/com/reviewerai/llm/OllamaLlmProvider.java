package com.reviewerai.llm;

import com.reviewerai.config.EvaluationConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
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

    /** On demande une sortie JSON : cela réduit nettement les réponses inexploitables. */
    private static final ResponseFormat JSON_FORMAT =
            ResponseFormat.builder().type(ResponseFormatType.JSON).build();

    private final EvaluationConfig config;
    private final ChatModel chatModel;

    /**
     * Construit l'adaptateur et son client une fois pour toutes.
     *
     * <p>Le {@code ChatModel} est bâti ici, pas à chaque appel : le reconstruire rouvrirait une
     * connexion pour chacun des critères. Sa construction n'ouvre aucune connexion — le réseau
     * n'est sollicité qu'au premier {@link #ask(LlmRequest)}.
     */
    public OllamaLlmProvider(EvaluationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.chatModel = (ChatModel) OllamaChatModel.builder()
                .baseUrl(config.ollamaBaseUrl())
                .modelName(config.modelName())
                .timeout(config.llmTimeout())
                .build();
    }

    /**
     * Interroge le modèle et renvoie sa réponse brute.
     *
     * <p>Le prompt système et le prompt utilisateur restent deux messages distincts : la
     * frontière de confiance passe entre les deux, les mélanger laisserait le code évalué
     * réécrire nos consignes. La durée de l'aller-retour est mesurée et reportée. Tout échec de
     * LangChain4j est traduit par {@link #classify}, jamais laissé remonter tel quel.
     */
    @Override
    public LlmResponse ask(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(request.systemPrompt()), UserMessage.from(request.userPrompt()))
                .responseFormat(JSON_FORMAT)
                .maxOutputTokens(request.maxTokens())
                .build();

        long startNanos = System.nanoTime();
        try {
            ChatResponse response = chatModel.chat(chatRequest);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return new LlmResponse(response.aiMessage().text(), config.modelName(), elapsed);
        } catch (RuntimeException e) {
            throw classify(e, config.modelName());
        }
    }

    @Override
    public String modelName() {
        return config.modelName();
    }

    /**
     * Traduit un échec de LangChain4j en {@link LlmException}, en décidant s'il vaut d'être
     * réessayé — la distinction qu'exploite {@link RetryingLlmProvider}.
     *
     * <p>Ce classement ne suit <b>pas</b> celui de LangChain4j ({@code RetriableException} /
     * {@code NonRetriableException}) mais celui demandé par le projet. La différence tient
     * surtout à un cas : un serveur de modèle injoignable est ici traité comme passager — on
     * veut le réessayer, le temps qu'Ollama démarre —, là où LangChain4j le classe définitif.
     *
     * <p>Passagers : délai dépassé, connexion refusée, indisponibilité momentanée, erreur 5xx.
     * Définitifs : modèle inconnu, hôte introuvable, erreur 4xx, requête refusée.
     *
     * @param failure   l'exception remontée par la bibliothèque
     * @param modelName le modèle interrogé, pour un message parlant
     */
    static LlmException classify(Throwable failure, String modelName) {
        // 1. Signaux réseau de bas niveau : les plus fiables, où qu'ils soient dans la chaîne.
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return LlmException.transientFailure("délai d'appel au modèle dépassé", failure);
            }
            if (cause instanceof ConnectException) {
                return LlmException.transientFailure("modèle injoignable : connexion refusée", failure);
            }
            if (cause instanceof UnknownHostException) {
                return LlmException.permanentFailure("hôte du serveur de modèle introuvable", failure);
            }
        }
        // 2. Exceptions typées de LangChain4j.
        if (failure instanceof HttpException http) {
            return http.statusCode() >= 500
                    ? LlmException.transientFailure("erreur serveur du modèle (" + http.statusCode() + ")", failure)
                    : LlmException.permanentFailure("requête refusée par le modèle (" + http.statusCode() + ")", failure);
        }
        if (failure instanceof ModelNotFoundException) {
            return LlmException.permanentFailure("modèle inconnu : " + modelName, failure);
        }
        if (failure instanceof TimeoutException) {
            return LlmException.transientFailure("délai d'appel au modèle dépassé", failure);
        }
        if (failure instanceof UnresolvedModelServerException
                || failure instanceof InternalServerException
                || failure instanceof RateLimitException) {
            return LlmException.transientFailure("serveur de modèle momentanément indisponible", failure);
        }
        if (failure instanceof NonRetriableException) {
            return LlmException.permanentFailure("appel au modèle refusé : " + failure.getMessage(), failure);
        }
        if (failure instanceof RetriableException) {
            return LlmException.transientFailure("échec passager du modèle : " + failure.getMessage(), failure);
        }
        // 3. Rien de reconnu : on ne réessaie pas une cause qu'on ne comprend pas.
        return LlmException.permanentFailure(
                "échec inattendu de l'appel au modèle : " + failure.getClass().getSimpleName(), failure);
    }
}
