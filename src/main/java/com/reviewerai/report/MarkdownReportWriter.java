package com.reviewerai.report;

import com.reviewerai.model.EvaluationResult;

/**
 * Produit le rapport d'évaluation au format Markdown.
 *
 * <p>Format de travail, pas format de rendu : c'est {@link LatexReportWriter} que le sujet
 * exige. Celui-ci se lit directement dans un terminal ou sur une page web, sans compilation,
 * ce qui le rend bien plus commode pendant le développement et pour l'affichage dans
 * l'interface.
 *
 * <p>Son existence sert aussi de démonstration : deux formats, une seule logique métier, aucun
 * code partagé à dupliquer. C'est ce que le patron Stratégie apporte concrètement ici.
 *
 * <p>Structure visée :
 * <pre>
 * # Évaluation de mon-projet
 * Analysé le 10/09/2026 · modèle qwen2.5-coder:7b · 9 appels · note globale 14,2/20
 *
 * | Critère | Note | Max |
 * |---|---|---|
 * | Architecture et modularité | 8 | 10 |
 *
 * ## Architecture et modularité — 8/10
 * Appréciation générale.
 * **Points forts** · **Points faibles** · **Recommandations**
 * ### Signalements
 * - **HIGH** — src/App.java:42 — Titre
 * </pre>
 *
 * <p><b>Sécurité</b> : tout texte provenant du modèle passe par
 * {@link MarkdownEscaper#escape}. Sans cela, un signalement contenant
 * {@code [clique ici](http://…)} deviendrait un lien actif dans le rapport publié.
 *
 */
public final class MarkdownReportWriter implements ReportWriter {

    @Override
    public String render(EvaluationResult result) {
        throw new UnsupportedOperationException("MarkdownReportWriter : à implémenter");
    }

    @Override
    public String fileExtension() {
        return "md";
    }

    @Override
    public String formatName() {
        return "Markdown";
    }
}
