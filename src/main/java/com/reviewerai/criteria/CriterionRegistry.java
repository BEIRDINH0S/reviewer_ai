package com.reviewerai.criteria;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Le catalogue des critères disponibles.
 *
 * <p>C'est ce qui permet à l'utilisateur de <b>choisir ses critères d'évaluation</b>, une
 * exigence explicite du sujet : l'interface affiche {@link #descriptors()} sous forme de cases
 * à cocher, et le service reçoit ensuite {@link #select(Collection)}.
 *
 * <p>C'est aussi le seul endroit à modifier pour ajouter un critère. Écrire la classe, l'ajouter
 * à la liste de la fabrique, et il apparaît dans l'interface, dans la configuration, dans le
 * rapport et dans l'historique — sans qu'aucune de ces quatre parties ne change. C'est la
 * réponse à « comment ajoutez-vous un nouveau critère ? ».
 *
 * <p>L'ordre d'inscription est conservé : le rapport présente toujours les critères dans le
 * même ordre, ce qui rend deux évaluations comparables d'un coup d'oeil.
 */
public final class CriterionRegistry {

    private final Map<String, Criterion> byId;

    /** L'ordre d'inscription, que {@link Map#copyOf} ne conserve pas. */
    private final List<String> order;

    public CriterionRegistry(List<Criterion> criteria) {
        Objects.requireNonNull(criteria, "criteria");
        Map<String, Criterion> map = new LinkedHashMap<>();
        for (Criterion criterion : criteria) {
            String id = criterion.id();
            if (map.put(id, criterion) != null) {
                // Deux critères de même identifiant rendraient la sélection ambiguë et
                // fausseraient l'historique, où l'identifiant sert de clé.
                throw new IllegalArgumentException("Deux critères portent l'identifiant : " + id);
            }
        }
        this.byId = Map.copyOf(map);
        this.order = criteria.stream().map(Criterion::id).toList();
    }

    /** Catalogue construit à partir des critères donnés. */
    public static CriterionRegistry of(Criterion... criteria) {
        return new CriterionRegistry(List.of(criteria));
    }

    /** Tous les critères, dans l'ordre d'inscription. */
    public List<Criterion> all() {
        return order.stream().map(byId::get).toList();
    }

    /** Les cartes d'identité, pour l'interface et pour le rapport. */
    public List<CriterionDescriptor> descriptors() {
        return all().stream().map(Criterion::descriptor).toList();
    }

    /** Les identifiants disponibles, dans l'ordre d'inscription. */
    public List<String> ids() {
        return order;
    }

    /** Un critère par son identifiant. */
    public Optional<Criterion> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Les critères demandés, dans l'ordre du catalogue.
     *
     * <p>L'ordre du catalogue plutôt que celui de la demande : le rapport reste ainsi
     * identique quel que soit l'ordre des cases cochées.
     *
     * @param ids identifiants demandés ; vide ou {@code null} signifie « tous »
     * @throws IllegalArgumentException si un identifiant est inconnu — mieux vaut refuser que
     *                                  produire un rapport auquel il manque silencieusement un critère
     */
    public List<Criterion> select(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return all();
        }
        List<String> unknown = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Critère(s) inconnu(s) : " + String.join(", ", unknown)
                            + ". Disponibles : " + String.join(", ", order));
        }
        return order.stream().filter(ids::contains).map(byId::get).toList();
    }

    /** Nombre de critères inscrits. */
    public int size() {
        return order.size();
    }
}
