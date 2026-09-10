package com.reviewerai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests des décorateurs de résilience et de traçabilité.
 *
 * <p>Aucun modèle n'est interrogé : c'est exactement ce que le sujet demande en exigeant que
 * l'application soit testable sans appeler un LLM à chaque test.
 */
class LlmDecoratorsTest {

    private static LlmRequest request() {
        return new LlmRequest("système", "utilisateur", 500);
    }

    /** Fournisseur qui échoue les {@code failures} premières fois, puis répond. */
    private static LlmProvider flaky(int failures, boolean retryable, AtomicInteger attempts) {
        return new LlmProvider() {
            @Override
            public LlmResponse ask(LlmRequest r) {
                if (attempts.incrementAndGet() <= failures) {
                    throw new LlmException("panne " + attempts.get(), retryable);
                }
                return new LlmResponse("ok", "test", Duration.ZERO);
            }

            @Override
            public String modelName() {
                return "test";
            }
        };
    }

    @Test
    @DisplayName("un échec passager est retenté et finit par aboutir")
    void retriesTransientFailures() {
        var attempts = new AtomicInteger();
        var provider = new RetryingLlmProvider(flaky(2, true, attempts), 3, 0);

        assertEquals("ok", provider.ask(request()).text());
        assertEquals(3, attempts.get());
    }

    @Test
    @DisplayName("un échec définitif n'est pas retenté")
    void doesNotRetryPermanentFailures() {
        var attempts = new AtomicInteger();
        var provider = new RetryingLlmProvider(flaky(5, false, attempts), 3, 0);

        assertThrows(LlmException.class, () -> provider.ask(request()));
        assertEquals(1, attempts.get(), "réessayer une requête refusée ne changerait rien");
    }

    @Test
    @DisplayName("le nombre de tentatives est plafonné")
    void stopsAfterMaxAttempts() {
        var attempts = new AtomicInteger();
        var provider = new RetryingLlmProvider(flaky(99, true, attempts), 3, 0);

        var exception = assertThrows(LlmException.class, () -> provider.ask(request()));
        assertEquals(3, attempts.get());
        assertTrue(exception.getMessage().contains("3 tentative"));
    }

    @Test
    @DisplayName("une réponse vide est traitée comme un échec passager")
    void blankResponseCountsAsFailure() {
        var attempts = new AtomicInteger();
        LlmProvider blank = new LlmProvider() {
            @Override
            public LlmResponse ask(LlmRequest r) {
                attempts.incrementAndGet();
                return LlmResponse.empty("test");
            }

            @Override
            public String modelName() {
                return "test";
            }
        };

        assertThrows(LlmException.class, () -> new RetryingLlmProvider(blank, 2, 0).ask(request()));
        assertEquals(2, attempts.get(), "une réponse vide doit être retentée");
    }

    @Test
    @DisplayName("un nombre de tentatives nul est refusé à la construction")
    void zeroAttemptsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryingLlmProvider(StubLlmProvider.returning("x"), 0, 0));
    }

    @Test
    @DisplayName("les appels sont comptés, échecs compris")
    void callsAreCounted() {
        var counting = CountingLlmProvider.silent(StubLlmProvider.returning("réponse"));

        counting.ask(request());
        counting.ask(request());

        assertEquals(2, counting.callCount());
        assertEquals(0, counting.failureCount());
    }

    @Test
    @DisplayName("les échecs sont comptés séparément")
    void failuresAreCountedSeparately() {
        var counting = CountingLlmProvider.silent(StubLlmProvider.failing(false));

        assertThrows(LlmException.class, () -> counting.ask(request()));

        assertEquals(1, counting.callCount());
        assertEquals(1, counting.failureCount());
    }

    @Test
    @DisplayName("le journal ne contient jamais le contenu du prompt")
    void journalNeverLeaksPromptContent() {
        List<String> journal = new ArrayList<>();
        var counting = new CountingLlmProvider(
                StubLlmProvider.returning("réponse du modèle"), journal::add);

        counting.ask(new LlmRequest("consigne secrète", "code du projet évalué", 500));

        assertEquals(1, journal.size());
        assertFalse(journal.getFirst().contains("code du projet évalué"),
                "le prompt transporte du code d'un tiers : il n'a rien à faire dans un journal");
        assertFalse(journal.getFirst().contains("consigne secrète"));
    }

    @Test
    @DisplayName("empilés, le compteur ne compte qu'un appel métier malgré les tentatives")
    void countingWrapsRetrying() {
        var attempts = new AtomicInteger();
        var counting = CountingLlmProvider.silent(
                new RetryingLlmProvider(flaky(2, true, attempts), 3, 0));

        counting.ask(request());

        assertEquals(3, attempts.get(), "trois requêtes réseau");
        assertEquals(1, counting.callCount(), "mais un seul appel métier");
    }

    @Test
    @DisplayName("le fournisseur hors ligne se déclare comme tel")
    void offlineProviderIsNotLive() {
        assertFalse(StubLlmProvider.silent().isLive());
        assertFalse(CountingLlmProvider.silent(StubLlmProvider.silent()).isLive(),
                "le décorateur doit relayer l'information au rapport");
    }
}
