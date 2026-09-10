package com.reviewerai.verify;

import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectSnapshot;

import java.util.List;

/**
 * Écarte les signalements qui désignent un endroit inexistant.
 *
 * <p>Trois contrôles, du plus grossier au plus fin :
 * <ul>
 *   <li>le fichier cité existe-t-il dans le projet ? Un modèle qui invente un chemin a
 *       probablement inventé le problème qui va avec ;
 *   <li>ce fichier faisait-il partie du contexte envoyé ? Signaler un problème dans un fichier
 *       qu'on ne lui a pas montré signifie que le modèle a extrapolé ;
 *   <li>la ligne citée existe-t-elle dans ce fichier ? Les petits modèles citent régulièrement
 *       des numéros de ligne qui n'ont aucun rapport.
 * </ul>
 *
 * <p>Un signalement sans fichier est conservé : un critère comme l'architecture décrit
 * légitimement un problème d'ensemble, sans endroit précis à montrer.
 *
 * <p>Une ligne hors bornes ne fait pas rejeter le signalement, seulement disparaître la ligne :
 * le problème décrit peut être réel même si le modèle s'est trompé de numéro.
 *
 * <p>C'est autant une règle de sécurité que de qualité — c'est ici qu'on détecte qu'un modèle
 * a suivi une consigne glissée dans le code évalué plutôt que de juger ce code.
 */
public final class KnownLocationVerifier implements FindingVerifier {

    @Override
    public List<Finding> verify(List<Finding> findings, EvaluationContext context, ProjectSnapshot project) {
        List<String> shown = context.filePaths();
        return findings.stream()
                .filter(f -> !f.location().hasFile() || project.byPath(f.location().filePath()).isPresent())
                .filter(f -> !f.location().hasFile() || shown.contains(f.location().filePath()))
                .map(f -> clampLine(f, project))
                .toList();
    }

    /** Efface le numéro de ligne s'il sort du fichier, sans écarter le signalement. */
    private static Finding clampLine(Finding finding, ProjectSnapshot project) {
        if (!finding.location().hasLine() || !finding.location().hasFile()) {
            return finding;
        }
        int lines = project.byPath(finding.location().filePath())
                .map(ProjectFile::lineCount)
                .orElse(0);
        if (finding.location().line() <= lines) {
            return finding;
        }
        var withoutLine = new com.reviewerai.model.CodeLocation(
                finding.location().filePath(), finding.location().symbol(), -1);
        return new Finding(withoutLine, finding.severity(), finding.title(),
                finding.explanation(), finding.confidence());
    }
}
