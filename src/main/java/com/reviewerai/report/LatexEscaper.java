package com.reviewerai.report;

import java.util.Map;

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

    /**
     * Les caractères que {@code inputenc} ne sait pas rendre, et leur équivalent en toutes
     * lettres.
     *
     * <p>Le préambule charge {@code inputenc} et {@code fontenc T1}, qui couvrent le Latin-1 —
     * donc tous les accents français. Au-delà, {@code inputenc} s'arrête sur une erreur :
     * « Unicode character not set up for use with LaTeX ». Un simple {@code ≥} dans un texte du
     * modèle suffirait à rendre le rapport incompilable.
     *
     * <p>Le tiret cadratin figure ici alors qu'il est courant en français : il vaut U+2014,
     * donc hors Latin-1, et LaTeX l'écrit {@code ---}.
     */
    private static final Map<Character, String> BEYOND_LATIN1 = Map.ofEntries(
            Map.entry('\u2014', "---"),
            Map.entry('\u2013', "--"),
            Map.entry('\u2026', "..."),
            Map.entry('\u2019', "'"),
            Map.entry('\u201c', "``"),
            Map.entry('\u201d', "''"),
            Map.entry('\u2265', ">="),
            Map.entry('\u2264', "<="),
            Map.entry('\u2260', "!="),
            Map.entry('\u2192', "->"),
            Map.entry('\u2190', "<-"),
            Map.entry('\u00d7', "x"),
            Map.entry('\u2022', "-"),
            Map.entry('\u20ac', "EUR"));

    /** Remplace un caractère hors Latin-1 dont on n'a pas d'équivalent. */
    private static final char UNSUPPORTED = '?';

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
                default -> appendRenderable(sb, c);
            }
        }
        return sb.toString();
    }

    /**
     * Ajoute un caractère ordinaire, en garantissant que LaTeX saura l'imprimer.
     *
     * <p>Le Latin-1 passe tel quel : c'est ce que couvre le préambule, et cela suffit au
     * français. Au-delà, on traduit si l'on sait, et on remplace sinon. Un point
     * d'interrogation signale au lecteur qu'un caractère a été perdu — plus honnête que de le
     * supprimer en silence, et infiniment préférable à un rapport qui ne compile pas.
     */
    private static void appendRenderable(StringBuilder sb, char c) {
        if (c <= '\u00ff') {
            sb.append(c);
            return;
        }
        String replacement = BEYOND_LATIN1.get(c);
        sb.append(replacement == null ? String.valueOf(UNSUPPORTED) : replacement);
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
