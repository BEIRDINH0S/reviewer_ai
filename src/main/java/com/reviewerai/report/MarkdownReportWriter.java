package com.reviewerai.report;

import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.Finding;
import com.reviewerai.model.Severity;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Produit le rapport d'évaluation au format Markdown.
 *
 * <p>Format de travail, pas format de rendu : c'est {@link LatexReportWriter} que le sujet
 * exige. Celui-ci se lit directement dans un terminal ou sur une page web, sans compilation,
 * ce qui le rend bien plus commode pendant le développement et pour l'affichage dans
 * l'interface.
 *
 * <p>Son existence sert aussi de démonstration : deux formats, une seule logique métier, aucun
 * code partagé à dupliquer. C'est ce que le patron Stratégie apporte concrètement ici — la
 * logique de calcul (notes, critères évalués, note globale) vit dans {@link EvaluationResult},
 * les deux writers ne font que la mettre en forme.
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

    /** Le document est destiné à des lecteurs francophones : séparateur décimal virgule. */
    private static final Locale LOCALE = Locale.FRENCH;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", LOCALE);

    @Override
    public String render(EvaluationResult result) {
        Objects.requireNonNull(result, "result");

        StringBuilder md = new StringBuilder(4096);
        appendHeader(md, result);
        appendSummary(md, result);
        appendCriteria(md, result);
        appendUnevaluated(md, result);
        appendWarnings(md, result);
        return md.toString();
    }

    @Override
    public String fileExtension() {
        return "md";
    }

    @Override
    public String formatName() {
        return "Markdown";
    }

    /**
     * Le titre et la ligne de contexte.
     *
     * <p>Reprend ce que le sujet exige de retrouver dans le rapport : identification du projet,
     * date, modèle interrogé, configuration.
     */
    private static void appendHeader(StringBuilder md, EvaluationResult result) {
        // Le nom du projet vient d'un répertoire potentiellement hostile : on l'échappe. Le
        // modèle, la configuration, la provenance et la révision sont fournis par l'opérateur ou
        // par l'outil, pas par le projet évalué : les échapper ne ferait que barrer inutilement
        // de barres obliques un nom comme « qwen2.5-coder ».
        md.append("# Évaluation de ").append(MarkdownEscaper.escape(result.project().name())).append("\n\n");
        md.append("Analysé le ").append(formatDate(result))
                .append(" · modèle ").append(result.modelName())
                .append(" · ").append(result.llmCalls()).append(" appel(s) au modèle")
                .append(" · ").append(formatDuration(result.duration()))
                .append(" · note globale ").append(formatScore(result.overallScore())).append("/20\n\n");
        md.append("Provenance : ").append(result.project().origin().label());
        if (!result.project().revision().isEmpty()) {
            md.append(" · révision ").append(result.project().shortRevision());
        }
        md.append("  \nConfiguration : ").append(result.configLabel()).append("\n\n");
    }

    /**
     * La synthèse : le tableau des notes, puis l'appréciation d'ensemble.
     *
     * <p>Cette appréciation est <b>calculée à partir des notes</b>, pas demandée au modèle :
     * elle ne peut donc pas contredire le tableau qui la précède.
     */
    private static void appendSummary(StringBuilder md, EvaluationResult result) {
        md.append("| Critère | Note | Max |\n|---|---|---|\n");
        for (CriterionResult criterion : result.criteria()) {
            // Un critère non évalué n'entre pas dans le total : afficher son maximum ferait
            // croire à une erreur de calcul.
            String score = criterion.evaluated() ? String.valueOf(criterion.score()) : "—";
            String max = criterion.evaluated() ? String.valueOf(criterion.maxScore()) : "—";
            md.append("| ").append(cell(criterion.label()))
                    .append(" | ").append(score)
                    .append(" | ").append(max).append(" |\n");
        }
        md.append("| **Total** | **").append(result.totalScore())
                .append("** | **").append(result.totalMaxScore()).append("** |\n\n");

        md.append("**Note globale : ").append(formatScore(result.overallScore())).append("/20**\n\n");
        md.append(overallComment(result)).append("\n\n");
    }

    /**
     * L'appréciation d'ensemble, déduite des notes.
     *
     * <p>Mentionne explicitement les critères manquants : un lecteur qui ignore qu'une partie de
     * l'évaluation n'a pas abouti lit une note qui ne veut pas dire ce qu'il croit.
     */
    private static String overallComment(EvaluationResult result) {
        int evaluated = result.evaluatedCriteria().size();
        int total = result.criteria().size();
        long findings = result.allFindings().size();

        StringBuilder comment = new StringBuilder(256);
        comment.append("L'évaluation a porté sur %d critère(s) sur %d, et relevé %d signalement(s) : "
                        .formatted(evaluated, total, findings))
                .append("%d de gravité haute, %d moyenne, %d basse."
                        .formatted(result.countBySeverity(Severity.HIGH),
                                result.countBySeverity(Severity.MEDIUM),
                                result.countBySeverity(Severity.LOW)));
        if (result.isPartial()) {
            comment.append(" Attention : ").append(total - evaluated)
                    .append(" critère(s) n'ont pas pu être évalués et n'entrent pas dans la note globale.");
        }
        return comment.toString();
    }

    /** Une section par critère évalué. */
    private static void appendCriteria(StringBuilder md, EvaluationResult result) {
        for (CriterionResult criterion : result.evaluatedCriteria()) {
            md.append("## ").append(MarkdownEscaper.escape(criterion.label()))
                    .append(" — ").append(criterion.score()).append('/').append(criterion.maxScore())
                    .append("\n\n");
            if (!criterion.summary().isEmpty()) {
                md.append(MarkdownEscaper.escape(criterion.summary())).append("\n\n");
            }
            appendItemList(md, "Points forts", criterion.strengths());
            appendItemList(md, "Points faibles", criterion.weaknesses());
            appendItemList(md, "Recommandations", criterion.recommendations());
            appendFindings(md, criterion.findings());
        }
    }

    /** Une liste à puces, ou rien du tout si elle est vide. */
    private static void appendItemList(StringBuilder md, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        md.append("**").append(title).append("**\n\n");
        for (String item : items) {
            md.append("- ").append(MarkdownEscaper.escape(item)).append('\n');
        }
        md.append('\n');
    }

    /**
     * Les signalements localisés qui appuient la note.
     *
     * <p>Le titre et l'explication viennent du modèle : ils sont échappés, sinon un titre
     * contenant {@code [texte](url)} deviendrait un lien actif dans le rapport publié.
     */
    private static void appendFindings(StringBuilder md, List<Finding> findings) {
        if (findings.isEmpty()) {
            return;
        }
        md.append("### Signalements\n\n");
        for (Finding finding : findings) {
            // L'emplacement va dans une portée de code : le Markdown y est déjà inerte, l'échapper
            // n'ajouterait que des barres obliques inutiles dans un chemin. On neutralise seulement
            // le caractère qui pourrait en sortir. Le titre et l'explication, eux, viennent du
            // modèle et sont de la prose : ils passent par l'échappement complet.
            md.append("- **").append(finding.severity().name()).append("** — `")
                    .append(codeSpan(finding.location().describe())).append("` — ")
                    .append(MarkdownEscaper.escape(finding.title()));
            if (!finding.explanation().isEmpty()) {
                md.append(" : ").append(MarkdownEscaper.escape(finding.explanation()));
            }
            md.append('\n');
        }
        md.append('\n');
    }

    /** Contenu sûr pour une portée de code : on retire le backtick, seul caractère qui l'ouvre ou la ferme. */
    private static String codeSpan(String text) {
        return text.replace('`', '\'');
    }

    /**
     * Les critères qui n'ont pas abouti, et pourquoi.
     *
     * <p>Le sujet demande une récupération partielle ; encore faut-il qu'elle se voie. Faire
     * disparaître un critère en panne laisserait croire qu'il a été jugé.
     */
    private static void appendUnevaluated(StringBuilder md, EvaluationResult result) {
        List<CriterionResult> unevaluated = result.criteria().stream()
                .filter(criterion -> !criterion.evaluated())
                .toList();
        if (unevaluated.isEmpty()) {
            return;
        }
        md.append("## Critères non évalués\n\n");
        for (CriterionResult criterion : unevaluated) {
            md.append("- **").append(MarkdownEscaper.escape(criterion.label()))
                    .append("** — ").append(MarkdownEscaper.escape(criterion.summary())).append('\n');
        }
        md.append('\n');
    }

    /** Les incidents rencontrés en chemin, s'il y en a eu. */
    private static void appendWarnings(StringBuilder md, EvaluationResult result) {
        appendItemList(md, "Incidents rencontrés", result.warnings());
    }

    /** Une cellule de tableau : on échappe le {@code |}, qui casserait la colonne. */
    private static String cell(String text) {
        return MarkdownEscaper.escape(text);
    }

    private static String formatDate(EvaluationResult result) {
        return DATE_FORMAT.format(result.analysedAt().atZone(ZoneId.systemDefault()));
    }

    private static String formatScore(double score) {
        return String.format(LOCALE, "%.1f", score);
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        return seconds < 60
                ? seconds + " s"
                : "%d min %02d s".formatted(seconds / 60, seconds % 60);
    }
}
