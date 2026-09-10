package com.reviewerai.llm;

/**
 * Un appel au modèle a échoué.
 *
 * <p>Le sujet insiste : un LLM n'est pas un composant déterministe. Cette exception couvre
 * tout ce qui peut mal se passer — délai dépassé, erreur HTTP, serveur arrêté, réponse vide.
 *
 * <p>{@link #retryable} distingue les pannes qui valent la peine d'être retentées de celles
 * qui se reproduiront à l'identique. Réessayer trois fois une requête mal formée fait perdre
 * trois fois plus de temps sans rien changer ; réessayer un délai dépassé aboutit souvent.
 * C'est {@link RetryingLlmProvider} qui exploite cette distinction.
 */
public class LlmException extends RuntimeException {

    private final boolean retryable;

    public LlmException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public LlmException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** Panne passagère : délai dépassé, serveur momentanément indisponible, réponse vide. */
    public static LlmException transientFailure(String message, Throwable cause) {
        return new LlmException(message, cause, true);
    }

    /** Panne définitive : modèle inconnu, requête refusée, configuration invalide. */
    public static LlmException permanentFailure(String message, Throwable cause) {
        return new LlmException(message, cause, false);
    }

    /** Vrai si réessayer a une chance d'aboutir. */
    public boolean isRetryable() {
        return retryable;
    }
}
