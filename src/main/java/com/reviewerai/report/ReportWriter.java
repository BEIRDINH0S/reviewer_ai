package com.reviewerai.report;

import com.reviewerai.model.EvaluationResult;

/**
 * Met en forme le résultat d'une évaluation.
 *
 * <p><b>Patron de conception : Stratégie</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet impose un rapport LaTeX, mais un rapport Markdown est bien plus commode
 *       pendant le développement, et une sortie HTML ou JSON aurait son usage. Comment
 *       produire plusieurs formats sans que le contrôleur ni les vues aient à les connaître ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une interface, une implémentation par format. Le contrôleur reçoit un
 *       {@code ReportWriter} et ignore lequel. Ajouter un format est une pure addition.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Le sujet exige que la structure du document ne dépende pas du modèle. C'est garanti
 *       ici par construction : un {@code ReportWriter} ne reçoit qu'un
 *       {@link EvaluationResult}, une structure de données Java. Le modèle a rempli des
 *       champs ; c'est ce code, et lui seul, qui décide de la forme du document.</dd>
 * </dl>
 *
 * <p><b>Sécurité</b> : toute implémentation doit échapper les textes issus du modèle selon les
 * règles de son format. C'est la dernière barrière avant que ce contenu ne parvienne à un
 * lecteur — ou à un compilateur LaTeX.
 */
public interface ReportWriter {

    /**
     * @param result le résultat complet de l'évaluation
     * @return le document formaté, prêt à être écrit sur disque
     */
    String render(EvaluationResult result);

    /** Extension de fichier associée, sans le point. */
    String fileExtension();

    /** Nom du format, affiché dans l'interface. */
    String formatName();
}
