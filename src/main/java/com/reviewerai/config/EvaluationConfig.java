package com.reviewerai.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Les réglages d'une évaluation. Immuable, construite une fois, injectée dans les briques.
 *
 * <p>Regrouper les réglages ici plutôt que de les lire depuis des variables globales garde les
 * briques testables : un test fournit sa propre configuration sans toucher à l'environnement.
 * C'est aussi ce qui permet au rapport d'indiquer la configuration utilisée, exigence du sujet.
 *
 * @param projectSource     le projet à évaluer : répertoire, dépôt git ou archive
 * @param criterionIds      critères demandés ; liste vide signifie « tous »
 * @param includePatterns   motifs de fichiers à inclure ; liste vide signifie « tout »
 * @param excludePatterns   motifs de fichiers à exclure
 * @param reportFile        fichier du rapport à écrire
 * @param historyDirectory  répertoire de l'historique, ou {@code null} pour ne rien conserver
 * @param ollamaBaseUrl     URL du serveur de modèle (boucle locale par défaut, cf. sécurité)
 * @param modelName         nom du modèle interrogé
 * @param maxContextTokens  budget de jetons pour le contexte d'UN critère
 * @param maxResponseTokens plafond de la réponse attendue du modèle
 * @param maxFilesPerCriterion nombre maximal de fichiers envoyés pour un critère
 * @param maxFileSizeBytes  taille maximale d'un fichier lu (protection fichiers piégés)
 * @param llmTimeoutSeconds délai maximal d'un appel au modèle
 * @param maxAttempts       nombre total de tentatives par appel, la première comprise
 * @param minConfidence     confiance en dessous de laquelle un signalement est écarté
 * @param maxNeighborDepth  profondeur d'exploration du graphe d'appel autour d'une méthode centrale
 * @param maxNeighbors      nombre maximal de méthodes voisines jointes à une méthode centrale
 */
public record EvaluationConfig(
        Path projectSource,
        List<String> criterionIds,
        List<String> includePatterns,
        List<String> excludePatterns,
        Path reportFile,
        Path historyDirectory,
        String ollamaBaseUrl,
        String modelName,
        int maxContextTokens,
        int maxResponseTokens,
        int maxFilesPerCriterion,
        long maxFileSizeBytes,
        int llmTimeoutSeconds,
        int maxAttempts,
        double minConfidence,
        int maxNeighborDepth,
        int maxNeighbors) {

    public EvaluationConfig {
        Objects.requireNonNull(projectSource, "projectSource");
        criterionIds = criterionIds == null ? List.of() : List.copyOf(criterionIds);
        includePatterns = includePatterns == null ? List.of() : List.copyOf(includePatterns);
        excludePatterns = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Le délai d'appel au modèle, sous une forme directement utilisable. */
    public Duration llmTimeout() {
        return Duration.ofSeconds(llmTimeoutSeconds);
    }

    /** Le répertoire d'historique, absent si l'analyse ne doit rien conserver. */
    public Optional<Path> history() {
        return Optional.ofNullable(historyDirectory);
    }

    /**
     * Résumé lisible de la configuration, repris tel quel dans le rapport.
     *
     * <p>Le sujet exige que le rapport indique la configuration utilisée. Produire ce texte ici
     * plutôt que dans chaque {@code ReportWriter} évite que les formats divergent, et garantit
     * qu'un réglage ajouté demain apparaîtra partout.
     */
    public String describe() {
        return "modèle %s · budget %d jetons · %d fichiers/critère · confiance ≥ %.2f · %d tentative(s)"
                .formatted(modelName, maxContextTokens, maxFilesPerCriterion, minConfidence, maxAttempts);
    }

    /**
     * Construction pas à pas avec des valeurs par défaut raisonnables.
     *
     * <p><b>Patron de conception : Monteur</b> (création)
     * <dl>
     *   <dt>Problème traité</dt>
     *   <dd>Comment construire un objet à quinze composants, dont quatorze ont une valeur par
     *       défaut, sans imposer une liste d'arguments positionnels illisible ni multiplier les
     *       constructeurs ?</dd>
     *   <dt>Solution</dt>
     *   <dd>Chaque réglage a sa méthode, appelée seulement si l'on veut s'écarter du défaut.
     *       {@link #build()} vérifie les champs obligatoires et produit l'objet final.</dd>
     *   <dt>Remarques</dt>
     *   <dd>Le cours présente un monteur abstrait dont les classes filles redéfinissent les
     *       étapes. Ici il n'existe qu'une façon d'assembler une configuration : le monteur
     *       sert uniquement à nommer les arguments. L'objet produit est immuable ; le monteur
     *       ne l'est pas et n'est pas destiné à être partagé entre fils d'exécution.</dd>
     * </dl>
     */
    public static final class Builder {
        private Path projectSource;
        private List<String> criterionIds = List.of();
        private List<String> includePatterns = List.of();
        private List<String> excludePatterns = List.of();
        private Path reportFile = Path.of("evaluation.tex");
        private Path historyDirectory;
        // Par défaut le serveur de modèle n'écoute que sur la boucle locale : son API n'a
        // aucune authentification, l'exposer demande une décision explicite.
        private String ollamaBaseUrl = "http://127.0.0.1:11434";
        private String modelName = "qwen2.5-coder:7b";
        private int maxContextTokens = 6000;
        private int maxResponseTokens = 1200;
        private int maxFilesPerCriterion = 12;
        private long maxFileSizeBytes = 1_000_000L;
        private int llmTimeoutSeconds = 900;
        private int maxAttempts = 3;
        private double minConfidence = 0.5;
        // Une seule couche de voisinage suffit à montrer les relations directes ; au-delà, le
        // contexte se dilue. Six voisines : assez pour juger le couplage, sans noyer le budget.
        private int maxNeighborDepth = 1;
        private int maxNeighbors = 6;

        public Builder projectSource(Path v) { this.projectSource = v; return this; }
        public Builder criterionIds(List<String> v) { this.criterionIds = v; return this; }
        public Builder includePatterns(List<String> v) { this.includePatterns = v; return this; }
        public Builder excludePatterns(List<String> v) { this.excludePatterns = v; return this; }
        public Builder reportFile(Path v) { this.reportFile = v; return this; }
        public Builder historyDirectory(Path v) { this.historyDirectory = v; return this; }
        public Builder ollamaBaseUrl(String v) { this.ollamaBaseUrl = v; return this; }
        public Builder modelName(String v) { this.modelName = v; return this; }
        public Builder maxContextTokens(int v) { this.maxContextTokens = v; return this; }
        public Builder maxResponseTokens(int v) { this.maxResponseTokens = v; return this; }
        public Builder maxFilesPerCriterion(int v) { this.maxFilesPerCriterion = v; return this; }
        public Builder maxFileSizeBytes(long v) { this.maxFileSizeBytes = v; return this; }
        public Builder llmTimeoutSeconds(int v) { this.llmTimeoutSeconds = v; return this; }
        public Builder maxAttempts(int v) { this.maxAttempts = v; return this; }
        public Builder minConfidence(double v) { this.minConfidence = v; return this; }
        public Builder maxNeighborDepth(int v) { this.maxNeighborDepth = v; return this; }
        public Builder maxNeighbors(int v) { this.maxNeighbors = v; return this; }

        /**
         * @throws IllegalStateException si le projet à évaluer n'a pas été indiqué
         * @throws IllegalArgumentException si un réglage numérique est hors bornes
         */
        public EvaluationConfig build() {
            if (projectSource == null) {
                throw new IllegalStateException("projectSource est obligatoire");
            }
            requirePositive(maxContextTokens, "maxContextTokens");
            requirePositive(maxResponseTokens, "maxResponseTokens");
            requirePositive(maxFilesPerCriterion, "maxFilesPerCriterion");
            requirePositive(llmTimeoutSeconds, "llmTimeoutSeconds");
            requirePositive(maxAttempts, "maxAttempts");
            requirePositive(maxNeighborDepth, "maxNeighborDepth");
            requirePositive(maxNeighbors, "maxNeighbors");
            if (maxFileSizeBytes <= 0) {
                throw new IllegalArgumentException("maxFileSizeBytes doit être positif");
            }
            if (minConfidence < 0.0 || minConfidence > 1.0) {
                throw new IllegalArgumentException("minConfidence hors [0,1] : " + minConfidence);
            }
            return new EvaluationConfig(projectSource, criterionIds, includePatterns, excludePatterns,
                    reportFile, historyDirectory, ollamaBaseUrl, modelName, maxContextTokens,
                    maxResponseTokens, maxFilesPerCriterion, maxFileSizeBytes, llmTimeoutSeconds,
                    maxAttempts, minConfidence, maxNeighborDepth, maxNeighbors);
        }

        private static void requirePositive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " doit être positif : " + value);
            }
        }
    }
}
