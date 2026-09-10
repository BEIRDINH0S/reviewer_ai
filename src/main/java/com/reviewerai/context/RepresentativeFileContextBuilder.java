package com.reviewerai.context;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.model.CodeExcerpt;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.project.FileSelector;
import com.reviewerai.util.SafeFiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stratégie de contexte par défaut : les fichiers les plus représentatifs, sous budget.
 *
 * <p>Le raisonnement tient en une phrase : à défaut de tout envoyer, on envoie ce qui porte le
 * plus d'information par jeton dépensé.
 *
 * <p><b>Classement</b> — les fichiers retenus par le critère sont triés par nombre de lignes
 * décroissant. Un fichier long concentre en général l'essentiel de la logique ; une classe de
 * quinze lignes n'apprend rien sur l'architecture. À nombre de lignes égal, l'ordre
 * alphabétique tranche, ce qui garantit un contexte reproductible d'une exécution à l'autre.
 *
 * <p><b>Budget</b> — on ajoute des fichiers tant que le budget de jetons le permet. Un fichier
 * trop gros pour ce qui reste n'est pas tronqué mais sauté, et on essaie le suivant : un
 * fichier coupé au milieu d'une méthode induit le modèle en erreur bien plus qu'il ne
 * l'informe.
 *
 * <p><b>Ce que cette stratégie ne fait pas</b> — elle ignore les liens entre fichiers. Pour un
 * critère qui juge le couplage, {@link CallGraphContextBuilder} donne un contexte nettement
 * meilleur. C'est une limite assumée, à signaler dans le rapport.
 */
public final class RepresentativeFileContextBuilder implements ContextBuilder {

    private final EvaluationConfig config;

    public RepresentativeFileContextBuilder(EvaluationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public EvaluationContext build(ProjectSnapshot project,
                                   CriterionDescriptor descriptor,
                                   FileSelector selector) {
        Path root = project.project().root();
        List<ProjectFile> candidates = project.files().stream()
                .filter(f -> f.kind().isTextual())
                .filter(selector::accepts)
                .sorted(Comparator.comparingInt(ProjectFile::lineCount).reversed()
                        .thenComparing(ProjectFile::path))
                .toList();

        List<CodeExcerpt> excerpts = new ArrayList<>();
        int used = 0;
        int rank = 0;

        for (ProjectFile file : candidates) {
            if (excerpts.size() >= config.maxFilesPerCriterion()) {
                break;
            }
            var content = SafeFiles.readText(root, root.resolve(file.path()), config.maxFileSizeBytes());
            if (content.isEmpty()) {
                continue; // fichier illisible : on passe, sans faire échouer le critère
            }
            CodeExcerpt excerpt = CodeExcerpt.wholeFile(file.path(), content.get(), reasonFor(file, ++rank));
            int cost = excerpt.estimatedTokens();
            if (used + cost > config.maxContextTokens()) {
                // Trop gros pour ce qu'il reste : on saute plutôt que de tronquer au milieu
                // d'une méthode, et on tente le fichier suivant, plus petit.
                continue;
            }
            excerpts.add(excerpt);
            used += cost;
        }

        return new EvaluationContext(
                descriptor.id(),
                project.project(),
                List.copyOf(excerpts),
                ProjectInventory.describe(project),
                used);
    }

    /**
     * La phrase qui accompagne l'extrait dans le prompt.
     *
     * <p>Dire au modèle pourquoi on lui montre ce fichier améliore nettement la pertinence de
     * sa réponse, pour un coût de quelques jetons.
     */
    private static String reasonFor(ProjectFile file, int rank) {
        return "%s, %d lignes — %s fichier le plus volumineux de la sélection"
                .formatted(file.kind().label(), file.lineCount(), rank == 1 ? "premier" : rank + "e");
    }
}
