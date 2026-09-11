package com.reviewerai.llm;

import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.llm.CriterionResponseParser.InvalidResponseException;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.Finding;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.Severity;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de {@link JsonCriterionResponseParser}.
 *
 * <p>Ce sont des tests de la frontière de confiance : ils donnent une réponse brute — telle
 * qu'un petit modèle peut la produire, injection de prompt comprise — et vérifient que ce qui
 * franchit le parseur est propre. Aucun modèle n'est nécessaire.
 */
class JsonCriterionResponseParserTest {

    private static final String EXISTING = "src/main/java/com/exemple/A.java";

    private final JsonCriterionResponseParser parser = new JsonCriterionResponseParser();
    private final CriterionDescriptor descriptor =
            CriterionDescriptor.llm("architecture", "Architecture", "juge l'architecture");

    /** Projet à un seul fichier, celui que les signalements valides peuvent citer. */
    private ProjectSnapshot project() {
        ProjectRef ref = ProjectRef.of("demo", Path.of("/tmp/demo"), SourceKind.DIRECTORY);
        return new ProjectSnapshot(ref, List.of(new ProjectFile(EXISTING, FileKind.JAVA_MAIN, 1000, 100)));
    }

    private CriterionResult parse(String raw) {
        return parser.parse(raw, descriptor, project());
    }

    @Test
    @DisplayName("une réponse bien formée est lue correctement")
    void readsWellFormedResponse() {
        String raw = """
                {
                  "criterion": "Architecture",
                  "score": 7,
                  "maxScore": 10,
                  "summary": "découpage MVC clair",
                  "strengths": ["séparation vue/modèle"],
                  "weaknesses": ["contrôleur un peu gros"],
                  "recommendations": ["extraire un service"],
                  "findings": [
                    {"file": "%s", "line": 42, "severity": "HIGH", "title": "couplage fort",
                     "explanation": "dépend d'une implémentation", "confidence": 0.8}
                  ]
                }
                """.formatted(EXISTING);

        CriterionResult result = parse(raw);

        assertEquals(7, result.score());
        assertEquals(10, result.maxScore());
        assertEquals("découpage MVC clair", result.summary());
        assertEquals(List.of("séparation vue/modèle"), result.strengths());
        assertEquals(List.of("contrôleur un peu gros"), result.weaknesses());
        assertEquals(List.of("extraire un service"), result.recommendations());
        assertTrue(result.evaluated());

        assertEquals(1, result.findings().size());
        Finding finding = result.findings().get(0);
        assertEquals(EXISTING, finding.location().filePath());
        assertEquals(42, finding.location().line());
        assertEquals(Severity.HIGH, finding.severity());
        assertEquals(0.8, finding.confidence());
    }

    @Test
    @DisplayName("l'identifiant et le libellé viennent du descripteur, jamais du modèle")
    void trustsDescriptorNotModel() {
        CriterionResult result = parse("""
                {"criterion": "Autre chose", "score": 5, "maxScore": 100}
                """);

        assertEquals("architecture", result.criterionId());
        assertEquals("Architecture", result.label());
    }

    @Test
    @DisplayName("une réponse entourée de texte et de barrières de code est quand même lue")
    void readsResponseWrappedInProse() {
        String raw = """
                Voici mon analyse du critère demandé :
                ```json
                {"score": 6, "summary": "correct"}
                ```
                J'espère que cela convient.
                """;

        CriterionResult result = parse(raw);

        assertEquals(6, result.score());
        assertEquals("correct", result.summary());
    }

    @Test
    @DisplayName("un JSON malformé lève InvalidResponseException, ne renvoie pas null")
    void malformedJsonThrows() {
        assertThrows(InvalidResponseException.class, () -> parse("{\"score\": }"));
    }

    @Test
    @DisplayName("une réponse sans le moindre objet JSON lève InvalidResponseException")
    void responseWithoutObjectThrows() {
        assertThrows(InvalidResponseException.class, () -> parse("désolé, je ne peux pas répondre"));
    }

    @Test
    @DisplayName("un JSON valide mais dans un autre schéma est refusé, pas noté 0")
    void rejectsWellFormedJsonInForeignSchema() {
        // Réponse réellement produite par qwen2.5-coder:1.5b sur le critère « architecture ».
        // Avant ce contrôle, elle donnait un 0/10 marqué comme évalué.
        String response = """
                {
                  "criteria": [
                    {
                      "name": "Architecture et modularité",
                      "weight": 10,
                      "notes": "La structure du projet est claire.",
                      "feedback": "Le projet est structuré et modulaire."
                    }
                  ]
                }
                """;
        assertThrows(CriterionResponseParser.InvalidResponseException.class, () -> parse(response));
    }

    @Test
    @DisplayName("une réponse sans note est refusée même si elle contient du texte")
    void rejectsResponseWithoutScore() {
        assertThrows(CriterionResponseParser.InvalidResponseException.class,
                () -> parse("{\"summary\": \"tout va bien\", \"strengths\": [\"propre\"]}"));
    }

    @Test
    @DisplayName("une note textuelle est refusée, jamais convertie en 0")
    void rejectsNonNumericScore() {
        assertThrows(CriterionResponseParser.InvalidResponseException.class,
                () -> parse("{\"score\": \"sept\", \"summary\": \"bien\"}"));
    }

    @Test
    @DisplayName("un zéro argumenté reste une note recevable")
    void keepsArguedZero() {
        CriterionResult result = parse("{\"score\": 0, \"summary\": \"aucune architecture discernable\"}");
        assertEquals(0, result.score());
        assertTrue(result.evaluated());
        assertEquals("aucune architecture discernable", result.summary());
    }

    @Test
    @DisplayName("une note de 15 sur un barème de 10 est ramenée à 10")
    void clampsScoreAboveMax() {
        assertEquals(10, parse("{\"score\": 15}").score());
    }

    @Test
    @DisplayName("une note négative est ramenée à 0")
    void clampsNegativeScore() {
        assertEquals(0, parse("{\"score\": -3}").score());
    }

    @Test
    @DisplayName("un signalement citant un fichier absent du projet est écarté")
    void dropsFindingOnUnknownFile() {
        CriterionResult result = parse("""
                {"score": 5, "findings": [
                  {"file": "src/inexistant/B.java", "title": "faux", "confidence": 0.9}
                ]}
                """);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    @DisplayName("une confiance de 1.7 est ramenée à 1.0")
    void clampsConfidence() {
        CriterionResult result = parse("""
                {"score": 5, "findings": [
                  {"file": "%s", "title": "problème", "confidence": 1.7}
                ]}
                """.formatted(EXISTING));

        assertEquals(1, result.findings().size());
        assertEquals(1.0, result.findings().get(0).confidence());
    }

    @Test
    @DisplayName("un signalement sans titre est ignoré, sans valeur inventée")
    void dropsFindingWithoutTitle() {
        CriterionResult result = parse("""
                {"score": 5, "findings": [
                  {"file": "%s", "confidence": 0.9}
                ]}
                """.formatted(EXISTING));

        assertTrue(result.findings().isEmpty());
    }

    @Test
    @DisplayName("une explication démesurée est tronquée")
    void truncatesHugeExplanation() {
        String huge = "x".repeat(50_000);
        CriterionResult result = parse("""
                {"score": 5, "findings": [
                  {"file": "%s", "title": "fuite", "explanation": "%s", "confidence": 0.5}
                ]}
                """.formatted(EXISTING, huge));

        assertEquals(1, result.findings().size());
        String explanation = result.findings().get(0).explanation();
        assertTrue(explanation.length() <= Finding.MAX_TEXT_LENGTH + 1,
                "l'explication doit être tronquée à " + Finding.MAX_TEXT_LENGTH + " caractères");
    }

    @Test
    @DisplayName("une liste de plus de dix entrées est ramenée à dix")
    void capsListLength() {
        StringBuilder entries = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            entries.append(i == 0 ? "" : ",").append("\"point ").append(i).append('"');
        }
        CriterionResult result = parse("{\"score\": 5, \"strengths\": [" + entries + "]}");

        assertEquals(JsonCriterionResponseParser.MAX_ITEMS_PER_LIST, result.strengths().size());
    }

    @Test
    @DisplayName("les entrées vides d'une liste sont ignorées")
    void ignoresEmptyListEntries() {
        CriterionResult result = parse("""
                {"score": 5, "strengths": ["  ", "vrai point", ""]}
                """);

        assertEquals(List.of("vrai point"), result.strengths());
    }
}
