package com.reviewerai.context;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.llm.LlmProvider;
import com.reviewerai.llm.LlmRequest;
import com.reviewerai.llm.LlmResponse;
import com.reviewerai.llm.StubLlmProvider;
import com.reviewerai.model.CodeExcerpt;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.DirectoryProjectLoader;
import com.reviewerai.project.FileSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la stratégie de contexte par résumé progressif.
 *
 * <p>Aucun vrai modèle : le fournisseur est une doublure. On vérifie que les fichiers qui ne
 * tiennent pas en entier sont résumés, que le cache évite de les résumer deux fois, et qu'un
 * échec de résumé ne fait pas échouer le critère.
 */
class SummarizingContextBuilderTest {

    private static final CriterionDescriptor DESCRIPTOR =
            CriterionDescriptor.llm("architecture", "Architecture", "juge l'architecture");

    private static String classBody(char letter) {
        return "class " + letter + " {\n"
                + "  int m1() { return 1; }\n"
                + "  int m2() { return 2; }\n"
                + "  int m3() { return 3; }\n"
                + "  int m4() { return 4; }\n"
                + "}\n";
    }

    private static ProjectSnapshot threeFileProject(Path repo) throws IOException {
        Path dir = repo.resolve("src/main/java/demo");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("A.java"), classBody('A'));
        Files.writeString(dir.resolve("B.java"), classBody('B'));
        Files.writeString(dir.resolve("C.java"), classBody('C'));
        return DirectoryProjectLoader.standard().load(repo, FileSelector.all());
    }

    /** Budget serré : un fichier tient en entier, les autres doivent être résumés. */
    private static EvaluationConfig tightConfig() {
        return EvaluationConfig.builder().projectSource(Path.of(".")).maxContextTokens(120).build();
    }

    @Test
    @DisplayName("les fichiers qui ne tiennent pas en entier sont résumés")
    void filesBeyondBudgetAreSummarised(@TempDir Path repo) throws IOException {
        ProjectSnapshot project = threeFileProject(repo);
        var builder = new SummarizingContextBuilder(tightConfig(), StubLlmProvider.returning("RÉSUMÉ du fichier"));

        EvaluationContext context = builder.build(project, DESCRIPTOR, FileSelector.all());

        assertTrue(context.excerpts().stream().anyMatch(e -> e.reason().startsWith("fichier complet")),
                "au moins un fichier doit entrer en entier");
        List<CodeExcerpt> summaries = context.excerpts().stream()
                .filter(e -> e.reason().startsWith("résumé")).toList();
        assertFalse(summaries.isEmpty(), "les fichiers en trop doivent être résumés");
        assertEquals("RÉSUMÉ du fichier", summaries.getFirst().content());
    }

    @Test
    @DisplayName("un échec de résumé ne fait pas échouer le critère")
    void summaryFailureFallsBackGracefully(@TempDir Path repo) throws IOException {
        ProjectSnapshot project = threeFileProject(repo);
        var builder = new SummarizingContextBuilder(tightConfig(), StubLlmProvider.failing(true));

        EvaluationContext context = builder.build(project, DESCRIPTOR, FileSelector.all());

        assertFalse(context.isEmpty(), "les fichiers complets restent présents malgré l'échec des résumés");
        assertTrue(context.excerpts().stream().noneMatch(e -> e.reason().startsWith("résumé")),
                "aucun résumé ne doit apparaître quand le modèle échoue");
    }

    @Test
    @DisplayName("un fichier n'est résumé qu'une fois, même sur plusieurs critères")
    void summariesAreCachedAcrossCriteria(@TempDir Path repo) throws IOException {
        ProjectSnapshot project = threeFileProject(repo);
        CountingProvider provider = new CountingProvider();
        var builder = new SummarizingContextBuilder(tightConfig(), provider);

        builder.build(project, DESCRIPTOR, FileSelector.all());
        int afterFirst = provider.asks;
        builder.build(project, CriterionDescriptor.llm("solid", "SOLID", "juge SOLID"), FileSelector.all());

        assertTrue(afterFirst > 0, "le premier passage a bien résumé des fichiers");
        assertEquals(afterFirst, provider.asks,
                "le second critère réutilise les résumés en cache, sans nouvel appel");
    }

    /** Fournisseur qui compte ses appels et renvoie un résumé fixe. */
    private static final class CountingProvider implements LlmProvider {
        private int asks;

        @Override
        public LlmResponse ask(LlmRequest request) {
            asks++;
            return new LlmResponse("résumé simulé", "stub", Duration.ZERO);
        }

        @Override
        public String modelName() {
            return "stub";
        }
    }
}
