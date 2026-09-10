package com.reviewerai.view.web;

import com.reviewerai.config.ServerConfig;
import com.reviewerai.controller.EvaluationController;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.SourceKind;
import com.reviewerai.report.ReportWriter;
import com.reviewerai.service.EvaluationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du serveur web : il démarre pour de bon et on l'interroge par HTTP.
 *
 * <p>Le métier est remplacé par des doublures, donc ces tests vérifient uniquement la
 * plomberie HTTP. Ils tournent en quelques dizaines de millisecondes et n'ont besoin ni d'un
 * projet réel ni d'un modèle.
 */
class WebServerTest {

    private WebServer server;
    private HttpClient client;
    private String baseUrl;

    @TempDir
    Path tempDir;

    private static final ProjectRef PROJECT =
            ProjectRef.of("demo", Path.of("/tmp/demo"), SourceKind.DIRECTORY);

    /** Service qui ne fait rien, pour que les requêtes aboutissent sans évaluation réelle. */
    private static EvaluationService silentService() {
        return listener -> EvaluationResult.empty(PROJECT);
    }

    private static ProjectSnapshot fakeProject(Path source) {
        return new ProjectSnapshot(PROJECT, List.of(
                new ProjectFile("src/main/java/A.java", FileKind.JAVA_MAIN, 500, 40)));
    }

    @BeforeEach
    void startServer() throws IOException {
        // Le système choisit un port libre, ce qui évite les collisions entre tests.
        server = new WebServer(
                new ServerConfig(ServerConfig.LOOPBACK, ServerConfig.ANY_FREE_PORT),
                tempDir.resolve("evaluation.tex"),
                List.of(CriterionDescriptor.deterministic("tests", "Présence de tests", "")),
                WebServerTest::fakeProject,
                config -> new EvaluationController(config, silentService(), new StubReportWriter()));
        server.start();

        baseUrl = "http://127.0.0.1:" + server.port();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    @DisplayName("la page d'accueil est servie")
    void servesHomePage() throws Exception {
        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));
    }

    @Test
    @DisplayName("le script et la feuille de style sont servis séparément")
    void servesStaticAssets() throws Exception {
        assertEquals(200, get("/app.js").statusCode());
        assertEquals(200, get("/style.css").statusCode());
    }

    @Test
    @DisplayName("une adresse inconnue renvoie 404")
    void unknownPathIs404() throws Exception {
        assertEquals(404, get("/nexiste-pas").statusCode());
    }

    @Test
    @DisplayName("les scripts en ligne sont interdits par l'en-tête de sécurité")
    void sendsContentSecurityPolicy() throws Exception {
        String csp = get("/").headers().firstValue("Content-Security-Policy").orElse("");

        assertTrue(csp.contains("default-src 'self'"));
        assertFalse(csp.contains("unsafe-inline"));
    }

    @Test
    @DisplayName("la liste des critères est servie au formulaire")
    void servesCriteriaList() throws Exception {
        HttpResponse<String> response = get("/api/criteria");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Présence de tests"));
    }

    @Test
    @DisplayName("l'arborescence d'un projet est servie")
    void servesProjectTree() throws Exception {
        HttpResponse<String> response = post("/api/project", "{\"project\":\"/tmp/demo\"}");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("src/main/java/A.java"));
    }

    @Test
    @DisplayName("l'état initial indique qu'aucune évaluation n'a eu lieu")
    void statusStartsIdle() throws Exception {
        HttpResponse<String> response = get("/api/status");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"IDLE\""));
    }

    @Test
    @DisplayName("une requête sans les champs obligatoires est refusée")
    void rejectsIncompleteRequest() throws Exception {
        HttpResponse<String> response = post("/api/evaluate", "{}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("error"));
    }

    @Test
    @DisplayName("un corps qui n'est pas du JSON est refusé")
    void rejectsMalformedJson() throws Exception {
        assertEquals(400, post("/api/evaluate", "pas du json").statusCode());
    }

    @Test
    @DisplayName("une évaluation valide est acceptée")
    void acceptsValidRequest() throws Exception {
        HttpResponse<String> response = post("/api/evaluate", "{\"project\":\"/tmp/demo\"}");

        assertEquals(202, response.statusCode());
    }

    @Test
    @DisplayName("GET sur la route d'évaluation est refusé")
    void evaluateRejectsGet() throws Exception {
        assertEquals(405, get("/api/evaluate").statusCode());
    }

    @Test
    @DisplayName("le rapport n'est pas disponible avant la fin d'une évaluation")
    void reportIsAbsentBeforeEvaluation() throws Exception {
        assertEquals(404, get("/api/report").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Évite de dépendre d'un rendu de rapport, qui n'est pas encore écrit. */
    private static final class StubReportWriter implements ReportWriter {
        @Override
        public String render(EvaluationResult result) {
            return "rapport de test";
        }

        @Override
        public String fileExtension() {
            return "md";
        }

        @Override
        public String formatName() {
            return "test";
        }
    }
}
