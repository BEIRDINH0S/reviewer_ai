package com.reviewerai.context;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.graph.CodeGraphBuilder;
import com.reviewerai.model.CodeExcerpt;
import com.reviewerai.model.CodeGraph;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.MethodRef;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;
import com.reviewerai.project.FileSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la stratégie de contexte par graphe d'appel.
 *
 * <p>Le graphe est fourni par une doublure : on contrôle exactement la centralité des méthodes
 * et leurs liens, sans dépendre de la brique Graphe ni d'un vrai projet Java.
 */
class CallGraphContextBuilderTest {

    private static final ProjectSnapshot PROJECT =
            new ProjectSnapshot(ProjectRef.of("demo", Path.of("/tmp/demo"), SourceKind.DIRECTORY), List.of());
    private static final CriterionDescriptor DESCRIPTOR =
            CriterionDescriptor.llm("architecture", "Architecture", "juge l'architecture");

    private static MethodRef method(String simpleClass, String name) {
        return MethodRef.of(simpleClass + ".java", "demo." + simpleClass, name, List.of(), 1, 3);
    }

    /**
     * Graphe en étoile : {@code hub} est appelée par a, b, c (trois appelants, la plus centrale)
     * et appelle {@code leaf}.
     */
    private static FakeCodeGraph starGraph() {
        MethodRef hub = method("H", "hub");
        MethodRef leaf = method("L", "leaf");
        MethodRef a = method("A", "a");
        MethodRef b = method("B", "b");
        MethodRef c = method("C", "c");

        FakeCodeGraph graph = new FakeCodeGraph();
        for (MethodRef m : List.of(hub, leaf, a, b, c)) {
            graph.add(m, "void " + m.displayName() + "() { /* corps */ }");
        }
        graph.edge(a, hub);
        graph.edge(b, hub);
        graph.edge(c, hub);
        graph.edge(hub, leaf);
        return graph;
    }

    private static EvaluationConfig config(int maxContextTokens) {
        return EvaluationConfig.builder()
                .projectSource(Path.of("."))
                .maxContextTokens(maxContextTokens)
                .maxNeighbors(6)
                .maxNeighborDepth(1)
                .build();
    }

    private static CallGraphContextBuilder builder(CodeGraph graph, ContextBuilder fallback) {
        return new CallGraphContextBuilder(config(6000), (CodeGraphBuilder) repo -> graph, fallback);
    }

    private static ContextBuilder failingFallback() {
        return (project, descriptor, selector) -> {
            throw new AssertionError("le repli ne devait pas être utilisé");
        };
    }

    @Test
    @DisplayName("la méthode la plus appelée est retenue en premier")
    void mostCalledMethodComesFirst() {
        EvaluationContext context = builder(starGraph(), failingFallback())
                .build(PROJECT, DESCRIPTOR, FileSelector.all());

        CodeExcerpt first = context.excerpts().getFirst();
        assertEquals("H.java", first.filePath(), "hub est la méthode la plus appelée");
        assertTrue(first.reason().contains("3 appelant(s)"), "sa centralité est expliquée : " + first.reason());
    }

    @Test
    @DisplayName("les voisines apparaissent avec leur relation dans la raison de l'extrait")
    void neighboursCarryTheirRelation() {
        EvaluationContext context = builder(starGraph(), failingFallback())
                .build(PROJECT, DESCRIPTOR, FileSelector.all());

        List<String> reasons = context.excerpts().stream().map(CodeExcerpt::reason).toList();
        assertTrue(reasons.stream().anyMatch(r -> r.startsWith("appelée par H#hub")),
                "leaf est appelée par hub");
        assertTrue(reasons.stream().anyMatch(r -> r.startsWith("appelle H#hub")),
                "a, b, c appellent hub");
    }

    @Test
    @DisplayName("le budget de jetons est respecté")
    void tokenBudgetIsHonoured() {
        int budget = 40;
        var builder = new CallGraphContextBuilder(config(budget),
                (CodeGraphBuilder) repo -> starGraph(), failingFallback());

        EvaluationContext context = builder.build(PROJECT, DESCRIPTOR, FileSelector.all());

        assertTrue(context.estimatedTokens() <= budget,
                "le contexte (" + context.estimatedTokens() + ") doit tenir dans le budget " + budget);
        assertFalse(context.isEmpty(), "au moins la méthode centrale doit passer");
    }

    @Test
    @DisplayName("deux exécutions sur le même projet produisent exactement le même contexte")
    void resultIsDeterministic() {
        CallGraphContextBuilder builder = builder(starGraph(), failingFallback());

        EvaluationContext first = builder.build(PROJECT, DESCRIPTOR, FileSelector.all());
        EvaluationContext second = builder.build(PROJECT, DESCRIPTOR, FileSelector.all());

        assertEquals(render(first), render(second), "le contexte doit être reproductible");
    }

    @Test
    @DisplayName("un graphe vide fait passer au repli sans erreur")
    void emptyGraphFallsBack() {
        ContextBuilder fallback = (project, descriptor, selector) -> new EvaluationContext(
                descriptor.id(), project.project(),
                List.of(new CodeExcerpt("FALLBACK", 1, 1, "x", "repli")), "", 1);

        EvaluationContext context = builder(CodeGraph.empty(), fallback)
                .build(PROJECT, DESCRIPTOR, FileSelector.all());

        assertTrue(context.filePaths().contains("FALLBACK"), "le repli doit avoir produit le contexte");
    }

    @Test
    @DisplayName("le graphe n'est construit qu'une fois pour neuf critères")
    void graphIsBuiltOnce() {
        CountingGraphBuilder graphBuilder = new CountingGraphBuilder(starGraph());
        var builder = new CallGraphContextBuilder(config(6000), graphBuilder, failingFallback());

        for (int i = 0; i < 9; i++) {
            builder.build(PROJECT, DESCRIPTOR, FileSelector.all());
        }

        assertEquals(1, graphBuilder.builds, "le graphe coûte cher : il ne doit être construit qu'une fois");
    }

    private static String render(EvaluationContext context) {
        StringBuilder sb = new StringBuilder();
        for (CodeExcerpt excerpt : context.excerpts()) {
            sb.append(excerpt.filePath()).append('|').append(excerpt.reason()).append('|')
                    .append(excerpt.content()).append('\n');
        }
        return sb.toString();
    }

    /** Compte le nombre de constructions de graphe. */
    private static final class CountingGraphBuilder implements CodeGraphBuilder {
        private final CodeGraph graph;
        private int builds;

        CountingGraphBuilder(CodeGraph graph) {
            this.graph = graph;
        }

        @Override
        public CodeGraph build(Path repo) {
            builds++;
            return graph;
        }
    }

    /** Graphe d'appel en mémoire, entièrement contrôlé par le test. */
    private static final class FakeCodeGraph implements CodeGraph {
        private final Set<MethodRef> methods = new HashSet<>();
        private final Map<MethodRef, Set<MethodRef>> callees = new HashMap<>();
        private final Map<MethodRef, Set<MethodRef>> callers = new HashMap<>();
        private final Map<String, MethodRef> bySignature = new HashMap<>();
        private final Map<MethodRef, String> sources = new HashMap<>();

        void add(MethodRef method, String source) {
            methods.add(method);
            bySignature.put(method.signature(), method);
            sources.put(method, source);
            callees.computeIfAbsent(method, k -> new HashSet<>());
            callers.computeIfAbsent(method, k -> new HashSet<>());
        }

        void edge(MethodRef from, MethodRef to) {
            callees.get(from).add(to);
            callers.get(to).add(from);
        }

        @Override public Set<MethodRef> methods() { return methods; }
        @Override public Set<MethodRef> callees(MethodRef m) { return callees.getOrDefault(m, Set.of()); }
        @Override public Set<MethodRef> callers(MethodRef m) { return callers.getOrDefault(m, Set.of()); }
        @Override public Optional<MethodRef> findBySignature(String s) { return Optional.ofNullable(bySignature.get(s)); }
        @Override public Optional<String> sourceOf(MethodRef m) { return Optional.ofNullable(sources.get(m)); }
    }
}
