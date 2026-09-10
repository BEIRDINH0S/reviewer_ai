package com.reviewerai.context;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.graph.CodeGraphBuilder;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.FileSelector;

import java.util.Objects;

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
 * <p>Marche à suivre pour l'implémentation :
 * <ol>
 *   <li>construire le graphe une seule fois par analyse — il coûte cher, et le reconstruire
 *       pour chacun des neuf critères serait le principal poste de dépense de l'outil.
 *       Le mémoriser dans un champ, en le construisant à la première demande ;
 *   <li>classer les méthodes par centralité : le nombre d'appelants est un indicateur simple
 *       et suffisant. Une méthode appelée par vingt autres est structurante, une méthode
 *       appelée par personne est probablement du code mort — ce qui est d'ailleurs un
 *       signalement en soi ;
 *   <li>pour chaque méthode retenue, prendre ses voisines jusqu'à
 *       {@code config.maxNeighborDepth()}, sans dépasser {@code config.maxNeighbors()} ;
 *   <li>convertir chaque {@code MethodContext} en {@link com.reviewerai.model.CodeExcerpt},
 *       en indiquant dans la raison le lien avec la méthode centrale — « appelle X »,
 *       « appelé par Y ». C'est ce qui permet au modèle de raisonner sur les relations ;
 *   <li>si le budget se resserre, dégrader en n'envoyant que la signature des voisines
 *       ({@code Neighbor.full == false}) plutôt que de retirer des voisines : connaître
 *       l'existence d'un lien vaut mieux que de l'ignorer ;
 *   <li>si le graphe est vide — projet non Java, sources illisibles — déléguer à
 *       {@link RepresentativeFileContextBuilder} plutôt que de renvoyer un contexte vide.
 * </ol>
 *
 */
public final class CallGraphContextBuilder implements ContextBuilder {

    private final EvaluationConfig config;
    private final CodeGraphBuilder graphBuilder;
    private final ContextBuilder fallback;

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
        return fallback.build(project, descriptor, selector);
    }
}
