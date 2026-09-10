package com.reviewerai.criteria;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.service.ProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mesure la documentation présente dans le projet, sans interroger de modèle.
 *
 * <p>Trois indices, chacun vérifiable sur le seul inventaire des fichiers :
 * <ul>
 *   <li>un README à la racine — sans lui, personne ne sait par où commencer ;
 *   <li>des fichiers de documentation en nombre raisonnable au regard de la taille du projet ;
 *   <li>des {@code package-info.java}, qui indiquent une documentation pensée par paquet et
 *       non ajoutée après coup.
 * </ul>
 *
 * <p>Comme {@link TestPresenceCriterion}, ce critère compte sans lire. La <i>qualité</i> de la
 * documentation — une Javadoc qui explique pourquoi plutôt que quoi — relève du modèle.
 */
public final class DocumentationCriterion implements Criterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "documentation";

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.deterministic(ID, "Documentation", """
                Présence d'un README, de fichiers de documentation et de package-info,
                mesurée sans modèle.""");
    }

    @Override
    public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
        boolean hasReadme = project.files().stream()
                .anyMatch(f -> f.directory().isEmpty()
                        && f.fileName().toLowerCase(Locale.ROOT).startsWith("readme"));
        List<ProjectFile> docs = project.ofKind(FileKind.DOCUMENTATION);
        long packageInfos = project.ofKind(FileKind.JAVA_MAIN).stream()
                .filter(f -> f.fileName().equals("package-info.java"))
                .count();
        long packages = project.ofKind(FileKind.JAVA_MAIN).stream()
                .map(ProjectFile::directory)
                .distinct()
                .count();

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Barème : 4 points pour le README, 3 pour la documentation, 3 pour les package-info.
        int score = 0;
        if (hasReadme) {
            score += 4;
            strengths.add("Un README est présent à la racine du projet.");
        } else {
            weaknesses.add("Aucun README à la racine : le projet ne se présente pas lui-même.");
            recommendations.add("Ajouter un README expliquant comment compiler, configurer et lancer le projet.");
        }

        if (docs.size() >= 3) {
            score += 3;
            strengths.add("%d fichier(s) de documentation.".formatted(docs.size()));
        } else if (!docs.isEmpty()) {
            score += 1;
            weaknesses.add("La documentation se limite à %d fichier(s).".formatted(docs.size()));
        } else {
            weaknesses.add("Aucun fichier de documentation en dehors du code.");
        }

        if (packages > 0) {
            double coverage = (double) packageInfos / packages;
            score += (int) Math.round(Math.min(1.0, coverage) * 3);
            if (coverage >= 0.8) {
                strengths.add("%d paquet(s) sur %d disposent d'un package-info."
                        .formatted(packageInfos, packages));
            } else {
                weaknesses.add("Seuls %d paquet(s) sur %d disposent d'un package-info."
                        .formatted(packageInfos, packages));
                recommendations.add("Ajouter un package-info.java par paquet, expliquant son rôle.");
            }
        }

        String summary = hasReadme
                ? "Le projet est documenté : README présent, %d fichier(s) de documentation."
                        .formatted(docs.size())
                : "La documentation est incomplète : aucun README à la racine.";

        return new CriterionResult(ID, descriptor().label(),
                Math.min(score, descriptor().maxScore()), descriptor().maxScore(),
                summary, List.copyOf(strengths), List.copyOf(weaknesses), List.copyOf(recommendations),
                List.of(), true);
    }
}
