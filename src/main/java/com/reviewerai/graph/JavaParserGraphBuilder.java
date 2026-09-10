package com.reviewerai.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.CodeGraph;
import com.reviewerai.model.MethodRef;
import com.reviewerai.util.SafeFiles;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Construit le graphe d'appel du repo avec JavaParser et son Symbol Solver, stocké dans JGraphT.
 *
 * <p><b>Le lien le plus direct entre le cours et le code</b> : JavaParser repose sur le patron
 * Visiteur ({@code VoidVisitorAdapter}), qu'on utilise ici parce que la bibliothèque l'impose,
 * pas pour le caser.
 *
 * <p>Deux passes. La première indexe chaque déclaration de méthode en {@link MethodRef}. La
 * seconde, pour chaque appel, tente de le résoudre : si la méthode appelée est l'une des nôtres,
 * une arête relie l'appelant à l'appelé.
 *
 * <p><b>Résolution sans classpath</b> — le vrai morceau. Le {@code CombinedTypeSolver} résout ce
 * qu'il peut : le JDK par réflexion, nos propres sources par leurs racines. Aucun
 * {@code JarTypeSolver} sur les dépendances : cela supposerait un build. Un appel dont le type
 * n'est pas résolu (dépendance externe) est ignoré en silence — c'est le compromis assumé.
 *
 * <p><b>Sécurité</b> : parsing uniquement. Jamais de compilation, jamais de résolution de
 * dépendances distantes. Les sources passent par {@code SafeFiles}. Un fichier piégé qui lève
 * une {@code Exception} ou même une {@code StackOverflowError} est écarté, sans emporter le
 * reste du graphe.
 */
public final class JavaParserGraphBuilder implements CodeGraphBuilder {

    private final EvaluationConfig config;

    public JavaParserGraphBuilder(EvaluationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public CodeGraph build(Path repo) {
        List<Path> javaFiles = SafeFiles.javaSources(repo, config.maxFileSizeBytes());
        if (javaFiles.isEmpty()) {
            return CodeGraph.empty();
        }

        JavaParser parser = configuredParser(repo);
        Graph<MethodRef, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        Map<String, MethodRef> bySignature = new HashMap<>();
        Map<MethodRef, String> sources = new HashMap<>();
        Map<MethodDeclaration, MethodRef> declarationNodes = new IdentityHashMap<>();
        List<CompilationUnit> units = new ArrayList<>();

        for (Path file : javaFiles) {
            parse(parser, file).ifPresent(unit -> {
                units.add(unit);
                indexDeclarations(unit, SafeFiles.toRepoRelative(repo, file),
                        graph, bySignature, sources, declarationNodes);
            });
        }
        if (bySignature.isEmpty()) {
            return CodeGraph.empty();
        }

        for (CompilationUnit unit : units) {
            linkCalls(unit, graph, bySignature, declarationNodes);
        }
        return new JGraphTCodeGraph(graph, bySignature, sources);
    }

    /** Un parseur muni d'un résolveur de symboles limité au JDK et aux sources du repo. */
    private static JavaParser configuredParser(Path repo) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        for (Path root : sourceRoots(repo)) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }
        ParserConfiguration configuration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        return new JavaParser(configuration);
    }

    /** La racine du repo, plus les emplacements de sources conventionnels s'ils existent. */
    private static List<Path> sourceRoots(Path repo) {
        List<Path> roots = new ArrayList<>();
        roots.add(repo);
        for (String candidate : List.of("src/main/java", "src/test/java", "src")) {
            Path path = repo.resolve(candidate);
            if (Files.isDirectory(path)) {
                roots.add(path);
            }
        }
        return roots;
    }

    /** Parse un fichier, en écartant tout ce qui pourrait faire échouer la construction entière. */
    private static Optional<CompilationUnit> parse(JavaParser parser, Path file) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            return result.isSuccessful() ? result.getResult() : Optional.empty();
        } catch (IOException | RuntimeException | StackOverflowError e) {
            return Optional.empty();
        }
    }

    private static void indexDeclarations(CompilationUnit unit, String relativePath,
                                          Graph<MethodRef, DefaultEdge> graph,
                                          Map<String, MethodRef> bySignature,
                                          Map<MethodRef, String> sources,
                                          Map<MethodDeclaration, MethodRef> declarationNodes) {
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            try {
                MethodRef ref = toMethodRef(method, relativePath);
                if (ref == null) {
                    continue;
                }
                graph.addVertex(ref);
                bySignature.putIfAbsent(ref.signature(), ref);
                sources.putIfAbsent(ref, method.toString());
                declarationNodes.put(method, ref);
            } catch (RuntimeException | StackOverflowError e) {
                // Une méthode non indexable ne fait pas tomber le fichier.
            }
        }
    }

    /**
     * Convertit une déclaration en {@link MethodRef}, en préférant les types résolus.
     *
     * <p>La signature d'une déclaration et celle d'un appel doivent se calculer de la même façon,
     * sinon aucune arête ne se forme. Les deux passent donc par les types pleinement qualifiés du
     * Symbol Solver quand il les résout ; à défaut, on retombe sur les types tels qu'écrits, ce
     * qui indexe quand même la méthode faute de pouvoir la relier finement.
     */
    private static MethodRef toMethodRef(MethodDeclaration method, String relativePath) {
        int start = method.getBegin().map(position -> position.line).orElse(0);
        int end = method.getEnd().map(position -> position.line).orElse(start);
        try {
            ResolvedMethodDeclaration resolved = method.resolve();
            return MethodRef.of(relativePath, resolved.declaringType().getQualifiedName(),
                    resolved.getName(), resolvedParameters(resolved), start, end);
        } catch (RuntimeException | StackOverflowError e) {
            String className = astClassName(method);
            if (className.isEmpty()) {
                return null;
            }
            List<String> parameters = method.getParameters().stream()
                    .map(parameter -> parameter.getType().asString()).toList();
            return MethodRef.of(relativePath, className, method.getNameAsString(), parameters, start, end);
        }
    }

    private static void linkCalls(CompilationUnit unit,
                                  Graph<MethodRef, DefaultEdge> graph,
                                  Map<String, MethodRef> bySignature,
                                  Map<MethodDeclaration, MethodRef> declarationNodes) {
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            MethodRef caller = call.findAncestor(MethodDeclaration.class)
                    .map(declarationNodes::get)
                    .orElse(null);
            if (caller == null) {
                continue;
            }
            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                String signature = MethodRef.signatureOf(resolved.declaringType().getQualifiedName(),
                        resolved.getName(), resolvedParameters(resolved));
                MethodRef callee = bySignature.get(signature);
                if (callee != null && !caller.equals(callee) && !graph.containsEdge(caller, callee)) {
                    graph.addEdge(caller, callee);
                }
            } catch (RuntimeException | StackOverflowError e) {
                // Appel vers une dépendance externe non résolue : arête ignorée en silence.
            }
        }
    }

    private static List<String> resolvedParameters(ResolvedMethodLikeDeclaration resolved) {
        List<String> parameters = new ArrayList<>();
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            parameters.add(resolved.getParam(i).getType().describe());
        }
        return parameters;
    }

    private static String astClassName(MethodDeclaration method) {
        return method.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                .orElse("");
    }
}
