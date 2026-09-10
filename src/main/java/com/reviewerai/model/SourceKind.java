package com.reviewerai.model;

/**
 * D'où vient le projet évalué.
 *
 * <p>Le sujet demande trois provenances : une archive, un répertoire local et, en option, un
 * dépôt git. Chacune a sa classe de chargement dans {@code com.reviewerai.project} ; cette
 * énumération sert uniquement à tracer l'origine dans le rapport, qui doit identifier le
 * projet analysé.
 */
public enum SourceKind {

    /** Un répertoire déjà présent sur le disque. */
    DIRECTORY("répertoire"),

    /** Une archive ZIP, extraite dans un répertoire temporaire avant lecture. */
    ARCHIVE("archive"),

    /** Un dépôt git, lu à une révision donnée. */
    GIT("dépôt git");

    private final String label;

    SourceKind(String label) {
        this.label = label;
    }

    /** Libellé français, affiché dans le rapport et dans l'interface. */
    public String label() {
        return label;
    }
}
