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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'assemblage de {@link EvaluationServiceFactory}.
 *
 * <p>On vérifie ici les décisions de câblage qui ont un effet observable : un fournisseur hors
 * ligne ne doit pas être enveloppé de réessais, et chaque critère doit recevoir la stratégie de
 * contexte prévue pour lui. Le reste de l'assemblage est couvert par les tests des briques qu'il
 * empile.
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

    /** Fournisseur hors ligne qui retient les prompts reçus, pour inspecter le contexte envoyé. */
    private static final class CapturingProvider implements LlmProvider {
        private final List<String> prompts = new ArrayList<>();

        @Override
        public LlmResponse ask(LlmRequest request) {
            prompts.add(request.userPrompt());
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

    /**
     * Deux classes dont l'une appelle l'autre : de quoi former un graphe d'appel non vide, sans
     * quoi la stratégie par graphe déléguerait à son repli et le test ne prouverait rien.
     */
    private static void writeCallingClasses(Path project) throws IOException {
        Path sources = project.resolve("src/main/java/demo");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("Sample.java"),
                "package demo;\npublic class Sample {\n"
                        + "  public int add(int a, int b) { return a + b; }\n}\n");
        Files.writeString(sources.resolve("Caller.java"),
                "package demo;\npublic class Caller {\n  private final Sample sample = new Sample();\n"
                        + "  public int twice(int x) { return sample.add(x, x); }\n}\n");
    }

    /**
     * Un fichier de test dont le helper est appelé par cinq méthodes du même fichier — la forme
     * exacte qui hisse un helper de test en tête du classement par centralité.
     */
    private static void writeTestHelper(Path project) throws IOException {
        Path sources = project.resolve("src/test/java/demo");
        Files.createDirectories(sources);
        StringBuilder file = new StringBuilder("package demo;\npublic class SampleTest {\n"
                + "  private int helper(int x) { return new Sample().add(x, x); }\n");
        for (int i = 1; i <= 5; i++) {
            file.append("  public void case").append(i).append("() { helper(").append(i).append("); }\n");
        }
        Files.writeString(sources.resolve("SampleTest.java"), file.append("}\n").toString());
    }

    /** Le prompt envoyé au modèle pour un seul critère, l'évaluation étant restreinte à lui. */
    private static String promptFor(Path project, String criterionId) {
        EvaluationConfig config = EvaluationConfig.builder()
                .projectSource(project)
                .criterionIds(List.of(criterionId))
                .build();

        CapturingProvider provider = new CapturingProvider();
        EvaluationServiceFactory.create(config, provider, message -> { })
                .evaluate(ProgressListener.noop());

        assertEquals(1, provider.prompts.size(), "le critère « " + criterionId + " » doit appeler le modèle une fois");
        return provider.prompts.get(0);
    }

    @Test
    @DisplayName("les critères relationnels reçoivent le contexte par graphe d'appel")
    void relationalCriteriaGetCallGraphContext(@TempDir Path project) throws IOException {
        writeCallingClasses(project);

        // La raison d'un extrait issu du graphe est la seule trace observable de la stratégie
        // employée : elle voyage jusque dans le prompt.
        for (String criterionId : List.of("architecture", "solid", "design-patterns")) {
            assertTrue(promptFor(project, criterionId).contains("méthode structurante"),
                    "le critère « " + criterionId + " » doit recevoir des méthodes et leur voisinage");
        }
    }

    @Test
    @DisplayName("aucun extrait de test n'est envoyé, ni comme méthode centrale ni comme voisine")
    void testCodeNeverReachesTheModel(@TempDir Path project) throws IOException {
        writeCallingClasses(project);
        writeTestHelper(project);

        // helper() a cinq appelants, Sample#add en a deux : sans filtre, le helper de test
        // passerait devant, et ses cinq appelants suivraient comme voisines.
        String prompt = promptFor(project, "architecture");

        assertFalse(prompt.contains("SampleTest"),
                "aucune méthode de test ne doit figurer dans le contexte envoyé au modèle");
        assertTrue(prompt.contains("méthode structurante"),
                "le contexte doit tout de même venir du graphe, pas du repli");
    }

    @Test
    @DisplayName("les critères qui jugent le code tel qu'il se lit reçoivent des fichiers entiers")
    void textualCriteriaKeepRepresentativeFiles(@TempDir Path project) throws IOException {
        writeCallingClasses(project);

        for (String criterionId : List.of("readability", "error-handling", "security")) {
            assertFalse(promptFor(project, criterionId).contains("méthode structurante"),
                    "le critère « " + criterionId + " » doit recevoir des fichiers, pas des méthodes isolées");
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
