package com.reviewerai.report;

/**
 * Neutralise le Markdown contenu dans un texte non fiable.
 *
 * <p>Le texte des signalements vient du LLM, donc indirectement du code analysé. Un commentaire
 * malveillant dans une PR peut pousser le modèle à produire un lien, une image distante ou du
 * HTML, qui deviendraient actifs une fois le rapport publié.
 *
 * <p>La règle appliquée est volontairement brutale : on échappe tous les caractères actifs du
 * Markdown. Un rapport un peu moins joli est préférable à un rapport exploitable.
 */
public final class MarkdownEscaper {

    private static final String ACTIVE_CHARS = "\\`*_{}[]()#+-.!|<>";

    private MarkdownEscaper() {
    }

    /**
     * @param text texte non fiable, éventuellement {@code null}
     * @return le texte échappé, sans saut de ligne susceptible de casser la structure du rapport
     */
    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c == '\n') {
                sb.append(' '); // un saut de ligne casserait la puce Markdown en cours
                continue;
            }
            if (ACTIVE_CHARS.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
