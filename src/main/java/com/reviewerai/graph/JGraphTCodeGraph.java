package com.reviewerai.graph;

import com.reviewerai.model.CodeGraph;
import com.reviewerai.model.MethodRef;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Graphe d'appel stocké dans un {@link Graph} JGraphT dirigé.
 *
 * <p>Emballé derrière {@link CodeGraph} pour que la brique Contexte ne dépende pas de JGraphT :
 * elle ne voit que des {@link MethodRef} et des ensembles. Immuable une fois construit — le
 * builder assemble tout, puis le graphe n'est plus que consulté.
 *
 * <p>L'égalité d'un {@link MethodRef} ne porte que sur sa signature : deux références à la même
 * méthode sont donc le même sommet, même si leurs numéros de ligne diffèrent.
 */
final class JGraphTCodeGraph implements CodeGraph {

    private final Graph<MethodRef, DefaultEdge> graph;
    private final Map<String, MethodRef> bySignature;
    private final Map<MethodRef, String> sources;

    JGraphTCodeGraph(Graph<MethodRef, DefaultEdge> graph,
                     Map<String, MethodRef> bySignature,
                     Map<MethodRef, String> sources) {
        this.graph = graph;
        this.bySignature = Map.copyOf(bySignature);
        this.sources = Map.copyOf(sources);
    }

    @Override
    public Set<MethodRef> methods() {
        return Set.copyOf(graph.vertexSet());
    }

    @Override
    public Set<MethodRef> callees(MethodRef method) {
        if (!graph.containsVertex(method)) {
            return Set.of();
        }
        Set<MethodRef> result = new LinkedHashSet<>();
        for (DefaultEdge edge : graph.outgoingEdgesOf(method)) {
            result.add(graph.getEdgeTarget(edge));
        }
        return result;
    }

    @Override
    public Set<MethodRef> callers(MethodRef method) {
        if (!graph.containsVertex(method)) {
            return Set.of();
        }
        Set<MethodRef> result = new LinkedHashSet<>();
        for (DefaultEdge edge : graph.incomingEdgesOf(method)) {
            result.add(graph.getEdgeSource(edge));
        }
        return result;
    }

    @Override
    public Optional<MethodRef> findBySignature(String signature) {
        return Optional.ofNullable(bySignature.get(signature));
    }

    @Override
    public Optional<String> sourceOf(MethodRef method) {
        return Optional.ofNullable(sources.get(method));
    }
}
