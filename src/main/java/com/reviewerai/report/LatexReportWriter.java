package com.reviewerai.report;

import com.reviewerai.model.EvaluationResult;

/**
 * Produit le rapport d'évaluation au format LaTeX.
 *
 * <p>C'est une contrainte dure du sujet : l'application doit produire automatiquement un
 * fichier {@code .tex}, et le livrable comprend un {@code evaluation.tex} réellement engendré
 * par le programme.
 *
 * <p>Le document doit contenir, dans cet ordre :
 * <ol>
 *   <li>l'identification du projet évalué — {@code result.project().describe()} ;
 *   <li>la date d'analyse — {@code result.analysedAt()} ;
 *   <li>la configuration utilisée — {@code result.configLabel()} ;
 *   <li>le modèle interrogé et le nombre d'appels — {@code result.modelName()},
 *       {@code result.llmCalls()} ;
 *   <li>un tableau récapitulatif : une ligne par critère, note et maximum, puis la note
 *       globale sur 20 ({@code result.overallScore()}) ;
 *   <li>une section par critère : appréciation, points forts, points faibles, recommandations,
 *       et les signalements localisés ;
 *   <li>les critères non évalués, avec leur motif — la récupération partielle doit se voir ;
 *   <li>une synthèse générale.
 * </ol>
 *
 * <p><b>Le document est construit ici, pas par le modèle.</b> Le sujet l'exige : le LLM
 * remplit des champs, il ne dessine pas le rapport. Rien dans cette classe ne dépend de ce
 * qu'a répondu le modèle, sinon le contenu des champs.
 *
 * <p><b>Sécurité — non négociable</b> : tout texte issu de {@link EvaluationResult} passe par
 * {@link LatexEscaper#escape}. LaTeX est un langage exécutable et son compilateur lit des
 * fichiers ; un {@code \input} non échappé dans un titre de signalement suffirait à faire
 * fuiter un fichier de la machine dans le PDF produit. Aucune exception, aucun champ « de
 * confiance ».
 *
 * <p>Le préambule reste volontairement minimal — {@code article}, {@code inputenc},
 * {@code longtable}, {@code hyperref} — pour que le fichier compile avec une installation
 * LaTeX ordinaire, y compris celle du conteneur.
 *
 */
public final class LatexReportWriter implements ReportWriter {

    @Override
    public String render(EvaluationResult result) {
        throw new UnsupportedOperationException("LatexReportWriter : à implémenter");
    }

    @Override
    public String fileExtension() {
        return "tex";
    }

    @Override
    public String formatName() {
        return "LaTeX";
    }
}
