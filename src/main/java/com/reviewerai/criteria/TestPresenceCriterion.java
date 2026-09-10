package com.reviewerai.criteria;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.service.ProgressListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesure la présence de tests, sans interroger de modèle.
 *
 * <p>Le sujet demande de combiner des analyses déterministes et des analyses produites par
 * l'IA. Ce critère est un exemple du premier type, et il vaut mieux que le second pour cette
 * question précise : compter des fichiers de test est exact, reproductible, gratuit et
 * instantané. Demander à un modèle « ce projet a-t-il des tests ? » serait plus lent, plus
 * cher, et faux de temps en temps.
 *
 * <p>Ce que ce critère ne sait pas faire : juger si les tests sont <i>bons</i>. Il compte, il
 * ne lit pas. C'est une limite assumée — la qualité des tests relève d'un critère confié au
 * modèle.
 *
 * <p>Barème : le rapport entre le nombre de fichiers de test et le nombre de fichiers de
 * production. Un rapport de 0,5 — un test pour deux classes — vaut la note maximale ; c'est le
 * seuil couramment observé sur un projet correctement testé.
 */
public final class TestPresenceCriterion implements Criterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "tests";

    /** Rapport tests/production à partir duquel la note est maximale. */
    private static final double FULL_MARK_RATIO = 0.5;

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.deterministic(ID, "Présence de tests", """
                Rapport entre le nombre de fichiers de test et le nombre de classes de
                production, mesuré sans modèle.""");
    }

    @Override
    public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
        int mainFiles = project.ofKind(FileKind.JAVA_MAIN).size();
        int testFiles = project.ofKind(FileKind.JAVA_TEST).size();
        int testLines = project.ofKind(FileKind.JAVA_TEST).stream()
                .mapToInt(com.reviewerai.model.ProjectFile::lineCount).sum();

        if (mainFiles == 0) {
            return CriterionResult.failed(ID, descriptor().label(), descriptor().maxScore(),
                    "aucune classe Java de production trouvée");
        }

        double ratio = (double) testFiles / mainFiles;
        int score = (int) Math.round(Math.min(1.0, ratio / FULL_MARK_RATIO) * descriptor().maxScore());

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (testFiles == 0) {
            weaknesses.add("Aucun fichier de test n'a été trouvé dans le projet.");
            recommendations.add("Ajouter au moins un test par classe portant de la logique métier.");
        } else {
            strengths.add("%d fichier(s) de test pour %d classe(s) de production, soit un rapport de %.2f."
                    .formatted(testFiles, mainFiles, ratio));
            strengths.add("%d lignes de test au total.".formatted(testLines));
            if (ratio < FULL_MARK_RATIO) {
                weaknesses.add("Le rapport reste sous %.2f : une partie des classes n'est pas couverte."
                        .formatted(FULL_MARK_RATIO));
                recommendations.add("Cibler en priorité les classes sans test qui portent des règles métier.");
            }
        }

        String summary = testFiles == 0
                ? "Le projet ne contient aucun test automatisé."
                : "Le projet contient %d fichier(s) de test pour %d classe(s) de production."
                        .formatted(testFiles, mainFiles);

        return new CriterionResult(ID, descriptor().label(), score, descriptor().maxScore(),
                summary, List.copyOf(strengths), List.copyOf(weaknesses), List.copyOf(recommendations),
                List.of(), true);
    }
}
