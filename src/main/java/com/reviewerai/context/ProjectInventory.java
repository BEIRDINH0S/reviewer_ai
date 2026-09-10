package com.reviewerai.context;

import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectSnapshot;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Résume un projet en quelques lignes de chiffres.
 *
 * <p>Ce résumé accompagne <b>tous</b> les contextes envoyés au modèle, et c'est un choix
 * délibéré : il coûte une trentaine de jetons et donne la vue d'ensemble que les extraits
 * seuls ne donnent jamais. Sans lui, un modèle à qui l'on montre huit fichiers ignore s'il en
 * voit la totalité du projet ou un centième, et juge l'architecture d'un projet de trois cents
 * classes comme s'il en avait huit.
 */
public final class ProjectInventory {

    private ProjectInventory() {
    }

    /**
     * @param project le projet évalué
     * @return un résumé en texte brut, prêt à être inséré dans un prompt
     */
    public static String describe(ProjectSnapshot project) {
        Map<FileKind, Integer> counts = project.countByKind();
        String repartition = counts.entrySet().stream()
                .map(e -> "%d %s".formatted(e.getValue(), e.getKey().label()))
                .collect(Collectors.joining(", "));

        return """
               Projet : %s
               Fichiers retenus : %d (%s)
               Lignes de code Java de production : %d
               Paquets Java : %d"""
                .formatted(
                        project.project().describe(),
                        project.files().size(),
                        repartition.isEmpty() ? "aucun" : repartition,
                        project.mainJavaLines(),
                        packageCount(project));
    }

    private static long packageCount(ProjectSnapshot project) {
        return project.ofKind(FileKind.JAVA_MAIN).stream()
                .map(com.reviewerai.model.ProjectFile::directory)
                .distinct()
                .count();
    }
}
