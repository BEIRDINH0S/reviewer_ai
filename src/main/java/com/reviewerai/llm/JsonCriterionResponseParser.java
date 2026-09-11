package com.reviewerai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.model.CodeLocation;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Lit la réponse JSON du modèle et la valide.
 *
 * <p>Implémentation de référence de {@link CriterionResponseParser}, appuyée sur Jackson en
 * mode lecture d'arbre : on ne désérialise jamais directement vers un {@code record}. Une
 * désérialisation automatique accepterait tout ce que le modèle a écrit ; ici chaque champ est
 * lu, contrôlé et normalisé un par un.
 *
 * <p>Sans état, donc partageable entre critères et utilisable en parallèle : le projet évalué
 * arrive en paramètre plutôt que par le constructeur.
 *
 * <p>Le format attendu est celui du sujet :
 * <pre>
 * {
 *   "criterion": "Architecture",
 *   "score": 7,
 *   "maxScore": 10,
 *   "summary": "…",
 *   "strengths": ["…"],
 *   "weaknesses": ["…"],
 *   "recommendations": ["…"],
 *   "findings": [
 *     {"file": "src/…/A.java", "line": 42, "severity": "HIGH", "title": "…",
 *      "explanation": "…", "confidence": 0.8}
 *   ]
 * }
 * </pre>
 *
 */
public final class JsonCriterionResponseParser implements CriterionResponseParser {

    /** Au-delà, on considère que le modèle remplit du vide plutôt qu'il n'analyse. */
    public static final int MAX_ITEMS_PER_LIST = 10;

    /** Confiance retenue quand le modèle omet le champ : neutre, ni sûr ni infondé. */
    private static final double DEFAULT_CONFIDENCE = 0.5;

    /** Jackson en lecture d'arbre est sans état et sûr entre threads une fois construit. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Valide la réponse brute du modèle et en tire un {@link CriterionResult}.
     *
     * <p>Marche à suivre :
     * <ol>
     *   <li>isoler le premier objet JSON de la réponse — les petits modèles encadrent volontiers
     *       leur réponse de texte ou de barrières de code ;
     *   <li>lire l'arbre avec Jackson ; toute exception devient une
     *       {@link InvalidResponseException}, jamais une remontée telle quelle ;
     *   <li>vérifier que le modèle s'est réellement prononcé — voir {@link #requireVerdict} ;
     *   <li>note : la ramener dans {@code [0, descriptor.maxScore()]} — le {@code maxScore}
     *       renvoyé par le modèle est ignoré, celui du descripteur fait foi ;
     *   <li>listes de texte : entrées vides ignorées, textes trop longs tronqués, au plus
     *       {@link #MAX_ITEMS_PER_LIST} conservés ;
     *   <li>signalements : seuls ceux dont le fichier existe dans {@code project} sont gardés ;
     *   <li>l'identifiant et le libellé retenus sont ceux du descripteur, jamais ceux du modèle.
     * </ol>
     */
    @Override
    public CriterionResult parse(String rawResponse, CriterionDescriptor descriptor, ProjectSnapshot project) {
        JsonNode root = readTree(extractFirstJsonObject(rawResponse));
        requireVerdict(root);
        return new CriterionResult(
                descriptor.id(),
                descriptor.label(),
                clampScore(root.path("score").asInt(0), descriptor.maxScore()),
                descriptor.maxScore(),
                truncate(root.path("summary").asText("")),
                textList(root.path("strengths")),
                textList(root.path("weaknesses")),
                textList(root.path("recommendations")),
                findings(root.path("findings"), project),
                true);
    }

    /**
     * Vérifie que la réponse porte bien un verdict, et pas seulement du JSON bien formé.
     *
     * <p>Un petit modèle produit volontiers un objet valide dans un schéma qu'il a inventé —
     * relevé sur {@code qwen2.5-coder:1.5b} :
     * {@code {"criteria":[{"name":"…","weight":10,"notes":"…","feedback":"…"}]}}. Sans ce
     * contrôle, {@code path("score").asInt(0)} rend 0 et {@code path("summary").asText("")}
     * rend une chaîne vide : le critère est alors noté <b>0 sur 10 et marqué comme évalué</b>,
     * ce qui fait passer un échec pour un jugement et contamine la note globale.
     *
     * <p>On exige le seul champ sans lequel il n'y a pas d'évaluation du tout : la note. Le
     * résumé et les listes restent facultatifs — ne rien trouver à redire est une réponse
     * légitime, alors que ne pas se prononcer n'en est pas une.
     *
     * <p>Le contrôle porte sur la <b>présence</b> du champ et sur son type, jamais sur sa
     * valeur : un 0 sur 10 argumenté est un verdict recevable et doit continuer de passer.
     * C'est aussi pourquoi une note textuelle est refusée plutôt que convertie — {@code asInt}
     * rendrait 0 sur {@code "sept"}, soit exactement la fausse note qu'on cherche à empêcher.
     *
     * @throws InvalidResponseException si la réponse ne contient pas de verdict exploitable
     */
    private static void requireVerdict(JsonNode root) {
        if (!root.hasNonNull("score")) {
            throw new InvalidResponseException("réponse hors schéma : aucune note");
        }
        if (!root.path("score").isNumber()) {
            throw new InvalidResponseException("réponse hors schéma : note non numérique");
        }
    }

    /**
     * Isole le premier objet JSON de la réponse.
     *
     * <p>On repère la première accolade ouvrante et l'accolade fermante qui lui correspond, en
     * ignorant les accolades apparaissant à l'intérieur d'une chaîne. Le texte ou les barrières
     * de code qui entourent l'objet sont ainsi écartés.
     *
     * @throws InvalidResponseException si aucun objet complet n'est présent
     */
    private static String extractFirstJsonObject(String rawResponse) {
        if (rawResponse == null) {
            throw new InvalidResponseException("réponse absente");
        }
        int start = rawResponse.indexOf('{');
        if (start < 0) {
            throw new InvalidResponseException("aucun objet JSON dans la réponse");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < rawResponse.length(); i++) {
            char c = rawResponse.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return rawResponse.substring(start, i + 1);
            }
        }
        throw new InvalidResponseException("objet JSON non refermé dans la réponse");
    }

    /** Lit l'arbre JSON, en traduisant toute erreur de Jackson en {@link InvalidResponseException}. */
    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidResponseException("JSON illisible : " + e.getOriginalMessage());
        }
    }

    /** Ramène la note dans {@code [0, maxScore]} : un modèle rend régulièrement du 15/10. */
    private static int clampScore(int score, int maxScore) {
        return Math.max(0, Math.min(score, maxScore));
    }

    /**
     * Convertit un tableau JSON en liste de textes propres.
     *
     * <p>Les entrées vides sont ignorées, les textes trop longs tronqués, et l'on n'en garde au
     * plus que {@link #MAX_ITEMS_PER_LIST} : au-delà, le modèle remplit du vide.
     */
    private static List<String> textList(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode entry : array) {
            String text = entry.asText("");
            if (!text.isBlank()) {
                items.add(truncate(text.trim()));
            }
            if (items.size() == MAX_ITEMS_PER_LIST) {
                break;
            }
        }
        return List.copyOf(items);
    }

    /**
     * Convertit le tableau des signalements en {@link Finding} validés.
     *
     * <p>Un signalement n'est gardé que s'il cite un fichier réellement présent dans le projet
     * et porte un titre : c'est ce qui empêche le modèle d'inventer un problème sur un fichier
     * qui n'existe pas. La gravité passe par {@link Severity#parse}, la confiance est ramenée
     * dans {@code [0,1]}, et les textes trop longs sont tronqués par {@link Finding}.
     */
    private static List<Finding> findings(JsonNode array, ProjectSnapshot project) {
        if (!array.isArray()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (JsonNode entry : array) {
            String file = entry.path("file").asText("");
            String title = entry.path("title").asText("");
            if (title.isBlank() || project.byPath(file).isEmpty()) {
                continue;
            }
            int line = entry.path("line").asInt(-1);
            findings.add(new Finding(
                    line > 0 ? CodeLocation.ofLine(file, line) : CodeLocation.ofFile(file),
                    Severity.parse(entry.path("severity").asText(null)),
                    title,
                    entry.path("explanation").asText(""),
                    clampConfidence(entry.path("confidence").asDouble(DEFAULT_CONFIDENCE))));
        }
        return List.copyOf(findings);
    }

    /** Ramène la confiance dans {@code [0,1]} : un modèle rend parfois 1.7. */
    private static double clampConfidence(double confidence) {
        return Math.max(0.0, Math.min(confidence, 1.0));
    }

    /** Coupe un texte trop long, même motif que {@link Finding} : éviter le rejet d'un fichier entier. */
    private static String truncate(String text) {
        if (text == null || text.length() <= Finding.MAX_TEXT_LENGTH) {
            return text == null ? "" : text;
        }
        return text.substring(0, Finding.MAX_TEXT_LENGTH) + "…";
    }
}
