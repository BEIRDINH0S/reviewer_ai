package com.reviewerai.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de {@link MarkdownEscaper}.
 *
 * <p>Ce sont des tests de sécurité : ils vérifient qu'un texte venant du modèle ne peut pas
 * produire de Markdown actif dans le rapport.
 */
class MarkdownEscaperTest {

    @Test
    @DisplayName("un lien Markdown est neutralisé")
    void escapesLinks() {
        String escaped = MarkdownEscaper.escape("[clique ici](http://exemple.invalide)");

        assertFalse(escaped.contains("]("), "le lien ne doit plus être cliquable");
        assertTrue(escaped.contains("\\["));
    }

    @Test
    @DisplayName("les sauts de ligne deviennent des espaces")
    void flattensNewlines() {
        assertEquals("a b", MarkdownEscaper.escape("a\nb"));
        assertEquals("a b", MarkdownEscaper.escape("a\r\nb"));
    }

    @Test
    @DisplayName("null et chaîne vide donnent une chaîne vide")
    void handlesEmptyInput() {
        assertEquals("", MarkdownEscaper.escape(null));
        assertEquals("", MarkdownEscaper.escape(""));
    }

    @Test
    @DisplayName("un texte ordinaire reste lisible")
    void plainTextSurvives() {
        assertEquals("La variable est nulle ici", MarkdownEscaper.escape("La variable est nulle ici"));
    }
}
