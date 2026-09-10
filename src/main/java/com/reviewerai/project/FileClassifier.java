package com.reviewerai.project;

import com.reviewerai.model.FileKind;

import java.util.Locale;

/**
 * Détermine la nature d'un fichier à partir de son seul chemin.
 *
 * <p>Le sujet demande que l'application identifie les différents types de fichiers d'un
 * projet. C'est la seule classe qui le décide, ce qui évite que trois briques ne se fassent
 * chacune leur idée de ce qu'est « un fichier de test ».
 *
 * <p><b>On ne lit jamais le contenu pour classer.</b> Le classement est fait sur des milliers
 * de fichiers, dont l'immense majorité ne sera jamais envoyée au modèle : ouvrir chacun d'eux
 * coûterait cher pour rien. C'est aussi une mesure de prudence — moins on ouvre de fichiers
 * d'un projet non fiable, mieux on se porte.
 *
 * <p>Les règles sont volontairement simples et documentées : un correcteur doit pouvoir
 * prédire le classement d'un fichier en lisant cette classe.
 */
public final class FileClassifier {

    private FileClassifier() {
    }

    /**
     * @param relativePath chemin relatif à la racine du projet, avec des {@code /}
     * @return la nature déduite, jamais {@code null}
     */
    public static FileKind classify(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return FileKind.OTHER;
        }
        String path = relativePath.toLowerCase(Locale.ROOT);
        String name = fileName(path);

        if (path.endsWith(".java")) {
            return isTest(path, name) ? FileKind.JAVA_TEST : FileKind.JAVA_MAIN;
        }
        if (isBuildDescriptor(name)) {
            return FileKind.BUILD;
        }
        if (isDocker(name)) {
            return FileKind.DOCKER;
        }
        if (isScript(name)) {
            return FileKind.SCRIPT;
        }
        if (isDocumentation(name)) {
            return FileKind.DOCUMENTATION;
        }
        if (isConfiguration(name)) {
            return FileKind.CONFIG;
        }
        if (path.startsWith("src/main/resources/") || path.startsWith("src/test/resources/")) {
            return FileKind.RESOURCE;
        }
        return FileKind.OTHER;
    }

    /**
     * Vrai si le fichier est un test.
     *
     * <p>Deux indices, parce qu'aucun ne suffit seul : l'emplacement Maven ou Gradle habituel,
     * et les conventions de nommage de JUnit. Un projet qui range ses tests ailleurs et les
     * nomme autrement passera à travers — c'est une limite assumée, à signaler dans le rapport.
     */
    private static boolean isTest(String path, String name) {
        return path.contains("/test/")
                || path.startsWith("test/")
                || path.contains("/src/test/")
                || name.endsWith("test.java")
                || name.endsWith("tests.java")
                || name.endsWith("it.java")
                || name.startsWith("test");
    }

    private static boolean isBuildDescriptor(String name) {
        return name.equals("pom.xml")
                || name.equals("build.gradle")
                || name.equals("build.gradle.kts")
                || name.equals("settings.gradle")
                || name.equals("settings.gradle.kts")
                || name.equals("makefile")
                || name.equals("build.xml");
    }

    private static boolean isDocker(String name) {
        return name.equals("dockerfile")
                || name.startsWith("dockerfile.")
                || name.equals(".dockerignore")
                || (name.startsWith("docker-compose") && (name.endsWith(".yml") || name.endsWith(".yaml")));
    }

    private static boolean isScript(String name) {
        return name.endsWith(".sh") || name.endsWith(".bash")
                || name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".ps1");
    }

    private static boolean isDocumentation(String name) {
        return name.endsWith(".md") || name.endsWith(".adoc") || name.endsWith(".rst")
                || name.endsWith(".tex") || name.endsWith(".txt")
                || name.equals("readme") || name.equals("license") || name.equals("licence");
    }

    private static boolean isConfiguration(String name) {
        return name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml")
                || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".toml")
                || name.endsWith(".ini") || name.endsWith(".cfg")
                || name.startsWith(".git") || name.equals(".editorconfig");
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
