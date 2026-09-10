package com.reviewerai.criteria;

import com.reviewerai.model.CodeLocation;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.Severity;
import com.reviewerai.service.ProgressListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Juge l'organisation du projet, sans interroger de modèle.
 *
 * <p>Premier critère de la liste du sujet, et le plus facile à vérifier sans lire une ligne de
 * code : un descripteur de construction, une disposition de sources conventionnelle, une
 * stratégie de déploiement, et aucun fichier démesuré.
 *
 * <p>Le critère produit aussi de vrais signalements localisés — les fichiers trop longs — ce
 * qui montre qu'une analyse déterministe n'est pas condamnée à ne donner qu'une note.
 */
public final class ProjectStructureCriterion implements Criterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "project-structure";

    /** Au-delà, une classe fait presque toujours plus d'une chose. */
    private static final int LONG_FILE_LINES = 600;

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.deterministic(ID, "Organisation du projet", """
                Présence d'un descripteur de construction, disposition des sources, stratégie
                de déploiement et taille des fichiers, mesurées sans modèle.""");
    }

    @Override
    public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
        boolean hasBuild = !project.ofKind(FileKind.BUILD).isEmpty();
        boolean hasDocker = !project.ofKind(FileKind.DOCKER).isEmpty();
        boolean standardLayout = project.ofKind(FileKind.JAVA_MAIN).stream()
                .anyMatch(f -> f.path().startsWith("src/main/java/"));
        List<ProjectFile> longFiles = project.ofKind(FileKind.JAVA_MAIN).stream()
                .filter(f -> f.lineCount() > LONG_FILE_LINES)
                .toList();

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Barème : 3 points pour la construction, 3 pour la disposition, 2 pour le déploiement,
        // 2 pour l'absence de fichier démesuré.
        int score = 0;
        if (hasBuild) {
            score += 3;
            strengths.add("Le projet déclare sa construction (%s)."
                    .formatted(project.ofKind(FileKind.BUILD).getFirst().fileName()));
        } else {
            weaknesses.add("Aucun descripteur de construction : le projet ne se compile pas seul.");
            recommendations.add("Ajouter un pom.xml ou un build.gradle.");
        }

        if (standardLayout) {
            score += 3;
            strengths.add("Les sources suivent la disposition conventionnelle src/main/java.");
        } else {
            weaknesses.add("Les sources ne suivent pas la disposition src/main/java attendue.");
        }

        if (hasDocker) {
            score += 2;
            strengths.add("Une stratégie de déploiement est fournie (Docker).");
        } else {
            recommendations.add("Fournir un Dockerfile pour rendre le déploiement reproductible.");
        }

        List<Finding> findings = new ArrayList<>();
        if (longFiles.isEmpty()) {
            score += 2;
            strengths.add("Aucun fichier ne dépasse %d lignes.".formatted(LONG_FILE_LINES));
        } else {
            weaknesses.add("%d fichier(s) dépassent %d lignes."
                    .formatted(longFiles.size(), LONG_FILE_LINES));
            recommendations.add("Découper les fichiers les plus longs : ils portent probablement "
                    + "plusieurs responsabilités.");
            for (ProjectFile file : longFiles) {
                findings.add(new Finding(
                        CodeLocation.ofFile(file.path()),
                        Severity.LOW,
                        "Fichier de %d lignes".formatted(file.lineCount()),
                        "Au-delà de %d lignes, une classe porte presque toujours plus d'une responsabilité."
                                .formatted(LONG_FILE_LINES),
                        1.0)); // mesure exacte, pas une estimation : la confiance est totale
            }
        }

        return new CriterionResult(ID, descriptor().label(),
                Math.min(score, descriptor().maxScore()), descriptor().maxScore(),
                "Organisation évaluée sur %d fichier(s).".formatted(project.files().size()),
                List.copyOf(strengths), List.copyOf(weaknesses), List.copyOf(recommendations),
                List.copyOf(findings), true);
    }
}
