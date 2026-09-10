package com.reviewerai.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de l'échappement LaTeX.
 *
 * <p>Ce sont les tests les plus importants du package rapport. LaTeX est un langage
 * exécutable dont le compilateur sait lire des fichiers : un échappement défaillant transforme
 * le rapport en vecteur d'attaque, à partir d'un simple commentaire dans le projet évalué.
 */
class LatexEscaperTest {

    @Test
    @DisplayName("une commande LaTeX est neutralisée")
    void commandIsNeutralised() {
        String escaped = LatexEscaper.escape("\\input{/etc/passwd}");

        assertFalse(escaped.contains("\\input{"), "la commande ne doit plus être exécutable");
        assertTrue(escaped.contains("textbackslash"));
    }

    @Test
    @DisplayName("une tentative d'exécution système est neutralisée")
    void shellEscapeIsNeutralised() {
        String escaped = LatexEscaper.escape("\\write18{rm -rf /}");

        assertFalse(escaped.contains("\\write18"));
    }

    @Test
    @DisplayName("aucun caractère actif ne subsiste sans être précédé d'un antislash")
    void allActiveCharactersAreEscaped() {
        String escaped = LatexEscaper.escape("\\{}$&#^_~%");

        // Trois caractères sont remplacés par une commande dont les accolades vides font
        // partie de la syntaxe : on les retire avant de contrôler le reste.
        String remaining = escaped
                .replace("\\textbackslash{}", "")
                .replace("\\textasciicircum{}", "")
                .replace("\\textasciitilde{}", "");

        // Tout ce qui reste doit être une paire antislash + caractère actif.
        assertTrue(remaining.matches("(\\\\[{}$&#%_])*"),
                "caractère actif non échappé dans : " + escaped);
    }

    @Test
    @DisplayName("un pourcentage brut ne met pas le reste de la ligne en commentaire")
    void percentDoesNotCommentOutTheLine() {
        String escaped = LatexEscaper.escape("couverture 80% des classes");

        assertTrue(escaped.contains("\\%"));
        assertTrue(escaped.contains("des classes"), "le texte après le % doit survivre");
    }

    @Test
    @DisplayName("un saut de ligne devient une espace")
    void newlinesBecomeSpaces() {
        assertEquals("a b", LatexEscaper.escape("a\nb"));
        assertEquals("a b", LatexEscaper.escape("a\r\nb").replace("  ", " "));
    }

    @Test
    @DisplayName("un texte absent ou vide donne une chaîne vide")
    void nullAndEmptyAreSafe() {
        assertEquals("", LatexEscaper.escape(null));
        assertEquals("", LatexEscaper.escape(""));
    }

    @Test
    @DisplayName("un texte ordinaire traverse sans dommage")
    void plainTextSurvives() {
        assertEquals("Architecture correctement decoupee", LatexEscaper.escape("Architecture correctement decoupee"));
    }

    @Test
    @DisplayName("les accents français sont conservés")
    void accentsArePreserved() {
        assertEquals("Le découpage est cohérent", LatexEscaper.escape("Le découpage est cohérent"));
    }

    @Test
    @DisplayName("un caractère hors Latin-1 est traduit plutôt que laissé tel quel")
    void beyondLatin1IsTranslated() {
        // inputenc s'arrête sur une erreur devant ces caractères : le rapport ne compilerait pas.
        assertEquals(">= 0,50", LatexEscaper.escape("\u2265 0,50"));
        assertEquals("a --- b", LatexEscaper.escape("a \u2014 b"));
        assertEquals("...", LatexEscaper.escape("\u2026"));
    }

    @Test
    @DisplayName("un caractère exotique sans équivalent devient un point d'interrogation")
    void unknownSymbolBecomesAQuestionMark() {
        assertEquals("note ?", LatexEscaper.escape("note \u4e2d"),
                "mieux vaut perdre un caractère que produire un document incompilable");
    }

    @Test
    @DisplayName("un emoji ne fait pas échouer la compilation")
    void emojiIsReplaced() {
        // Un emoji est codé sur deux caractères Java ; les deux doivent être neutralisés.
        String escaped = LatexEscaper.escape("bravo \ud83d\ude00");

        assertFalse(escaped.codePoints().anyMatch(cp -> cp > 0xFF));
    }

    @Test
    @DisplayName("les accents français traversent sans dommage")
    void frenchAccentsSurviveTheLatin1Filter() {
        assertEquals("Le découpage est cohérent, à revoir çà et là",
                LatexEscaper.escape("Le découpage est cohérent, à revoir çà et là"));
    }

    @Test
    @DisplayName("une cellule de tableau trop longue est tronquée")
    void tableCellIsTruncated() {
        String escaped = LatexEscaper.escapeCell("x".repeat(200), 50);

        assertTrue(escaped.length() <= 51);
        assertTrue(escaped.endsWith("…"));
    }
}
