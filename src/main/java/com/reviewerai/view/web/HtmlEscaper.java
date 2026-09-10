package com.reviewerai.view.web;

/**
 * Neutralise le HTML contenu dans un texte non fiable.
 *
 * <p>Les titres et explications affichés dans le tableau viennent du modèle, donc
 * indirectement du code analysé. Sans échappement, un {@code <script>} glissé dans un
 * commentaire de pull request s'exécuterait dans le navigateur de la personne qui relit.
 *
 * <p>Le pendant de {@link com.reviewerai.report.MarkdownEscaper} pour le rapport : même
 * problème, deux formats de sortie, donc deux échappements distincts.
 *
 * <p>Note : la page actuelle insère les valeurs via {@code textContent}, ce qui échappe déjà
 * côté navigateur. Cette classe reste la protection à appliquer dès qu'un texte est inséré
 * dans du HTML produit côté serveur, et sert de filet si la page évolue.
 */
public final class HtmlEscaper {

    private HtmlEscaper() {
    }

    /**
     * @param text texte non fiable, éventuellement {@code null}
     * @return le texte utilisable sans risque dans un contenu HTML ou un attribut
     */
    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                // Échappée aussi : sans elle, un texte inséré dans un attribut délimité par
                // des apostrophes pourrait en sortir.
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
