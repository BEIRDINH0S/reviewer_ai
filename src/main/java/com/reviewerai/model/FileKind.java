package com.reviewerai.model;

/**
 * Nature d'un fichier du projet évalué.
 *
 * <p>Le sujet demande que l'application distingue les types de fichiers, et que tout ne soit
 * pas envoyé au modèle. Cette énumération est la base des deux : elle sert à afficher
 * l'arborescence, et à écrire des règles de sélection lisibles
 * ({@code KindFileSelector.only(JAVA_MAIN)}).
 *
 * <p>Le classement est fait par {@code com.reviewerai.project.FileClassifier}, à partir du
 * chemin seul : on ne lit jamais le contenu pour décider de ce qu'est un fichier.
 */
public enum FileKind {

    /** Source Java de production. */
    JAVA_MAIN("source Java", true),

    /** Source Java de test. */
    JAVA_TEST("test Java", true),

    /** Descripteur de construction : {@code pom.xml}, {@code build.gradle}… */
    BUILD("build", true),

    /** {@code Dockerfile}, {@code docker-compose.yml}… */
    DOCKER("docker", true),

    /** Configuration : {@code .properties}, {@code .yml}, {@code .json}… */
    CONFIG("configuration", true),

    /** Documentation : {@code .md}, {@code .adoc}, {@code .tex}… */
    DOCUMENTATION("documentation", true),

    /** Script exécutable : {@code .sh}, {@code .bat}, {@code .ps1}. */
    SCRIPT("script", true),

    /** Ressource embarquée qui n'entre dans aucune autre catégorie. */
    RESOURCE("ressource", false),

    /** Tout le reste : binaires, images, fichiers illisibles. */
    OTHER("autre", false);

    private final String label;
    private final boolean textual;

    FileKind(String label, boolean textual) {
        this.label = label;
        this.textual = textual;
    }

    /** Libellé français, affiché dans l'arborescence et dans le rapport. */
    public String label() {
        return label;
    }

    /**
     * Vrai si le contenu du fichier a du sens pour un humain — et donc pour le modèle.
     *
     * <p>Un fichier non textuel n'est jamais envoyé au LLM : ce serait des jetons dépensés
     * pour du bruit.
     */
    public boolean isTextual() {
        return textual;
    }

    /** Vrai s'il s'agit d'un source Java, de production ou de test. */
    public boolean isJava() {
        return this == JAVA_MAIN || this == JAVA_TEST;
    }
}
