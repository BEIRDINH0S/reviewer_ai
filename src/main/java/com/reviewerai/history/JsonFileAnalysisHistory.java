package com.reviewerai.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reviewerai.model.CodeLocation;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.Severity;
import com.reviewerai.model.SourceKind;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Historique conservé sur disque, un fichier JSON par analyse.
 *
 * <p>Un fichier par analyse plutôt qu'un fichier unique : deux analyses simultanées ne peuvent
 * pas se corrompre mutuellement, et l'historique reste lisible et réparable à la main. Les
 * fichiers sont nommés d'après l'horodatage, ce qui les rend triables sans les ouvrir.
 *
 * <p>Le format est écrit à la main plutôt que produit par sérialisation automatique. La raison
 * est la même que pour le JSON servi au navigateur : le format de l'historique doit rester
 * stable quand les {@code record} du domaine évoluent, faute de quoi une analyse enregistrée
 * aujourd'hui deviendra illisible à la prochaine refonte du modèle.
 *
 * <p><b>Sécurité</b> : on enregistre des notes, des durées et des compteurs — jamais le code
 * du projet évalué, jamais un prompt, jamais un secret. Un {@link EvaluationResult} ne porte
 * d'ailleurs aucun de ces éléments : c'est ce qui rend l'enregistrement sûr par construction.
 * Le répertoire d'historique est créé avec les droits par défaut de l'utilisateur, et n'est
 * jamais servi par le serveur web.
 */
public final class JsonFileAnalysisHistory implements AnalysisHistory {

    /** Horodatage compact et triable, en UTC : deux analyses se classent par leur nom de fichier. */
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS").withZone(ZoneOffset.UTC);

    private static final String SUFFIX = ".json";

    /** Filet de sécurité si un fichier d'historique a perdu son {@code maxScore} : le barème usuel. */
    private static final int DEFAULT_MAX_SCORE = 10;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path directory;

    /**
     * @param directory répertoire où ranger les analyses, créé au premier enregistrement
     */
    public JsonFileAnalysisHistory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** Le répertoire utilisé, affiché par l'interface. */
    public Path directory() {
        return directory;
    }

    @Override
    public String record(EvaluationResult result) {
        Objects.requireNonNull(result, "result");
        try {
            Files.createDirectories(directory);
            String id = freeId(result.analysedAt());
            writeAtomically(directory.resolve(id + SUFFIX), mapper.writeValueAsBytes(toNode(id, result)));
            return id;
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'enregistrer l'analyse dans " + directory, e);
        }
    }

    @Override
    public List<HistoryEntry> list() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<HistoryEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (Path file : stream) {
                readEntry(file).ifPresent(entries::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire l'historique dans " + directory, e);
        }
        entries.sort(Comparator.comparing(HistoryEntry::analysedAt).reversed());
        return List.copyOf(entries);
    }

    @Override
    public Optional<EvaluationResult> find(String id) {
        if (!isSafeId(id)) {
            return Optional.empty();
        }
        Path file = directory.resolve(id + SUFFIX);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(toResult(mapper.readTree(Files.readAllBytes(file))));
        } catch (IOException | RuntimeException e) {
            // Fichier corrompu ou format inattendu : on l'ignore plutôt que de faire échouer l'appel.
            return Optional.empty();
        }
    }

    /**
     * Lit le résumé d'un fichier d'historique, ou rien s'il est corrompu.
     *
     * <p>Un fichier illisible ne doit pas faire échouer {@link #list()} : l'historique reste
     * consultable même si l'une de ses entrées a été tronquée par un arrêt brutal.
     */
    private Optional<HistoryEntry> readEntry(Path file) {
        try {
            JsonNode root = mapper.readTree(Files.readAllBytes(file));
            String id = stripSuffix(file.getFileName().toString());
            return Optional.of(new HistoryEntry(
                    id,
                    root.path("projectName").asText(""),
                    Instant.parse(root.path("analysedAt").asText()),
                    root.path("overallScore").asDouble(0.0),
                    root.path("criteriaCount").asInt(0),
                    root.path("modelName").asText("")));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    // --- écriture ---

    private ObjectNode toNode(String id, EvaluationResult result) {
        ObjectNode node = mapper.createObjectNode();
        // Résumé, dupliqué en tête pour que list() n'ait pas à reconstruire tout le résultat.
        node.put("id", id);
        node.put("projectName", result.project().name());
        node.put("analysedAt", result.analysedAt().toString());
        node.put("overallScore", result.overallScore());
        node.put("criteriaCount", result.evaluatedCriteria().size());
        node.put("modelName", result.modelName());
        // Détail complet, pour rouvrir le rapport.
        node.put("projectRoot", result.project().root().toString());
        node.put("origin", result.project().origin().name());
        node.put("revision", result.project().revision());
        node.put("configLabel", result.configLabel());
        node.put("durationMillis", result.duration().toMillis());
        node.put("llmCalls", result.llmCalls());

        ArrayNode criteria = node.putArray("criteria");
        result.criteria().forEach(c -> criteria.add(toNode(c)));
        ArrayNode warnings = node.putArray("warnings");
        result.warnings().forEach(warnings::add);
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
        node.put("severity", finding.severity().name());
        node.put("title", finding.title());
        node.put("explanation", finding.explanation());
        node.put("confidence", finding.confidence());
        return node;
    }

    // --- lecture ---

    private EvaluationResult toResult(JsonNode root) {
        ProjectRef project = new ProjectRef(
                root.path("projectName").asText(""),
                Path.of(root.path("projectRoot").asText(".")),
                SourceKind.valueOf(root.path("origin").asText(SourceKind.DIRECTORY.name())),
                root.path("revision").asText(""));

        List<CriterionResult> criteria = new ArrayList<>();
        root.path("criteria").forEach(c -> criteria.add(toCriterion(c)));
        List<String> warnings = new ArrayList<>();
        root.path("warnings").forEach(w -> warnings.add(w.asText("")));

        return new EvaluationResult(project,
                Instant.parse(root.path("analysedAt").asText()),
                root.path("modelName").asText(""),
                root.path("configLabel").asText(""),
                criteria,
                Duration.ofMillis(root.path("durationMillis").asLong(0)),
                root.path("llmCalls").asInt(0),
                warnings);
    }

    private static CriterionResult toCriterion(JsonNode node) {
        List<String> strengths = textList(node.path("strengths"));
        List<String> weaknesses = textList(node.path("weaknesses"));
        List<String> recommendations = textList(node.path("recommendations"));
        List<Finding> findings = new ArrayList<>();
        node.path("findings").forEach(f -> findings.add(toFinding(f)));
        return new CriterionResult(
                node.path("id").asText(""),
                node.path("label").asText(""),
                node.path("score").asInt(0),
                node.path("maxScore").asInt(DEFAULT_MAX_SCORE),
                node.path("summary").asText(""),
                strengths, weaknesses, recommendations, findings,
                node.path("evaluated").asBoolean(false));
    }

    private static Finding toFinding(JsonNode node) {
        return new Finding(
                new CodeLocation(node.path("file").asText(""), node.path("symbol").asText(""),
                        node.path("line").asInt(-1)),
                Severity.valueOf(node.path("severity").asText(Severity.LOW.name())),
                node.path("title").asText(""),
                node.path("explanation").asText(""),
                node.path("confidence").asDouble(0.0));
    }

    private static List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText("")));
        return values;
    }

    // --- utilitaires ---

    /** Un identifiant libre pour cet horodatage, en évitant d'écraser une analyse existante. */
    private String freeId(Instant analysedAt) {
        String base = STAMP.format(analysedAt);
        String id = base;
        int n = 1;
        while (Files.exists(directory.resolve(id + SUFFIX))) {
            id = base + "-" + n++;
        }
        return id;
    }

    /**
     * Écrit d'abord dans un fichier temporaire puis le déplace : {@link #list()} ne voit jamais
     * un fichier à demi écrit, seulement l'ancien ou le nouveau.
     */
    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), "tmp-", SUFFIX);
        try {
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Un identifiant venu de l'extérieur ne doit jamais servir à sortir du répertoire. */
    private static boolean isSafeId(String id) {
        return id != null && !id.isBlank() && id.chars()
                .allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_');
    }

    private static String stripSuffix(String fileName) {
        return fileName.endsWith(SUFFIX) ? fileName.substring(0, fileName.length() - SUFFIX.length()) : fileName;
    }
}
