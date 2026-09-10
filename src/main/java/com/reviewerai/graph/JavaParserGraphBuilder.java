package com.reviewerai.graph;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.CodeGraph;

import java.nio.file.Path;

/**
 * Implémentation de la brique Graphe avec JavaParser + Symbol Solver, stockage JGraphT.
 *
 *
 * <p>Marche à suivre :
 * <ol>
 *   <li>Lister les {@code .java} du repo avec {@link com.reviewerai.util.SafeFiles#javaSources}
 *       (qui applique déjà la limite de taille et ignore les liens symboliques).
 *   <li>Configurer JavaParser avec un {@code CombinedTypeSolver} contenant uniquement
 *       {@code ReflectionTypeSolver} (JDK) et un {@code JavaParserTypeSolver} par racine de
 *       sources. PAS de {@code JarTypeSolver} sur les dépendances : cela supposerait un build.
 *   <li>Premier passage : indexer toutes les déclarations de méthode en {@code MethodRef}.
 *   <li>Second passage : pour chaque {@code MethodCallExpr}, tenter {@code resolve()}.
 *       En cas d'échec (dépendance externe non résolue), on ignore l'arête silencieusement —
 *       c'est le compromis assumé du mode sans classpath.
 *   <li>Stocker le tout dans un {@code DefaultDirectedGraph<MethodRef, DefaultEdge>} et exposer
 *       l'ensemble derrière {@link CodeGraph}.
 * </ol>
 *
 * <p>Sécurité : {@code try/catch} par fichier obligatoire. Un fichier piégé (imbrication
 * profonde) peut lever un {@code StackOverflowError} — le rattraper aussi, c'est une
 * {@code Error} et non une {@code Exception}.
 *
 * <p>Perf : le second passage domine le temps de construction. Si le repo de test est gros,
 * paralléliser sur les fichiers et ne synchroniser que l'ajout des arêtes.
 */
public final class JavaParserGraphBuilder implements CodeGraphBuilder {

    private final EvaluationConfig config;

    public JavaParserGraphBuilder(EvaluationConfig config) {
        this.config = config;
    }

    @Override
    public CodeGraph build(Path repo) {
        throw new UnsupportedOperationException("JavaParserGraphBuilder : à implémenter");
    }
}
