/**
 * Brique Rapport — met en forme le résultat d'une évaluation.
 *
 * <p>Le sujet impose de séparer trois choses, et ce package matérialise la troisième :
 * les résultats de l'évaluation, leur représentation Java
 * ({@link com.reviewerai.model.EvaluationResult}), et leur transformation en document.
 *
 * <p>Conséquence directe : <b>la structure du document ne dépend pas du modèle</b>. Un
 * {@link com.reviewerai.report.ReportWriter} ne reçoit qu'une structure de données. Le modèle
 * a rempli des champs ; c'est ce package, et lui seul, qui décide de la forme.
 *
 * <p>{@link com.reviewerai.report.LatexReportWriter} est le format exigé par le sujet ;
 * {@link com.reviewerai.report.MarkdownReportWriter} est le format de travail. Ajouter du HTML
 * ou du JSON pour une intégration continue demande une classe, et rien d'autre.
 *
 * <p><b>Sécurité</b> : c'est la dernière barrière avant le lecteur. Chaque format a son
 * échappement — {@link com.reviewerai.report.LatexEscaper} est le plus critique, LaTeX étant
 * un langage exécutable dont le compilateur sait lire des fichiers.
 *
 */
package com.reviewerai.report;
