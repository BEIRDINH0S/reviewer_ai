package com.reviewerai.model;

import java.util.Objects;

/**
 * Un problème précis relevé par un critère.
 *
 * <p>Un {@link CriterionResult} porte la note et l'appréciation d'ensemble ; un
 * {@code Finding} porte le détail localisé qui justifie cette note. Le rapport affiche les
 * deux : la note pour le survol, les signalements pour l'action.
 *
 * <p><b>Sécurité</b> : {@link #title} et {@link #explanation} viennent du modèle, donc
 * indirectement du code analysé, qui est une donnée non fiable. Ils doivent être échappés
 * avant tout affichage ({@code MarkdownEscaper}, {@code LatexEscaper}, {@code HtmlEscaper}).
 *
 * @param location    où se situe le problème
 * @param severity    gravité estimée
 * @param title       résumé en une phrase
 * @param explanation ce qui ne va pas, et dans quel cas cela pose problème
 * @param confidence  confiance dans le signalement, entre 0.0 et 1.0
 */
public record Finding(
        CodeLocation location,
        Severity severity,
        String title,
        String explanation,
        double confidence) {

    /** Longueur au-delà de laquelle un texte du modèle est tronqué (cf. injection de prompt). */
    public static final int MAX_TEXT_LENGTH = 2000;

    public Finding {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(severity, "severity");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence hors [0,1] : " + confidence);
        }
        title = truncate(title);
        explanation = truncate(explanation);
    }

    /** Signalement portant sur le projet entier, sans fichier précis. */
    public static Finding projectWide(Severity severity, String title, String explanation, double confidence) {
        return new Finding(CodeLocation.PROJECT_WIDE, severity, title, explanation, confidence);
    }

    /**
     * Coupe un texte trop long.
     *
     * <p>Un modèle poussé par une injection de prompt peut recracher un fichier entier. Le
     * rapport n'a pas à devenir le véhicule de ce contenu.
     */
    private static String truncate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "…";
    }
}
