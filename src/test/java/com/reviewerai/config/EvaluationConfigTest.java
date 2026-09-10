package com.reviewerai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests du monteur de configuration. */
class EvaluationConfigTest {

    private static EvaluationConfig.Builder minimal() {
        return EvaluationConfig.builder().projectSource(Path.of("."));
    }

    @Test
    @DisplayName("le projet à évaluer est obligatoire")
    void projectSourceIsRequired() {
        assertThrows(IllegalStateException.class, () -> EvaluationConfig.builder().build());
    }

    @Test
    @DisplayName("le serveur de modèle pointe sur la boucle locale par défaut")
    void modelServerIsLocalByDefault() {
        var config = minimal().build();

        assertTrue(config.ollamaBaseUrl().contains("127.0.0.1"),
                "exposer le serveur de modèle hors de la machine doit rester une décision explicite");
    }

    @Test
    @DisplayName("le rapport est un fichier LaTeX par défaut")
    void reportIsLatexByDefault() {
        assertEquals("evaluation.tex", minimal().build().reportFile().getFileName().toString());
    }

    @Test
    @DisplayName("une confiance minimale hors de [0,1] est refusée")
    void confidenceMustBeAProbability() {
        assertThrows(IllegalArgumentException.class, () -> minimal().minConfidence(1.5).build());
        assertThrows(IllegalArgumentException.class, () -> minimal().minConfidence(-0.1).build());
    }

    @Test
    @DisplayName("un budget de jetons nul est refusé")
    void tokenBudgetMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> minimal().maxContextTokens(0).build());
    }

    @Test
    @DisplayName("sans critère demandé, la liste est vide et signifie « tous »")
    void noCriteriaMeansAll() {
        assertTrue(minimal().build().criterionIds().isEmpty());
    }

    @Test
    @DisplayName("sans répertoire d'historique, rien n'est conservé")
    void historyIsAbsentByDefault() {
        assertTrue(minimal().build().history().isEmpty());
    }

    @Test
    @DisplayName("les motifs d'inclusion sont recopiés, pas partagés")
    void patternsAreDefensivelyCopied() {
        var mutable = new java.util.ArrayList<>(List.of("**/*.java"));
        var config = minimal().includePatterns(mutable).build();
        mutable.clear();

        assertEquals(1, config.includePatterns().size());
    }

    @Test
    @DisplayName("le résumé de configuration mentionne le modèle")
    void describeMentionsModel() {
        assertTrue(minimal().modelName("mon-modele").build().describe().contains("mon-modele"));
    }
}
