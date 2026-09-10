package com.reviewerai.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Compte les appels au modèle et en journalise le déroulement.
 *
 * <p><b>Patron de conception : Décorateur</b> (structurel)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet demande de retenir, pour chaque analyse, le modèle utilisé, la durée des
 *       opérations et le nombre d'appels passés — sans pour autant enregistrer de secrets ni
 *       de données confidentielles. Comment mesurer sans polluer le fournisseur ni
 *       l'orchestration ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Un second décorateur, empilable sur le premier. Il mesure, compte, notifie, puis
 *       délègue. Le retirer ne change rien au comportement fonctionnel.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Le journal ne contient <b>jamais</b> le contenu des prompts : seulement des tailles,
 *       des durées et des compteurs. Le prompt transporte du code d'un projet tiers, qu'on n'a
 *       aucune raison de recopier dans un fichier de journal.</dd>
 * </dl>
 */
public final class CountingLlmProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final Consumer<String> journal;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong totalMillis = new AtomicLong();

    /**
     * @param delegate le fournisseur décoré
     * @param journal  destinataire des lignes de journal ; {@code message -> {}} pour n'en produire aucune
     */
    public CountingLlmProvider(LlmProvider delegate, Consumer<String> journal) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /** Compte sans journaliser. */
    public static CountingLlmProvider silent(LlmProvider delegate) {
        return new CountingLlmProvider(delegate, message -> { });
    }

    @Override
    public LlmResponse ask(LlmRequest request) {
        calls.incrementAndGet();
        long start = System.nanoTime();
        try {
            LlmResponse response = delegate.ask(request);
            long millis = elapsedMillis(start);
            totalMillis.addAndGet(millis);
            // Des tailles, jamais le contenu : le prompt transporte du code non fiable.
            journal.accept("appel %d : %d jetons envoyés, %d reçus, %d ms"
                    .formatted(calls.get(), request.estimatedPromptTokens(),
                            response.estimatedTokens(), millis));
            return response;
        } catch (LlmException e) {
            failures.incrementAndGet();
            totalMillis.addAndGet(elapsedMillis(start));
            journal.accept("appel %d en échec : %s".formatted(calls.get(), e.getMessage()));
            throw e;
        }
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public boolean isLive() {
        return delegate.isLive();
    }

    /** Nombre d'appels passés depuis la création, échecs compris. */
    public int callCount() {
        return calls.get();
    }

    /** Nombre d'appels ayant échoué définitivement. */
    public int failureCount() {
        return failures.get();
    }

    /** Temps cumulé passé à attendre le modèle. */
    public Duration totalTime() {
        return Duration.ofMillis(totalMillis.get());
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
