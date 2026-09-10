package com.reviewerai.diff;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests de la brique Diff — à écrire par la personne 2.
 *
 * <p>Créer les dépôts de test dans un {@code @TempDir} avec l'API JGit ({@code Git.init()},
 * {@code Git.commit()}) : aucun dépôt figé à versionner, et chaque test part d'un état propre.
 */
class JGitDiffAnalyzerTest {

    @Test
    @Disabled("à implémenter avec JGitDiffAnalyzer")
    @DisplayName("une méthode dont le corps change est signalée comme modifiée")
    void detectsModifiedMethod() {
    }

    @Test
    @Disabled("à implémenter avec JGitDiffAnalyzer")
    @DisplayName("une méthode ajoutée est signalée comme ajoutée")
    void detectsAddedMethod() {
    }

    @Test
    @Disabled("à implémenter avec JGitDiffAnalyzer")
    @DisplayName("une méthode non touchée du même fichier n'est pas signalée")
    void ignoresUntouchedMethodInSameFile() {
    }

    @Test
    @Disabled("à implémenter avec JGitDiffAnalyzer")
    @DisplayName("un fichier qui ne compile pas ne fait pas échouer l'analyse")
    void malformedFileDoesNotBreakAnalysis() {
    }

    @Test
    @Disabled("à implémenter avec JGitDiffAnalyzer")
    @DisplayName("les fichiers qui ne sont pas du Java sont ignorés")
    void ignoresNonJavaFiles() {
    }
}
