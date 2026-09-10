package com.reviewerai.verify;

import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Enchaîne plusieurs {@link FindingVerifier}, la sortie de l'un alimentant le suivant.
 *
 * <p><b>Patron de conception : Composite</b> (structurel)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Comment appliquer plusieurs règles de filtrage sans que l'appelant ait à savoir
 *       combien il y en a, ni dans quel ordre ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Un composite implémente la même interface que les éléments qu'il regroupe. Les
 *       critères manipulent donc un vérificateur unique, qu'il y en ait un ou dix derrière.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Contrairement au Composite du cours, la structure est plate : une liste, pas un arbre.
 *       Un composite peut malgré tout en contenir un autre, puisqu'il est lui-même un
 *       {@link FindingVerifier}.</dd>
 * </dl>
 */
public final class CompositeFindingVerifier implements FindingVerifier {

    private final List<FindingVerifier> delegates;

    public CompositeFindingVerifier(List<FindingVerifier> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
    }

    public static CompositeFindingVerifier of(FindingVerifier... verifiers) {
        return new CompositeFindingVerifier(List.of(verifiers));
    }

    @Override
    public List<Finding> verify(List<Finding> findings, EvaluationContext context, ProjectSnapshot project) {
        List<Finding> current = findings;
        for (FindingVerifier delegate : delegates) {
            current = delegate.verify(current, context, project);
            if (current.isEmpty()) {
                return List.of(); // plus rien à filtrer, on évite les passages inutiles
            }
        }
        return current;
    }

    /** Nombre de règles enchaînées, utile aux tests. */
    public int size() {
        return delegates.size();
    }
}
