package com.reviewerai.view.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.history.HistoryEntry;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Traduit entre le JSON du navigateur et les objets du domaine.
 *
 * <p>La conversion est écrite à la main plutôt que laissée à Jackson. Deux raisons : le format
 * envoyé au navigateur reste stable même si les {@code record} du domaine changent, et on
 * décide explicitement de ce qui sort — un projet évalué n'a pas à voir sa structure interne
 * exposée par accident.
 */
public final class WebJson {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Construit la configuration d'une évaluation à partir du formulaire. */
    public EvaluationConfig toConfig(String requestBody, Path reportFile) throws IOException {
        JsonNode root = mapper.readTree(requestBody);
        var builder = EvaluationConfig.builder()
                .projectSource(Path.of(required(root, "project")))
                .reportFile(reportFile);

        if (root.hasNonNull("criteria") && root.get("criteria").isArray()) {
            builder.criterionIds(toStringList(root.get("criteria")));
        }
        if (root.hasNonNull("exclude") && root.get("exclude").isArray()) {
            builder.excludePatterns(toStringList(root.get("exclude")));
        }
        if (hasText(root, "model")) {
            builder.modelName(root.get("model").asText());
        }
        if (hasText(root, "ollamaUrl")) {
            builder.ollamaBaseUrl(root.get("ollamaUrl").asText());
        }
        return builder.build();
    }

    /** Vrai si le formulaire demande une évaluation sans appeler le modèle. */
    public boolean isOffline(String requestBody) throws IOException {
        JsonNode root = mapper.readTree(requestBody);
        return root.hasNonNull("offline") && root.get("offline").asBoolean();
    }

    /** La liste des critères disponibles, pour les cases à cocher du formulaire. */
    public String toJson(List<CriterionDescriptor> descriptors) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode array = node.putArray("criteria");
        for (CriterionDescriptor descriptor : descriptors) {
            ObjectNode item = array.addObject();
            item.put("id", descriptor.id());
            item.put("label", descriptor.label());
            item.put("maxScore", descriptor.maxScore());
            item.put("usesLlm", descriptor.usesLlm());
        }
        return mapper.writeValueAsString(node);
    }

    /**
     * L'arborescence du projet, que l'interface doit pouvoir afficher.
     *
     * <p>Plafonnée : un projet de dix mille fichiers rendrait la page inutilisable et la
     * réponse énorme. Le total réel est envoyé à part pour que l'utilisateur sache qu'il ne
     * voit pas tout.
     */
    public String toJson(ProjectSnapshot project, int maxFiles) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", project.project().name());
        node.put("origin", project.project().origin().label());
        node.put("revision", project.project().shortRevision());
        node.put("fileCount", project.files().size());
        node.put("javaLines", project.mainJavaLines());

        ArrayNode files = node.putArray("files");
        for (ProjectFile file : project.files().stream().limit(maxFiles).toList()) {
            ObjectNode item = files.addObject();
            item.put("path", file.path());
            item.put("kind", file.kind().label());
            item.put("lines", file.lineCount());
        }
        node.put("truncated", project.files().size() > maxFiles);
        return mapper.writeValueAsString(node);
    }

    /** L'état courant, tel que le navigateur l'attend. */
    public String toJson(EvaluationState state) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("status", state.status().name());
        node.put("stage", state.stage());
        node.put("current", state.current());
        node.put("total", state.total());
        node.put("currentLabel", state.currentLabel());
        node.put("error", state.errorMessage());

        ArrayNode warnings = node.putArray("warnings");
        state.warnings().forEach(warnings::add);

        if (state.result() != null) {
            node.set("result", toNode(state.result()));
            node.put("report", state.report());
        }
        return mapper.writeValueAsString(node);
    }

    /**
     * La liste de l'historique : un résumé par analyse, du plus récent au plus ancien.
     *
     * <p>Volontairement pauvre — juste de quoi peupler la liste sans relire chaque rapport
     * complet. Le détail n'est chargé qu'au clic, via {@link #toResultJson}.
     */
    public String toHistoryJson(List<HistoryEntry> entries) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode array = node.putArray("history");
        for (HistoryEntry entry : entries) {
            ObjectNode item = array.addObject();
            item.put("id", entry.id());
            item.put("project", entry.projectName());
            item.put("analysedAt", entry.analysedAt().toString());
            item.put("overallScore", entry.overallScore());
            item.put("criteriaCount", entry.criteriaCount());
            item.put("model", entry.modelName());
        }
        return mapper.writeValueAsString(node);
    }

    /** Une analyse complète de l'historique, telle que l'affichage du détail l'attend. */
    public String toResultJson(EvaluationResult result) throws IOException {
        return mapper.writeValueAsString(toNode(result));
    }

    /** Un message d'erreur simple, pour les réponses HTTP en échec. */
    public String toErrorJson(String message) {
        try {
            return mapper.writeValueAsString(mapper.createObjectNode().put("error", message));
        } catch (IOException e) {
            // Impossible en pratique : on sérialise un objet qu'on vient de construire.
            return "{\"error\":\"erreur interne\"}";
        }
    }

    private ObjectNode toNode(EvaluationResult result) {
        ObjectNode node = mapper.createObjectNode();
        node.put("project", result.project().describe());
        node.put("analysedAt", result.analysedAt().toString());
        node.put("model", result.modelName());
        node.put("configuration", result.configLabel());
        node.put("llmCalls", result.llmCalls());
        node.put("durationSeconds", result.duration().toSeconds());
        node.put("overallScore", result.overallScore());
        node.put("totalScore", result.totalScore());
        node.put("totalMaxScore", result.totalMaxScore());
        node.put("partial", result.isPartial());

        ArrayNode criteria = node.putArray("criteria");
        result.criteria().forEach(c -> criteria.add(toNode(c)));
        return node;
    }

    private ObjectNode toNode(CriterionResult criterion) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", criterion.criterionId());
        node.put("label", criterion.label());
        node.put("score", criterion.score());
        node.put("maxScore", criterion.maxScore());
        node.put("evaluated", criterion.evaluated());
        node.put("summary", criterion.summary());
        criterion.strengths().forEach(node.putArray("strengths")::add);
        criterion.weaknesses().forEach(node.putArray("weaknesses")::add);
        criterion.recommendations().forEach(node.putArray("recommendations")::add);

        ArrayNode findings = node.putArray("findings");
        criterion.findings().forEach(f -> findings.add(toNode(f)));
        return node;
    }

    private ObjectNode toNode(Finding finding) {
        ObjectNode node = mapper.createObjectNode();
        node.put("file", finding.location().filePath());
        node.put("symbol", finding.location().symbol());
        node.put("line", finding.location().line());
        node.put("where", finding.location().describe());
        node.put("severity", finding.severity().name());
        node.put("title", finding.title());
        node.put("explanation", finding.explanation());
        node.put("confidence", finding.confidence());
        return node;
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(item -> {
            String text = item.asText().trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        });
        return List.copyOf(values);
    }

    private static boolean hasText(JsonNode root, String field) {
        return root.hasNonNull(field) && !root.get(field).asText().isBlank();
    }

    private static String required(JsonNode root, String field) {
        if (!root.hasNonNull(field) || root.get(field).asText().isBlank()) {
            throw new IllegalArgumentException("Champ manquant : " + field);
        }
        return root.get(field).asText();
    }
}
