package com.reviewerai.context;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.graph.CodeGraphBuilder;
import com.reviewerai.model.CodeExcerpt;
import com.reviewerai.model.CodeGraph;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.MethodRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.FileClassifier;
import com.reviewerai.project.FileSelector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stratégie de contexte fine : les méthodes centrales et leur voisinage dans le graphe d'appel.
 *
 * <p>C'est la variante la plus ambitieuse du projet, et celle qui justifie la brique Graphe.
 * Là où {@link RepresentativeFileContextBuilder} envoie des fichiers entiers en espérant qu'ils
 * soient représentatifs, celle-ci envoie exactement ce qui se tient : une méthode importante,
 * ceux qui l'appellent, et ce qu'elle appelle.
 *
 * <p>L'intérêt dépasse l'économie de jetons. Pour juger le couplage ou le respect des
 * principes SOLID, ce sont les <b>relations</b> entre classes qui comptent, et un ensemble de
 * fichiers pris isolément ne les montre pas. Un modèle qui voit une méthode et ses six voisines
 * peut dire quelque chose de sensé sur leurs dépendances ; le même modèle devant huit fichiers
 * sans lien apparent ne le peut pas.
 *
 * <p>Le plafond d'extraits est {@code maxExcerptsPerCriterion}, pas {@code maxFilesPerCriterion} :
 * l'unité envoyée ici est la méthode, qui pèse une centaine de jetons là où un fichier en pèse un
 * millier. Appliquer le même compte aux deux unités laissait quatre cinquièmes du budget inutilisés.
 *
 * <p>Le graphe est construit <b>une seule fois par projet</b> puis mémorisé : le reconstruire
 * pour chacun des neuf critères serait le principal poste de dépense de l'outil. La centralité
 * d'une méthode est mesurée par son nombre d'appelants ; on part des plus centrales et on
 * descend jusqu'à épuiser le budget. Si le budget se resserre, une voisine est réduite à sa
 * signature plutôt que retirée : connaître l'existence d'un lien vaut mieux que l'ignorer. Un
 * graphe vide fait déléguer au repli plutôt que de renvoyer un contexte vide.
 *
 * <p><b>Seul le code de production est retenu</b>, en méthode centrale comme en voisine. Un
 * helper de test est appelé par toutes les méthodes {@code @Test} de son fichier, ce qui lui
 * donne un degré entrant qu'aucune méthode de production n'atteint : sans ce filtre, la tête du
 * classement n'est faite que de ces valeurs aberrantes. Le filtre porte sur la sélection, pas
 * sur le graphe — un appelant de test reste une information exploitable (une méthode
 * structurante que rien ne teste), et l'effacer du graphe la perdrait définitivement.
 *
 * <p>Le résultat est <b>déterministe</b> : centrales et voisines sont départagées par leur
 * signature, donc deux exécutions sur le même projet produisent exactement le même contexte.
 */
public final class CallGraphContextBuilder implements ContextBuilder {

    private final EvaluationConfig config;
    private final CodeGraphBuilder graphBuilder;
    private final ContextBuilder fallback;

    private CodeGraph cachedGraph;
    private Path cachedRoot;

    /**
     * @param config       les budgets de contexte
     * @param graphBuilder la brique Graphe
     * @param fallback     stratégie de repli quand le graphe n'apporte rien
     */
    public CallGraphContextBuilder(EvaluationConfig config,
                                   CodeGraphBuilder graphBuilder,
                                   ContextBuilder fallback) {
        this.config = Objects.requireNonNull(config, "config");
        this.graphBuilder = Objects.requireNonNull(graphBuilder, "graphBuilder");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public EvaluationContext build(ProjectSnapshot project,
                                   CriterionDescriptor descriptor,
                                   FileSelector selector) {
        CodeGraph graph = graphFor(project);
        if (graph.methods().isEmpty()) {
            return fallback.build(project, descriptor, selector);
        }
        List<CodeExcerpt> excerpts = selectExcerpts(graph);
        if (excerpts.isEmpty()) {
            // Le graphe a des méthodes mais aucune source exploitable : mieux vaut le repli qu'un
            // contexte vide, qui ferait rendre le critère « non évalué ».
            return fallback.build(project, descriptor, selector);
        }
        int used = excerpts.stream().mapToInt(CodeExcerpt::estimatedTokens).sum();
        return new EvaluationContext(descriptor.id(), project.project(), excerpts,
                ProjectInventory.describe(project), used);
    }

    /** Le graphe du projet, construit à la première demande puis mémorisé pour les critères suivants. */
    private synchronized CodeGraph graphFor(ProjectSnapshot project) {
        Path root = project.project().root();
        if (cachedGraph == null || !root.equals(cachedRoot)) {
            cachedGraph = graphBuilder.build(root);
            cachedRoot = root;
        }
        return cachedGraph;
    }

    /**
     * Choisit les extraits : les méthodes les plus appelées d'abord, chacune entourée de ses
     * voisines, jusqu'à épuiser le budget de jetons.
     */
    private List<CodeExcerpt> selectExcerpts(CodeGraph graph) {
        List<MethodRef> central = graph.methods().stream()
                .filter(CallGraphContextBuilder::isProduction)
                .sorted(byCentrality(graph))
                .toList();
        List<CodeExcerpt> excerpts = new ArrayList<>();
        Set<String> included = new HashSet<>();
        int used = 0;

        for (MethodRef target : central) {
            if (used >= config.maxContextTokens() || excerpts.size() >= config.maxExcerptsPerCriterion()) {
                break;
            }
            String source = graph.sourceOf(target).orElse("");
            if (source.isEmpty() || !included.add(target.signature())) {
                continue;
            }
            CodeExcerpt centralExcerpt = excerpt(target, source,
                    "méthode structurante — " + graph.callers(target).size() + " appelant(s)");
            if (used + centralExcerpt.estimatedTokens() > config.maxContextTokens()) {
                break; // même la méthode centrale ne tient plus : inutile de continuer
            }
            excerpts.add(centralExcerpt);
            used += centralExcerpt.estimatedTokens();
            used = addNeighbors(graph, target, excerpts, included, used);
        }
        return excerpts;
    }

    /** Ajoute les voisines de {@code target} tant que le budget le permet, en dégradant au besoin. */
    private int addNeighbors(CodeGraph graph, MethodRef target, List<CodeExcerpt> excerpts,
                             Set<String> included, int used) {
        for (Candidate neighbor : neighborsOf(graph, target)) {
            if (used >= config.maxContextTokens() || excerpts.size() >= config.maxExcerptsPerCriterion()) {
                break;
            }
            if (!included.add(neighbor.method().signature())) {
                continue;
            }
            CodeExcerpt full = excerpt(neighbor.method(), neighbor.body(), neighbor.reason(target));
            if (used + full.estimatedTokens() <= config.maxContextTokens()) {
                excerpts.add(full);
                used += full.estimatedTokens();
            } else {
                CodeExcerpt signatureOnly = excerpt(neighbor.method(), neighbor.method().signature(),
                        neighbor.reason(target) + " (signature seule)");
                if (used + signatureOnly.estimatedTokens() <= config.maxContextTokens()) {
                    excerpts.add(signatureOnly);
                    used += signatureOnly.estimatedTokens();
                }
            }
        }
        return used;
    }

    /**
     * Le voisinage d'une méthode, en largeur jusqu'à {@code maxNeighborDepth}, plafonné à
     * {@code maxNeighbors}. Les voisines sont visitées par ordre de signature, ce qui rend le
     * résultat reproductible.
     */
    private List<Candidate> neighborsOf(CodeGraph graph, MethodRef target) {
        List<Candidate> neighbors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(target.signature());
        List<MethodRef> frontier = List.of(target);

        for (int depth = 0; depth < config.maxNeighborDepth(); depth++) {
            List<MethodRef> next = new ArrayList<>();
            for (MethodRef node : frontier) {
                if (collect(graph, node, graph.callees(node), true, neighbors, seen, next)) {
                    return neighbors;
                }
                if (collect(graph, node, graph.callers(node), false, neighbors, seen, next)) {
                    return neighbors;
                }
            }
            frontier = next;
        }
        return neighbors;
    }

    /** Ajoute des voisines d'un même type ; renvoie {@code true} si le plafond est atteint. */
    private boolean collect(CodeGraph graph, MethodRef node, Set<MethodRef> related, boolean calledByNode,
                            List<Candidate> neighbors, Set<String> seen, List<MethodRef> next) {
        for (MethodRef method : sortedBySignature(related)) {
            if (neighbors.size() >= config.maxNeighbors()) {
                return true;
            }
            if (!isProduction(method)) {
                // Écartée en tant qu'extrait, mais laissée dans le parcours : elle a été vue,
                // donc elle ne sera pas reproposée à la couche suivante.
                seen.add(method.signature());
                continue;
            }
            if (seen.add(method.signature())) {
                neighbors.add(new Candidate(method, calledByNode, graph.sourceOf(method).orElse(method.signature())));
                next.add(method);
            }
        }
        return false;
    }

    /**
     * Vrai si la méthode appartient au code de production.
     *
     * <p>Le classement passe par {@link FileClassifier}, seul endroit du projet qui décide ce
     * qu'est un fichier de test : un {@code contains("src/test")} écrit ici se tromperait sur
     * une disposition non conventionnelle, et ferait une deuxième définition à maintenir.
     */
    private static boolean isProduction(MethodRef method) {
        return FileClassifier.classify(method.filePath()) == FileKind.JAVA_MAIN;
    }

    private static Comparator<MethodRef> byCentrality(CodeGraph graph) {
        return Comparator.comparingInt((MethodRef method) -> graph.callers(method).size()).reversed()
                .thenComparing(MethodRef::signature);
    }

    private static List<MethodRef> sortedBySignature(Set<MethodRef> methods) {
        return methods.stream().sorted(Comparator.comparing(MethodRef::signature)).toList();
    }

    private static CodeExcerpt excerpt(MethodRef method, String content, String reason) {
        return new CodeExcerpt(method.filePath(), method.startLine(), method.endLine(), content, reason);
    }

    /**
     * Une voisine retenue, avec ce qu'il faut pour l'inscrire dans le contexte.
     *
     * @param calledByTarget vrai si la méthode centrale appelle cette voisine, faux si l'inverse
     */
    private record Candidate(MethodRef method, boolean calledByTarget, String body) {
        String reason(MethodRef target) {
            return calledByTarget
                    ? "appelée par " + target.displayName()
                    : "appelle " + target.displayName();
        }
    }
}
