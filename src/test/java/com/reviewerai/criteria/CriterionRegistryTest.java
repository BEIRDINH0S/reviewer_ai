package com.reviewerai.criteria;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.service.ProgressListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests du catalogue de critères, qui porte la sélection faite par l'utilisateur. */
class CriterionRegistryTest {

    /** Critère factice : le catalogue ne doit pas avoir besoin de vrais critères pour être testé. */
    private record FakeCriterion(String id, String label) implements Criterion {
        @Override
        public CriterionDescriptor descriptor() {
            return CriterionDescriptor.deterministic(id, label, "");
        }

        @Override
        public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
            return CriterionResult.failed(id, label, 10, "critère factice");
        }
    }

    private static CriterionRegistry registry() {
        return CriterionRegistry.of(
                new FakeCriterion("a", "Premier"),
                new FakeCriterion("b", "Deuxième"),
                new FakeCriterion("c", "Troisième"));
    }

    @Test
    @DisplayName("l'ordre d'inscription est conservé")
    void insertionOrderIsPreserved() {
        assertEquals(List.of("a", "b", "c"), registry().ids());
    }

    @Test
    @DisplayName("sans sélection, tous les critères sont retenus")
    void emptySelectionMeansAll() {
        assertEquals(3, registry().select(List.of()).size());
        assertEquals(3, registry().select(null).size());
    }

    @Test
    @DisplayName("la sélection suit l'ordre du catalogue, pas celui de la demande")
    void selectionFollowsCatalogueOrder() {
        var selected = registry().select(List.of("c", "a"));

        assertEquals(List.of("a", "c"), selected.stream().map(Criterion::id).toList());
    }

    @Test
    @DisplayName("un critère inconnu est refusé, avec la liste des critères valides")
    void unknownCriterionIsRejected() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> registry().select(List.of("inexistant")));

        assertTrue(exception.getMessage().contains("inexistant"));
        assertTrue(exception.getMessage().contains("Disponibles"));
    }

    @Test
    @DisplayName("deux critères de même identifiant sont refusés à la construction")
    void duplicateIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CriterionRegistry.of(new FakeCriterion("a", "X"), new FakeCriterion("a", "Y")));
    }

    @Test
    @DisplayName("un critère se retrouve par son identifiant")
    void criterionIsFoundById() {
        assertTrue(registry().byId("b").isPresent());
        assertTrue(registry().byId("zzz").isEmpty());
    }
}
