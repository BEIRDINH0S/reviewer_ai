package com.reviewerai.llm;

import java.util.Objects;

/**
 * Réessaie un appel qui a échoué, puis abandonne proprement.
 *
 * <p><b>Patron de conception : Décorateur</b> (structurel)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet demande de gérer les délais dépassés, les erreurs HTTP, l'indisponibilité du
 *       serveur et les réponses vides, avec un nombre maximal de tentatives. Où mettre cette
 *       logique ? La dupliquer dans chaque fournisseur serait absurde ; la mettre dans le
 *       service mélangerait la résilience réseau et l'orchestration métier.</dd>
 *   <dt>Solution</dt>
 *   <dd>Une classe qui implémente {@link LlmProvider} et en enveloppe une autre. Elle ajoute
 *       le comportement sans modifier le fournisseur décoré, qui ignore jusqu'à son existence.
 *       On l'enfile ou on la retire d'une ligne dans la fabrique.</dd>
 *   <dt>Remarques</dt>
 *   <dd>C'est le décorateur du cours dans sa forme la plus pure : même interface, un
 *       composant décoré, un comportement ajouté avant et après la délégation. Les décorateurs
 *       se composent — {@code new CountingLlmProvider(new RetryingLlmProvider(ollama))} donne
 *       un fournisseur à la fois résilient et tracé, sans qu'aucune des deux classes ne
 *       connaisse l'autre.</dd>
 * </dl>
 *
 * <p>L'attente entre deux tentatives double à chaque fois. Un serveur saturé ne se libère pas
 * en dix millisecondes : marteler ne ferait qu'aggraver son état.
 */
public final class RetryingLlmProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final int maxAttempts;
    private final long initialBackoffMillis;

    /**
     * @param delegate             le fournisseur décoré
     * @param maxAttempts          nombre total de tentatives, la première comprise
     * @param initialBackoffMillis attente avant la deuxième tentative, doublée ensuite
     */
    public RetryingLlmProvider(LlmProvider delegate, int maxAttempts, long initialBackoffMillis) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts doit valoir au moins 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
    }

    /** Réglage courant : trois tentatives, une seconde d'attente initiale. */
    public static RetryingLlmProvider standard(LlmProvider delegate) {
        return new RetryingLlmProvider(delegate, 3, 1000);
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        LlmException last = null;
        long backoff = initialBackoffMillis;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                LlmResponse response = delegate.ask(request);
                if (response.isBlank()) {
                    // Une réponse vide est un échec au même titre qu'un délai dépassé : le
                    // modèle a répondu, mais rien d'exploitable n'est revenu.
                    throw LlmException.transientFailure("réponse vide du modèle", null);
                }
                return response;
            } catch (LlmException e) {
                last = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    break;
                }
                sleep(backoff);
                backoff *= 2;
            }
        }
        throw new LlmException(
                "échec après " + maxAttempts + " tentative(s) : " + (last == null ? "" : last.getMessage()),
                last, false);
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public boolean isLive() {
        return delegate.isLive();
    }

    /**
     * Attend, en restaurant l'indicateur d'interruption si on nous demande de nous arrêter.
     *
     * <p>Avaler une {@link InterruptedException} empêcherait l'arrêt du serveur web tant qu'une
     * analyse est en cours.
     */
    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("analyse interrompue", e, false);
        }
    }
}
