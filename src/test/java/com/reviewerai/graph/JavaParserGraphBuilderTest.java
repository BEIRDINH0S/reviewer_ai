package com.reviewerai.graph;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de la brique Graphe — à écrire par la personne 3.
 *
 * <p>Écrire les sources d'exemple dans un {@code @TempDir} plutôt que dans
 * {@code src/test/resources} : elles resteraient sinon compilées avec le projet.
 */
class JavaParserGraphBuilderTest {

    @Test
    @Disabled("à implémenter avec JavaParserGraphBuilder")
    @DisplayName("un appel entre deux méthodes crée une arête")
    void directCallCreatesEdge() {
    }

    @Test
    @Disabled("à implémenter avec JavaParserGraphBuilder")
    @DisplayName("les appelants sont retrouvés dans les deux sens")
    void callersAndCalleesAreSymmetric() {
    }

    @Test
    @Disabled("à implémenter avec JavaParserGraphBuilder")
    @DisplayName("un appel vers une bibliothèque externe est ignoré sans erreur")
    void unresolvableCallIsSkipped() {
    }

    @Test
    @Disabled("à implémenter avec JavaParserGraphBuilder")
    @DisplayName("un fichier illisible n'interrompt pas la construction")
    void brokenFileDoesNotStopBuild() {
    }
}
