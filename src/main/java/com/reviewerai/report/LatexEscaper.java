package com.reviewerai.report;

/**
 * Neutralise le LaTeX contenu dans un texte non fiable.
 *
 * <p><b>C'est la classe la plus sensible du package, et de loin.</b> Le Markdown mal échappé
 * donne un rapport laid ; le LaTeX mal échappé donne bien pire. LaTeX est un langage de
 * programmation complet, et son compilateur lit des fichiers : une commande
 * {@code \input{/etc/passwd}} glissée dans le texte d'un signalement recopierait le contenu de
 * ce fichier dans le PDF produit. {@code \write18} peut même, selon la configuration,
 * exécuter une commande système.
 *
 * <p>Or ce texte vient du modèle, donc indirectement du code évalué, qui est fourni par un
 * tiers. Le chemin d'attaque complet tient en une ligne : un commentaire dans le code évalué,
 * un modèle qui le recopie dans une explication, un rapport compilé — et le compilateur exécute.
 *
 * <p>La règle appliquée est donc brutale : les dix caractères actifs de LaTeX sont échappés
 * sans exception, aucune commande n'est laissée passer, aucune n'est reconnue comme légitime.
 * Un rapport un peu moins joli est infiniment préférable à un rapport qui exécute du code.
 */
public final class LatexEscaper {

    private LatexEscaper() {
    }

    /**
     * @param text texte non fiable, éventuellement {@code null}
     * @return le texte utilisable sans risque dans un document LaTeX
     */
    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                // La barre oblique inverse d'abord : c'est elle qui ouvre toute commande.
                case '\\' -> sb.append("\\textbackslash{}");
                case '{' -> sb.append("\\{");
                case '}' -> sb.append("\\}");
                case '$' -> sb.append("\\$");
                case '&' -> sb.append("\\&");
                case '#' -> sb.append("\\#");
                case '^' -> sb.append("\\textasciicircum{}");
                case '_' -> sb.append("\\_");
                case '~' -> sb.append("\\textasciitilde{}");
                case '%' -> sb.append("\\%");
                // Un saut de ligne dans une cellule de tableau casse la compilation.
                case '\n', '\r' -> sb.append(' ');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Échappe un texte destiné à une cellule de tableau, en le tronquant si besoin.
     *
     * <p>Une cellule de trois cents caractères déborde de la page et rend le tableau
     * illisible. Le texte complet reste disponible dans le corps du rapport.
     */
    public static String escapeCell(String text, int maxLength) {
        String escaped = escape(text);
        return escaped.length() <= maxLength ? escaped : escaped.substring(0, maxLength) + "…";
    }
}
