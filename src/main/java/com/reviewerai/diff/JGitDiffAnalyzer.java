package com.reviewerai.diff;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.model.ChangedMethod;

import java.nio.file.Path;
import java.util.List;

/**
 * Implémentation de la brique Diff avec JGit.
 *
 *
 * <p>Marche à suivre :
 * <ol>
 *   <li>Ouvrir le repo ({@code new FileRepositoryBuilder().setGitDir(repo.resolve(".git")...)}).
 *   <li>Résoudre {@code baseCommit} et {@code headCommit} en {@code RevCommit}.
 *   <li>Calculer le diff avec {@code DiffFormatter} et récupérer les {@code Edit} par fichier
 *       ({@code formatter.toFileHeader(entry).toEditList()}) — ce sont les plages de lignes modifiées.
 *   <li>Ne garder que les fichiers {@code .java} qui ne dépassent pas
 *       {@link EvaluationConfig#maxFileSizeBytes()}.
 *   <li>Parser la version HEAD de chaque fichier avec JavaParser, puis pour chaque déclaration de
 *       méthode, croiser sa plage de lignes avec les {@code Edit} : intersection non vide =
 *       méthode modifiée.
 *   <li>Construire le {@link ChangedMethod} avec
 *       {@link com.reviewerai.model.MethodRef#of} (ne pas fabriquer la signature à la main).
 * </ol>
 *
 * <p>Pièges connus :
 * <ul>
 *   <li>Un fichier renommé apparaît en {@code RENAME} : le chemin de base diffère du chemin HEAD.
 *   <li>Une méthode dont seule l'indentation change ressort comme modifiée ; c'est acceptable
 *       pour le prototype.
 *   <li>Un fichier qui ne parse pas ne doit PAS faire tomber la review : {@code try/catch} par
 *       fichier, on log et on continue.
 * </ul>
 */
public final class JGitDiffAnalyzer implements DiffAnalyzer {

    private final EvaluationConfig config;

    public JGitDiffAnalyzer(EvaluationConfig config) {
        this.config = config;
    }

    @Override
    public List<ChangedMethod> findChangedMethods(Path repo, String baseCommit, String headCommit) {
        throw new UnsupportedOperationException("JGitDiffAnalyzer : à implémenter");
    }
}
