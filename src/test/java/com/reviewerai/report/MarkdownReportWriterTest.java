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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du rendu Markdown.
 *
 * <p>Mêmes deux familles que pour le LaTeX : le document porte-t-il ce que le sujet exige, et —
 * le point critique — un texte venant du modèle peut-il redevenir du Markdown actif dans le
 * rapport publié.
 */
class MarkdownReportWriterTest {

    private static final ProjectRef PROJECT =
            new ProjectRef("demo", Path.of("/tmp/demo"), SourceKind.GIT, "a1b2c3d4e5f6");

    private final MarkdownReportWriter writer = new MarkdownReportWriter();

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

    @Test
    @DisplayName("l'en-tête identifie le projet, la date, le modèle et la configuration")
    void headerCarriesTheAnalysisContext() {
        String md = render(List.of(criterion("a", "Architecture", 8)));

        assertTrue(md.startsWith("# Évaluation de demo"), "titre du projet");
        assertTrue(md.contains("10/09/2026"), "date d'analyse");
        assertTrue(md.contains("qwen2.5-coder"), "modèle interrogé");
        assertTrue(md.contains("budget 6000 jetons"), "configuration utilisée");
        assertTrue(md.contains("a1b2c3d"), "révision, qui rend l'analyse reproductible");
    }

    @Test
    @DisplayName("le tableau récapitulatif porte une ligne par critère, le total et la note globale")
    void summaryTableListsEveryCriterion() {
        String md = render(List.of(criterion("a", "Architecture", 8), criterion("b", "Tests", 6)));

        assertTrue(md.contains("| Critère | Note | Max |"), "en-tête du tableau");
        assertTrue(md.contains("| Architecture | 8 | 10 |"));
        assertTrue(md.contains("| Tests | 6 | 10 |"));
        assertTrue(md.contains("| **Total** | **14** | **20** |"));
        assertTrue(md.contains("Note globale"));
    }

    @Test
    @DisplayName("la note globale est écrite à la française")
    void overallScoreUsesFrenchDecimalSeparator() {
        String md = render(List.of(criterion("a", "Architecture", 7)));

        assertTrue(md.contains("14,0/20"), "7/10 vaut 14/20 ; le séparateur doit être une virgule");
    }

    @Test
    @DisplayName("chaque critère a sa section avec forces, faiblesses et recommandations")
    void everyCriterionHasItsSection() {
        String md = render(List.of(criterion("a", "Architecture", 8)));

        assertTrue(md.contains("## Architecture — 8/10"));
        assertTrue(md.contains("**Points forts**"));
        assertTrue(md.contains("**Points faibles**"));
        assertTrue(md.contains("**Recommandations**"));
        assertTrue(md.contains("- un point fort"));
    }

    @Test
    @DisplayName("les signalements apparaissent avec leur gravité et leur emplacement")
    void findingsAreLocated() {
        var withFinding = new CriterionResult("a", "Sécurité", 4, 10, "",
                List.of(), List.of(), List.of(),
                List.of(new Finding(new CodeLocation("src/A.java", "A#run", 42),
                        Severity.HIGH, "Ressource non fermée", "Le flux reste ouvert.", 0.9)),
                true);

        String md = render(List.of(withFinding));

        assertTrue(md.contains("### Signalements"));
        assertTrue(md.contains("**HIGH**"));
        assertTrue(md.contains("src/A.java:42"));
        assertTrue(md.contains("Ressource non fermée"));
    }

    @Test
    @DisplayName("un lien Markdown dans un titre de signalement ressort inerte")
    void markdownLinkInFindingIsNeutralised() {
        var hostile = new CriterionResult("a", "Sécurité", 4, 10, "",
                List.of(), List.of(), List.of(),
                List.of(new Finding(CodeLocation.ofFile("src/A.java"), Severity.LOW,
                        "[clique ici](http://attaquant.invalide)", "", 0.5)),
                true);

        String md = render(List.of(hostile));

        // La syntaxe de lien « ]( » est rompue par l'échappement : le Markdown ne la rend plus
        // comme un lien cliquable. Les caractères subsistent, précédés d'une barre oblique, mais
        // ils sont inertes.
        assertFalse(md.contains("]("), "le lien ne doit plus être cliquable dans le rapport publié");
        assertTrue(md.contains("\\]\\("), "les crochets et parenthèses doivent être échappés, donc inertes");
    }

    @Test
    @DisplayName("un critère non évalué figure dans le document, avec son motif")
    void unevaluatedCriterionIsShownWithItsReason() {
        var failed = CriterionResult.failed("b", "Lisibilité", 10, "délai dépassé");

        String md = render(List.of(criterion("a", "Architecture", 8), failed));

        assertTrue(md.contains("## Critères non évalués"));
        assertTrue(md.contains("Lisibilité"));
        assertTrue(md.contains("délai dépassé"));
    }

    @Test
    @DisplayName("un critère non évalué apparaît sans note dans le tableau")
    void unevaluatedCriterionHasNoScoreInTheTable() {
        String md = render(List.of(CriterionResult.failed("b", "Lisibilité", 10, "panne")));

        assertTrue(md.contains("| Lisibilité | — | — |"),
                "afficher son maximum ferait croire à une erreur de calcul dans le total");
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
    @DisplayName("un résultat sans aucun critère évalué produit quand même un document")
    void fullyFailedEvaluationStillRenders() {
        String md = render(List.of(CriterionResult.failed("a", "Architecture", 10, "modèle absent")));

        assertTrue(md.startsWith("# Évaluation de demo"));
        assertTrue(md.contains("0,0/20"), "aucun critère évalué donne une note globale nulle");
    }

    @Test
    @DisplayName("l'extension et le nom du format sont ceux attendus")
    void formatIsAnnounced() {
        assertEquals("md", writer.fileExtension());
        assertEquals("Markdown", writer.formatName());
    }
}
