package com.reviewerai.view.web;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.config.ServerConfig;
import com.reviewerai.controller.EvaluationController;
import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.history.AnalysisHistory;
import com.reviewerai.model.EvaluationResult;
import com.reviewerai.model.ProjectSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Sert l'interface web et l'API que la page utilise.
 *
 * <p>Le serveur est celui du JDK ({@code com.sun.net.httpserver}) : aucune dépendance à
 * ajouter, au prix d'un peu de plomberie écrite ici.
 *
 * <p><b>Pourquoi une page web plutôt que JavaFX ou Swing</b> — le sujet autorise « une autre
 * solution Java que vous pouvez justifier ». Trois raisons, à reprendre dans le rapport :
 * l'équipe travaille sous Windows et macOS et le rendu du navigateur est identique partout ;
 * l'interface reste utilisable quand l'analyse tourne dans un conteneur, ce qui n'est pas le
 * cas d'une fenêtre native ; et aucune dépendance graphique n'entre dans le projet.
 *
 * <p>Les routes :
 * <ul>
 *   <li>{@code GET /} — la page, son script et sa feuille de style ;
 *   <li>{@code GET /api/criteria} — la liste des critères, pour les cases à cocher ;
 *   <li>{@code POST /api/project} — charge un projet et renvoie son arborescence ;
 *   <li>{@code POST /api/evaluate} — lance une évaluation et répond immédiatement ;
 *   <li>{@code GET /api/status} — l'état courant, interrogé régulièrement ;
 *   <li>{@code GET /api/report} — le rapport, une fois l'évaluation terminée ;
 *   <li>{@code GET /api/history} — la liste des analyses passées ;
 *   <li>{@code GET /api/history/{id}} — une analyse passée, en entier.
 * </ul>
 *
 * <p>L'évaluation tourne dans un fil séparé : une requête HTTP qui durerait plusieurs minutes
 * serait abandonnée par le navigateur bien avant la fin.
 */
public final class WebServer implements AutoCloseable {

    /**
     * Les fichiers statiques servis, associés à leur type de contenu.
     *
     * <p>Le JavaScript est un fichier à part plutôt qu'un bloc dans la page : cela permet
     * d'interdire les scripts en ligne dans l'en-tête {@code Content-Security-Policy}, et donc
     * de neutraliser une injection qui parviendrait jusqu'au HTML.
     */
    private static final Map<String, StaticResource> STATIC_FILES = Map.of(
            "/", new StaticResource("/web/index.html", "text/html; charset=utf-8"),
            "/index.html", new StaticResource("/web/index.html", "text/html; charset=utf-8"),
            "/app.js", new StaticResource("/web/app.js", "text/javascript; charset=utf-8"),
            "/style.css", new StaticResource("/web/style.css", "text/css; charset=utf-8"));

    /** Au-delà, l'arborescence renvoyée au navigateur est tronquée. */
    private static final int MAX_TREE_FILES = 500;

    private record StaticResource(String path, String contentType) {
    }

    private final ServerConfig config;
    private final Function<EvaluationConfig, EvaluationController> controllerFactory;
    private final Function<Path, ProjectSnapshot> projectPreview;
    private final List<CriterionDescriptor> criteria;
    private final AnalysisHistory history;
    private final EvaluationWebView view = new EvaluationWebView();
    private final WebJson json = new WebJson();
    private final Path reportFile;

    private HttpServer server;
    private ExecutorService analysisExecutor;

    /**
     * @param config            adresse et port d'écoute
     * @param reportFile        fichier où le rapport est écrit à chaque évaluation
     * @param criteria          les critères proposés dans le formulaire
     * @param projectPreview    charge un projet pour en afficher l'arborescence
     * @param controllerFactory fabrique un contrôleur pour une configuration donnée ; injectée
     *                          plutôt que codée en dur, pour que les tests fournissent un
     *                          contrôleur factice
     * @param history           l'historique consulté par les routes {@code /api/history} ;
     *                          c'est le même que celui où le service consigne ses analyses
     */
    public WebServer(ServerConfig config,
                     Path reportFile,
                     List<CriterionDescriptor> criteria,
                     Function<Path, ProjectSnapshot> projectPreview,
                     Function<EvaluationConfig, EvaluationController> controllerFactory,
                     AnalysisHistory history) {
        this.config = Objects.requireNonNull(config, "config");
        this.reportFile = Objects.requireNonNull(reportFile, "reportFile");
        this.criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
        this.projectPreview = Objects.requireNonNull(projectPreview, "projectPreview");
        this.controllerFactory = Objects.requireNonNull(controllerFactory, "controllerFactory");
        this.history = Objects.requireNonNull(history, "history");
    }

    /** Démarre le serveur. Rend la main aussitôt, le serveur tourne en arrière-plan. */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.bindAddress(), config.port()), 0);

        server.createContext("/", this::handleStatic);
        server.createContext("/api/criteria", this::handleCriteria);
        server.createContext("/api/project", this::handleProject);
        server.createContext("/api/evaluate", this::handleEvaluate);
        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/history", this::handleHistory);

        // Les requêtes sont courtes : deux fils suffisent largement.
        server.setExecutor(Executors.newFixedThreadPool(2));
        // Un seul fil d'analyse : on ne veut pas de deux évaluations simultanées.
        analysisExecutor = Executors.newSingleThreadExecutor();

        server.start();
    }

    /** Le port réellement utilisé, utile quand on demande au système d'en choisir un. */
    public int port() {
        return server == null ? config.port() : server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdownNow();
        }
    }

    // --- routes ---

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain", "Méthode non autorisée");
            return;
        }
        // createContext("/") attrape tout ce qui n'a pas trouvé de route plus précise.
        StaticResource resource = STATIC_FILES.get(exchange.getRequestURI().getPath());
        if (resource == null) {
            respond(exchange, 404, "text/plain", "Page inconnue");
            return;
        }
        respond(exchange, 200, resource.contentType(), readResource(resource.path()));
    }

    private void handleCriteria(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 405, json.toErrorJson("Méthode non autorisée"));
            return;
        }
        respondJson(exchange, 200, json.toJson(criteria));
    }

    private void handleProject(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 405, json.toErrorJson("Méthode non autorisée"));
            return;
        }
        String body = readBody(exchange);
        try {
            EvaluationConfig probe = json.toConfig(body, reportFile);
            ProjectSnapshot snapshot = projectPreview.apply(probe.projectSource());
            respondJson(exchange, 200, json.toJson(snapshot, MAX_TREE_FILES));
        } catch (IllegalArgumentException | IOException e) {
            respondJson(exchange, 400, json.toErrorJson("Projet illisible : " + e.getMessage()));
        } catch (RuntimeException e) {
            respondJson(exchange, 400, json.toErrorJson("Projet illisible : " + e.getMessage()));
        }
    }

    private void handleEvaluate(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 405, json.toErrorJson("Méthode non autorisée"));
            return;
        }

        String body = readBody(exchange);
        EvaluationConfig evaluationConfig;
        try {
            evaluationConfig = json.toConfig(body, reportFile);
        } catch (IllegalArgumentException | IOException e) {
            respondJson(exchange, 400, json.toErrorJson("Requête invalide : " + e.getMessage()));
            return;
        }

        if (!view.tryStart()) {
            respondJson(exchange, 409, json.toErrorJson("Une évaluation est déjà en cours"));
            return;
        }

        EvaluationController controller = controllerFactory.apply(evaluationConfig);
        analysisExecutor.submit(() -> controller.runEvaluation(view));

        respondJson(exchange, 202, "{\"started\":true}");
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 405, json.toErrorJson("Méthode non autorisée"));
            return;
        }
        respondJson(exchange, 200, json.toJson(view.currentState()));
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain", "Méthode non autorisée");
            return;
        }
        String report = view.currentState().report();
        if (report.isEmpty()) {
            respond(exchange, 404, "text/plain", "Aucun rapport disponible");
            return;
        }
        respond(exchange, 200, "text/plain; charset=utf-8", report);
    }

    /**
     * Sert la liste de l'historique, ou une analyse précise selon le chemin.
     *
     * <p>L'identifiant vient du navigateur : il ne sert jamais à construire un chemin de fichier
     * sans contrôle. {@code AnalysisHistory.find} rejette tout identifiant qui tenterait de sortir
     * du répertoire, ce qui devient ici un 404 plutôt qu'une lecture hors du dossier. Un
     * identifiant inconnu donne aussi 404, jamais une erreur 500.
     */
    private void handleHistory(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respondJson(exchange, 405, json.toErrorJson("Méthode non autorisée"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/history") || path.equals("/api/history/")) {
            respondJson(exchange, 200, json.toHistoryJson(history.list()));
            return;
        }
        String id = URLDecoder.decode(path.substring("/api/history/".length()), StandardCharsets.UTF_8);
        Optional<EvaluationResult> found = history.find(id);
        if (found.isEmpty()) {
            respondJson(exchange, 404, json.toErrorJson("Analyse introuvable"));
            return;
        }
        respondJson(exchange, 200, json.toResultJson(found.get()));
    }

    // --- utilitaires HTTP ---

    private String readResource(String resourcePath) throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Ressource introuvable : " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, "application/json; charset=utf-8", body);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // La page n'inclut aucune ressource externe et aucun script en ligne. On l'annonce au
        // navigateur : si un texte non échappé parvenait malgré tout jusqu'au HTML, le script
        // qu'il contiendrait ne serait pas exécuté.
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
