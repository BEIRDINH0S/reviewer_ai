package com.reviewerai.view.cli;

import com.reviewerai.config.EvaluationConfig;
import com.reviewerai.config.ServerConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduit les arguments de la ligne de commande en objets de configuration.
 *
 * <p>Deux modes de lancement, donc deux lectures différentes des mêmes arguments : en mode
 * serveur, le projet et les critères viennent du formulaire, pas d'ici.
 *
 * <p>Classe sans état, volontairement : elle transforme un tableau de chaînes en objets et ne
 * retient rien entre deux appels.
 */
public final class CliArguments {

    private CliArguments() {
    }

    /** Vrai si l'utilisateur demande le mode serveur web. */
    public static boolean wantsServe(String[] args) {
        return contains(args, "--serve");
    }

    /** Vrai si l'utilisateur demande le mode hors ligne, sans appel au modèle. */
    public static boolean isOffline(String[] args) {
        return contains(args, "--offline");
    }

    /** Vrai si l'utilisateur demande la liste des critères disponibles. */
    public static boolean wantsCriteriaList(String[] args) {
        return contains(args, "--list-criteria");
    }

    /** Vrai si l'utilisateur demande l'aide. */
    public static boolean wantsHelp(String[] args) {
        return contains(args, "--help") || contains(args, "-h");
    }

    /** Vrai si l'utilisateur demande un rapport Markdown plutôt que LaTeX. */
    public static boolean wantsMarkdown(String[] args) {
        return "markdown".equalsIgnoreCase(optionValue(args, "--format").orElse("latex"));
    }

    /**
     * Configuration d'une évaluation lancée depuis la ligne de commande.
     *
     * <p>{@code --include} et {@code --exclude} sont répétables : chaque occurrence ajoute un
     * motif. C'est plus lisible qu'une liste séparée par des virgules, et cela évite d'avoir à
     * échapper les virgules présentes dans certains motifs.
     *
     * @throws IllegalArgumentException si un argument obligatoire manque ou est mal formé
     */
    public static EvaluationConfig parse(String[] args) {
        var builder = EvaluationConfig.builder();
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--project" -> builder.projectSource(Path.of(value(args, ++i, "--project")));
                case "--criteria" -> builder.criterionIds(splitList(value(args, ++i, "--criteria")));
                case "--include" -> includes.add(value(args, ++i, "--include"));
                case "--exclude" -> excludes.add(value(args, ++i, "--exclude"));
                case "--out" -> builder.reportFile(Path.of(value(args, ++i, "--out")));
                case "--history" -> builder.historyDirectory(Path.of(value(args, ++i, "--history")));
                case "--model" -> builder.modelName(value(args, ++i, "--model"));
                case "--ollama-url" -> builder.ollamaBaseUrl(value(args, ++i, "--ollama-url"));
                case "--max-tokens" -> builder.maxContextTokens(intValue(args, ++i, "--max-tokens"));
                case "--max-files" -> builder.maxFilesPerCriterion(intValue(args, ++i, "--max-files"));
                case "--retries" -> builder.maxAttempts(intValue(args, ++i, "--retries"));
                // Options du mode serveur et du format : lues ailleurs, on saute leur valeur.
                case "--port", "--bind", "--format" -> i++;
                case "--serve", "--offline", "--help", "-h", "--list-criteria" -> { /* drapeaux */ }
                default -> throw new IllegalArgumentException("Option inconnue : " + args[i]);
            }
        }
        return builder.includePatterns(includes).excludePatterns(excludes).build();
    }

    /**
     * Adresse et port d'écoute du serveur web.
     *
     * @throws IllegalArgumentException si une valeur est mal formée
     */
    public static ServerConfig parseServer(String[] args) {
        String bind = ServerConfig.LOOPBACK;
        int port = 8080;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = intValue(args, ++i, "--port");
                case "--bind" -> bind = value(args, ++i, "--bind");
                // Les autres options prenant une valeur : on saute leur valeur pour ne pas la
                // confondre avec une option.
                case "--project", "--criteria", "--include", "--exclude", "--out", "--history",
                     "--model", "--ollama-url", "--max-tokens", "--max-files", "--retries",
                     "--format" -> i++;
                case "--serve", "--offline", "--help", "-h", "--list-criteria" -> { /* drapeaux */ }
                default -> throw new IllegalArgumentException("Option inconnue : " + args[i]);
            }
        }
        return new ServerConfig(bind, port);
    }

    /** Fichier où écrire le rapport ; identique dans les deux modes. */
    public static Path reportFile(String[] args) {
        return optionValue(args, "--out")
                .map(Path::of)
                .orElseGet(() -> Path.of(wantsMarkdown(args) ? "evaluation.md" : "evaluation.tex"));
    }

    /** Le texte d'aide, affiché sans argument ou quand les arguments sont invalides. */
    public static String usage() {
        return """
               AI Project Reviewer — évalue un projet Java et produit un rapport LaTeX.

               Interface web :
                 ai-reviewer --serve [--port 8080] [--bind 127.0.0.1] [--offline]

               Évaluation unique en ligne de commande :
                 ai-reviewer --project CHEMIN [options]

               Le projet peut être un répertoire, un dépôt git ou une archive .zip.

               Options :
                 --criteria a,b,c    critères à évaluer      (défaut : tous)
                 --list-criteria     liste les critères disponibles
                 --include MOTIF     n'évaluer que ces fichiers  (répétable)
                 --exclude MOTIF     exclure ces fichiers        (répétable)
                 --out FICHIER       rapport à écrire        (défaut : evaluation.tex)
                 --format FORMAT     latex ou markdown       (défaut : latex)
                 --history REP       conserver l'historique dans ce répertoire
                 --model NOM         modèle interrogé        (défaut : qwen2.5-coder:7b)
                 --ollama-url URL    serveur de modèle       (défaut : http://127.0.0.1:11434)
                 --max-tokens N      budget de contexte      (défaut : 6000)
                 --max-files N       fichiers par critère    (défaut : 12)
                 --retries N         tentatives par appel    (défaut : 3)
                 --offline           n'appelle aucun modèle
                 --help              affiche ce message
               """;
    }

    private static List<String> splitList(String raw) {
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static java.util.Optional<String> optionValue(String[] args, String option) {
        for (int i = 0; i < args.length - 1; i++) {
            if (option.equals(args[i])) {
                return java.util.Optional.of(args[i + 1]);
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean contains(String[] args, String flag) {
        for (String a : args) {
            if (a.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " attend une valeur");
        }
        return args[index];
    }

    private static int intValue(String[] args, int index, String option) {
        try {
            return Integer.parseInt(value(args, index, option));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " attend un nombre entier", e);
        }
    }
}
