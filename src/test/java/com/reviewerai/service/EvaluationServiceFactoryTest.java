package com.reviewerai.service;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.llm.LlmRequest;
import com.reviewerai.llm.LlmResponse;
import com.reviewerai.model.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'assemblage de {@link EvaluationServiceFactory}.
 *
 * <p>On vérifie ici la seule décision de câblage qui a un effet observable : un fournisseur
 * hors ligne ne doit pas être enveloppé de réessais. Le reste de l'assemblage est couvert par
 * les tests des briques qu'il empile.
 */
class EvaluationServiceFactoryTest {

    /**
     * Fournisseur hors ligne qui compte ses sollicitations. Il renvoie toujours une réponse
     * vide, comme le fait le mode hors ligne réel.
     */
    private static final class CountingOfflineProvider implements LlmProvider {
        private int asks;

        @Override
        public LlmResponse ask(LlmRequest request) {
            asks++;
            return LlmResponse.empty("hors ligne");
        }

        @Override
        public String modelName() {
            return "hors ligne";
        }

        @Override
        public boolean isLive() {
            return false;
        }
    }

    /** Compte les critères qui ont réellement déclenché un appel au modèle. */
    private static final class LlmCallCounter implements ProgressListener {
        private int llmCalls;

        @Override
        public void onLlmCall(String criterionLabel, int estimatedTokens) {
            llmCalls++;
        }
    }

    @Test
    @DisplayName("un fournisseur hors ligne n'est sollicité qu'une fois par critère, pas trois")
    void offlineProviderIsNotRetried(@TempDir Path project) throws IOException {
        // Un fichier Java réel : sans lui, le contexte serait vide et aucun critère confié au
        // modèle ne l'appellerait.
        Files.writeString(project.resolve("Sample.java"),
                "package demo;\npublic class Sample {\n  public int add(int a, int b) { return a + b; }\n}\n");

        EvaluationConfig config = EvaluationConfig.builder()
                .projectSource(project)
                .maxAttempts(3)
                .build();

        CountingOfflineProvider provider = new CountingOfflineProvider();
        LlmCallCounter counter = new LlmCallCounter();

        EvaluationResult result = EvaluationServiceFactory.create(config, provider, message -> { })
                .evaluate(counter);

        assertTrue(counter.llmCalls > 0, "au moins un critère confié au modèle doit s'exécuter");
        assertEquals(counter.llmCalls, provider.asks,
                "hors ligne, chaque critère ne sollicite le fournisseur qu'une fois — jamais " + config.maxAttempts());
        assertEquals("hors ligne", result.modelName());
    }
}
