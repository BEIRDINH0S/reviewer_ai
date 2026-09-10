package com.reviewerai.graph;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.CodeGraph;
import com.reviewerai.model.MethodRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la brique Graphe.
 *
 * <p>Les sources d'exemple sont écrites dans un {@code @TempDir} plutôt que dans
 * {@code src/test/resources} : elles seraient sinon compilées avec le projet. Chaque projet
 * jouet met deux méthodes dans un même paquet pour que la résolution sans classpath aboutisse.
 */
class JavaParserGraphBuilderTest {

    private CodeGraph buildGraphFor(Path repo) {
        EvaluationConfig config = EvaluationConfig.builder().projectSource(repo).build();
        return new JavaParserGraphBuilder(config).build(repo);
    }

    /** Écrit un fichier source sous {@code demo/} pour coller au paquet déclaré. */
    private static void writeSource(Path repo, String simpleName, String body) throws IOException {
        Path pkg = repo.resolve("demo");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve(simpleName + ".java"), body);
    }

    @Test
    @DisplayName("un appel entre deux méthodes crée une arête")
    void directCallCreatesEdge(@TempDir Path repo) throws IOException {
        writeSource(repo, "A", "package demo;\npublic class A {\n  void run() { B.help(); }\n}\n");
        writeSource(repo, "B", "package demo;\npublic class B {\n  static void help() {}\n}\n");

        CodeGraph graph = buildGraphFor(repo);

        MethodRef run = graph.findBySignature("demo.A#run()").orElseThrow();
        MethodRef help = graph.findBySignature("demo.B#help()").orElseThrow();
        assertTrue(graph.callees(run).contains(help), "run appelle help : l'arête doit exister");
    }

    @Test
    @DisplayName("les appelants sont retrouvés dans les deux sens")
    void callersAndCalleesAreSymmetric(@TempDir Path repo) throws IOException {
        writeSource(repo, "A", "package demo;\npublic class A {\n  void run() { B.help(); }\n}\n");
        writeSource(repo, "B", "package demo;\npublic class B {\n  static void help() {}\n}\n");

        CodeGraph graph = buildGraphFor(repo);
        MethodRef run = graph.findBySignature("demo.A#run()").orElseThrow();
        MethodRef help = graph.findBySignature("demo.B#help()").orElseThrow();

        assertTrue(graph.callees(run).contains(help), "run -> help en sortant");
        assertTrue(graph.callers(help).contains(run), "help <- run en entrant");
    }

    @Test
    @DisplayName("un appel vers une bibliothèque externe est ignoré sans erreur")
    void unresolvableCallIsSkipped(@TempDir Path repo) throws IOException {
        // Inconnue.gone() ne se résout nulle part : l'arête est ignorée, mais l'appel interne
        // à help() doit quand même se former, et la construction ne doit pas échouer.
        writeSource(repo, "A", "package demo;\npublic class A {\n"
                + "  void run() { B.help(); Inconnue.gone(); }\n}\n");
        writeSource(repo, "B", "package demo;\npublic class B {\n  static void help() {}\n}\n");

        CodeGraph graph = buildGraphFor(repo);

        MethodRef run = graph.findBySignature("demo.A#run()").orElseThrow();
        MethodRef help = graph.findBySignature("demo.B#help()").orElseThrow();
        assertTrue(graph.callees(run).contains(help), "l'appel résolu doit rester, malgré l'appel externe");
        assertFalse(graph.methods().isEmpty(), "la construction a abouti");
    }

    @Test
    @DisplayName("un fichier illisible n'interrompt pas la construction")
    void brokenFileDoesNotStopBuild(@TempDir Path repo) throws IOException {
        writeSource(repo, "A", "package demo;\npublic class A {\n  void run() {}\n}\n");
        writeSource(repo, "Casse", "ceci n'est pas du Java valide {{{ (((");

        CodeGraph graph = buildGraphFor(repo);

        assertNotNull(graph.findBySignature("demo.A#run()").orElse(null),
                "le fichier valide doit être indexé malgré le fichier cassé");
    }

    @Test
    @DisplayName("un repo sans source parsable donne un graphe vide")
    void unparsableRepoYieldsEmptyGraph(@TempDir Path repo) throws IOException {
        writeSource(repo, "Casse", "pas du tout du Java {{{");

        assertTrue(buildGraphFor(repo).methods().isEmpty());
    }
}
