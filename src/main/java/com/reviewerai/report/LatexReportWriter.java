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
 * Produit le rapport d'évaluation au format LaTeX.
 *
 * <p>C'est une contrainte dure du sujet : l'application doit produire automatiquement un
 * fichier {@code .tex}, et le livrable comprend un {@code evaluation.tex} réellement engendré
 * par le programme.
 *
 * <p><b>Le document est construit ici, pas par le modèle.</b> Le sujet l'exige : le LLM
 * remplit des champs, il ne dessine pas le rapport. Cette classe ne reçoit qu'un
 * {@link EvaluationResult}, c'est-à-dire une structure de données Java. Rien de sa mise en
 * forme ne dépend de ce qu'a répondu le modèle — pas même la synthèse finale, qui est
 * recalculée à partir des notes.
 *
 * <p><b>Sécurité — la règle sans exception</b> : tout texte issu de {@link EvaluationResult}
 * passe par {@link LatexEscaper}. Aucun champ n'est réputé de confiance. LaTeX est un langage
 * exécutable dont le compilateur lit des fichiers ; un {@code \input} non échappé dans un titre
 * de signalement suffirait à recopier un fichier de la machine dans le PDF produit. Le chemin
 * d'attaque tient en trois étapes : un commentaire dans le projet évalué, un modèle qui le
 * recopie dans une explication, un rapport compilé.
 *
 * <p>Le préambule est volontairement minimal, pour que le fichier compile avec une installation
 * LaTeX ordinaire — y compris celle, réduite, du conteneur.
 *
 * <p><b>Compiler deux fois.</b> Le tableau de synthèse est un {@code longtable}, qui mesure ses
 * colonnes à la première passe et ne les applique qu'à la seconde. Une compilation unique donne
 * un tableau dont les en-têtes ne sont pas alignés sur leurs colonnes. C'est le comportement
 * normal de LaTeX, le même que pour la table des matières ou les références :
 * <pre>pdflatex evaluation.tex &amp;&amp; pdflatex evaluation.tex</pre>
 */
public final class LatexReportWriter implements ReportWriter {

    /** Le document est destiné à des lecteurs francophones : séparateur décimal virgule. */
    private static final Locale LOCALE = Locale.FRENCH;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", LOCALE);

    /** Au-delà, un titre déborde de la colonne du tableau récapitulatif. */
    private static final int MAX_TABLE_CELL = 45;

    @Override
    public String render(EvaluationResult result) {
        Objects.requireNonNull(result, "result");

        StringBuilder tex = new StringBuilder(8192);
        appendPreamble(tex, result);
        appendContext(tex, result);
        appendSummary(tex, result);
        appendCriteria(tex, result);
        appendUnevaluated(tex, result);
        appendWarnings(tex, result);
        tex.append("\\end{document}\n");
        return tex.toString();
    }

    @Override
    public String fileExtension() {
        return "tex";
    }

    @Override
    public String formatName() {
        return "LaTeX";
    }

    /**
     * Le préambule et le titre.
     *
     * <p>Cinq paquets, tous présents dans n'importe quelle distribution LaTeX : le rapport doit
     * compiler chez le correcteur, pas seulement sur nos machines.
     */
    private static void appendPreamble(StringBuilder tex, EvaluationResult result) {
        tex.append("% Document engendré automatiquement par AI Project Reviewer.\n")
                .append("% Ne pas modifier à la main : il est réécrit à chaque évaluation.\n")
                .append("\\documentclass[11pt,a4paper]{article}\n")
                .append("\\usepackage[utf8]{inputenc}\n")
                .append("\\usepackage[T1]{fontenc}\n")
                .append("\\usepackage{array}\n")
                .append("\\usepackage{longtable}\n")
                .append("\\usepackage{geometry}\n")
                .append("\\usepackage{hyperref}\n")
                .append("\\geometry{margin=2.5cm}\n\n")
                .append("\\title{Évaluation de ")
                .append(LatexEscaper.escape(result.project().name()))
                .append("}\n")
                .append("\\date{")
                .append(LatexEscaper.escape(formatDate(result)))
                .append("}\n")
                .append("\\author{AI Project Reviewer}\n\n")
                .append("\\begin{document}\n")
                .append("\\maketitle\n\n");
    }

    /**
     * Le contexte de l'analyse.
     *
     * <p>Rassemble ce que le sujet exige de retrouver dans le rapport : identification du
     * projet, date, configuration utilisée, modèle interrogé.
     */
    private static void appendContext(StringBuilder tex, EvaluationResult result) {
        // Une colonne « p » plutôt que « l » : la configuration tient sur plusieurs lignes et
        // déborderait de la page dans une colonne à largeur naturelle. En drapeau plutôt que
        // justifiée, sinon LaTeX étire les espaces d'une ligne repliée de deux mots.
        tex.append("\\section*{Contexte de l'analyse}\n")
                .append("\\begin{tabular}{@{}l>{\\raggedright\\arraybackslash}p{0.62\\textwidth}@{}}\n");

        appendContextRow(tex, "Projet", result.project().name());
        appendContextRow(tex, "Provenance", result.project().origin().label());
        if (!result.project().revision().isEmpty()) {
            appendContextRow(tex, "Révision", result.project().shortRevision());
        }
        appendContextRow(tex, "Date d'analyse", formatDate(result));
        appendContextRow(tex, "Modèle interrogé", result.modelName());
        appendContextRow(tex, "Appels au modèle", String.valueOf(result.llmCalls()));
        appendContextRow(tex, "Durée", formatDuration(result.duration()));
        appendContextRow(tex, "Configuration", result.configLabel());

        tex.append("\\end{tabular}\n\n");
    }

    private static void appendContextRow(StringBuilder tex, String label, String value) {
        tex.append("\\textbf{").append(LatexEscaper.escape(label)).append("} & ")
                .append(LatexEscaper.escape(value)).append(" \\\\\n");
    }

    /**
     * La synthèse : le tableau des notes, puis l'appréciation d'ensemble.
     *
     * <p>Cette appréciation est <b>calculée à partir des notes</b>, pas demandée au modèle.
     * C'est ce qui garantit qu'elle ne peut pas contredire le tableau qui la précède.
     */
    private static void appendSummary(StringBuilder tex, EvaluationResult result) {
        tex.append("\\section*{Synthèse}\n")
                .append("\\begin{longtable}{@{}lrr@{}}\n")
                .append("\\hline\n")
                .append("\\textbf{Critère} & \\textbf{Note} & \\textbf{Maximum} \\\\\n")
                .append("\\hline\n")
                .append("\\endfirsthead\n")
                .append("\\hline\n")
                .append("\\textbf{Critère} & \\textbf{Note} & \\textbf{Maximum} \\\\\n")
                .append("\\hline\n")
                .append("\\endhead\n");

        for (CriterionResult criterion : result.criteria()) {
            // Un critère non évalué n'entre pas dans le total : afficher son maximum ferait
            // croire à une erreur de calcul, le lecteur additionnant une colonne dont le total
            // ne reprend qu'une partie.
            String score = criterion.evaluated() ? String.valueOf(criterion.score()) : "---";
            String max = criterion.evaluated() ? String.valueOf(criterion.maxScore()) : "---";
            tex.append(LatexEscaper.escapeCell(criterion.label(), MAX_TABLE_CELL))
                    .append(" & ").append(score)
                    .append(" & ").append(max)
                    .append(" \\\\\n");
        }

        tex.append("\\hline\n")
                .append("\\textbf{Total} & \\textbf{")
                .append(result.totalScore())
                .append("} & \\textbf{")
                .append(result.totalMaxScore())
                .append("} \\\\\n")
                .append("\\hline\n")
                .append("\\end{longtable}\n\n")
                .append("\\begin{center}\n")
                .append("\\Large\\textbf{Note globale : ")
                .append(formatScore(result.overallScore()))
                .append("\\,/\\,20}\n")
                .append("\\end{center}\n\n")
                .append(LatexEscaper.escape(overallComment(result)))
                .append("\n\n");
    }

    /**
     * L'appréciation d'ensemble, déduite des notes.
     *
     * <p>Mentionne explicitement les critères manquants : un lecteur qui ignore qu'un tiers de
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
            comment.append(" Attention : ")
                    .append(total - evaluated)
                    .append(" critère(s) n'ont pas pu être évalués et n'entrent pas dans la note globale. ")
                    .append("Celle-ci porte donc sur une partie du barème seulement.");
        }
        return comment.toString();
    }

    /** Une section par critère évalué. */
    private static void appendCriteria(StringBuilder tex, EvaluationResult result) {
        for (CriterionResult criterion : result.evaluatedCriteria()) {
            tex.append("\\section{")
                    .append(LatexEscaper.escape(criterion.label()))
                    .append(" — ").append(criterion.score())
                    .append('/').append(criterion.maxScore())
                    .append("}\n");

            if (!criterion.summary().isEmpty()) {
                tex.append(LatexEscaper.escape(criterion.summary())).append("\n\n");
            }

            appendItemList(tex, "Points forts", criterion.strengths());
            appendItemList(tex, "Points faibles", criterion.weaknesses());
            appendItemList(tex, "Recommandations", criterion.recommendations());
            appendFindings(tex, criterion.findings());
        }
    }

    /**
     * Une liste à puces, ou rien du tout si elle est vide.
     *
     * <p>Le cas vide n'est pas un détail cosmétique : un {@code itemize} sans {@code item} fait
     * échouer la compilation LaTeX. Un critère sans point faible est pourtant un cas normal.
     */
    private static void appendItemList(StringBuilder tex, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        tex.append("\\subsection*{").append(LatexEscaper.escape(title)).append("}\n")
                .append("\\begin{itemize}\n");
        for (String item : items) {
            tex.append("  \\item ").append(LatexEscaper.escape(item)).append('\n');
        }
        tex.append("\\end{itemize}\n\n");
    }

    /** Les signalements localisés qui appuient la note. */
    private static void appendFindings(StringBuilder tex, List<Finding> findings) {
        if (findings.isEmpty()) {
            return;
        }
        tex.append("\\subsection*{Signalements}\n")
                .append("\\begin{itemize}\n");
        for (Finding finding : findings) {
            tex.append("  \\item \\textbf{").append(finding.severity().name()).append("} — ")
                    .append("\\texttt{").append(LatexEscaper.escape(finding.location().describe()))
                    .append("} — ")
                    .append(LatexEscaper.escape(finding.title()));
            if (!finding.explanation().isEmpty()) {
                tex.append(" : ").append(LatexEscaper.escape(finding.explanation()));
            }
            tex.append('\n');
        }
        tex.append("\\end{itemize}\n\n");
    }

    /**
     * Les critères qui n'ont pas abouti, et pourquoi.
     *
     * <p>Le sujet demande une récupération partielle ; encore faut-il qu'elle se voie. Faire
     * disparaître un critère en panne laisserait croire qu'il a été jugé.
     */
    private static void appendUnevaluated(StringBuilder tex, EvaluationResult result) {
        List<CriterionResult> unevaluated = result.criteria().stream()
                .filter(criterion -> !criterion.evaluated())
                .toList();
        if (unevaluated.isEmpty()) {
            return;
        }
        tex.append("\\section*{Critères non évalués}\n")
                .append("\\begin{itemize}\n");
        for (CriterionResult criterion : unevaluated) {
            tex.append("  \\item \\textbf{").append(LatexEscaper.escape(criterion.label()))
                    .append("} — ").append(LatexEscaper.escape(criterion.summary())).append('\n');
        }
        tex.append("\\end{itemize}\n\n");
    }

    /** Les incidents rencontrés en chemin, s'il y en a eu. */
    private static void appendWarnings(StringBuilder tex, EvaluationResult result) {
        appendItemList(tex, "Incidents rencontrés", result.warnings());
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
