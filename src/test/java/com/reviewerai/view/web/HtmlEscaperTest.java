package com.reviewerai.view.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de {@link HtmlEscaper}.
 *
 * <p>Tests de sécurité : ils vérifient qu'un texte venant du modèle ne peut pas devenir du
 * HTML actif dans le navigateur.
 */
class HtmlEscaperTest {

    @Test
    @DisplayName("une balise script est neutralisée")
    void escapesScriptTag() {
        String escaped = HtmlEscaper.escape("<script>alert(1)</script>");

        assertFalse(escaped.contains("<script>"));
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", escaped);
    }

    @Test
    @DisplayName("les guillemets et apostrophes sont échappés")
    void escapesQuotes() {
        assertEquals("&quot;a&quot; &#39;b&#39;", HtmlEscaper.escape("\"a\" 'b'"));
    }

    @Test
    @DisplayName("l'esperluette est échappée en premier, sans double échappement")
    void escapesAmpersandOnce() {
        assertEquals("&amp;lt;", HtmlEscaper.escape("&lt;"));
    }

    @Test
    @DisplayName("null et chaîne vide donnent une chaîne vide")
    void handlesEmptyInput() {
        assertEquals("", HtmlEscaper.escape(null));
        assertEquals("", HtmlEscaper.escape(""));
    }

    @Test
    @DisplayName("un texte ordinaire reste intact")
    void plainTextSurvives() {
        assertEquals("La variable peut être nulle", HtmlEscaper.escape("La variable peut être nulle"));
    }
}
