package com.reviewerai.criteria;

import com.reviewerai.model.CodeLocation;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.Severity;
import com.reviewerai.service.ProgressListener;
import com.reviewerai.util.SafeFiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Mesure la duplication de code, sans interroger de modèle.
 *
 * <p>Le sujet liste « duplication de code » parmi les critères possibles. C'est mesurable
 * exactement, donc c'est un travail pour un critère déterministe, pas pour le modèle.
 *
 * <p>Algorithme des détecteurs classiques, en beaucoup plus court : chaque fichier de production
 * est découpé en fenêtres glissantes de {@value #WINDOW} lignes <b>normalisées</b> — indentation
 * et commentaires retirés, espaces internes réduits. Deux fenêtres identiques dans des endroits
 * différents révèlent un bloc dupliqué. La fenêtre de six lignes est ce qui évite les faux
 * positifs : un getter d'une ligne ne la remplit jamais.
 *
 * <p>Le critère produit de vrais signalements localisés — « ce bloc est identique à tel autre » —
 * et pas seulement une note.
 *
 * <p>C'est aussi la démonstration vivante de l'extensibilité demandée par le sujet : l'ajouter
 * n'a touché que deux fichiers, cette classe et une ligne dans {@code EvaluationServiceFactory}.
 * Ni le moteur, ni le rapport, ni les vues, ni l'historique, ni la ligne de commande ne bougent.
 */
public final class DuplicationCriterion implements Criterion {

    /** Identifiant stable, utilisé dans la configuration, le rapport et l'historique. */
    public static final String ID = "duplication";

    /** Taille d'une fenêtre, en lignes normalisées : assez pour exclure un getter banal. */
    private static final int WINDOW = 6;

    /** Au-delà, la liste des signalements devient illisible ; la note, elle, tient déjà le compte. */
    private static final int MAX_FINDINGS = 15;

    private final long maxFileSizeBytes;

    /**
     * @param maxFileSizeBytes taille maximale d'un fichier lu, alignée sur la configuration
     */
    public DuplicationCriterion(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public CriterionDescriptor descriptor() {
        return CriterionDescriptor.deterministic(ID, "Duplication de code", """
                Blocs de code identiques entre fichiers, détectés par fenêtres glissantes de
                lignes normalisées, sans modèle.""");
    }

    @Override
    public CriterionResult evaluate(ProjectSnapshot project, ProgressListener listener) {
        Path root = project.project().root();
        Map<String, List<Occurrence>> blocks = new TreeMap<>();
        for (ProjectFile file : project.ofKind(FileKind.JAVA_MAIN)) {
            index(root, file, blocks);
        }

        List<Finding> findings = duplications(blocks);
        int maxScore = descriptor().maxScore();
        int score = Math.max(0, maxScore - findings.size());

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        if (findings.isEmpty()) {
            strengths.add("Aucun bloc dupliqué de %d lignes ou plus n'a été détecté.".formatted(WINDOW));
        } else {
            weaknesses.add("%d bloc(s) de code dupliqué(s) entre fichiers.".formatted(findings.size()));
            recommendations.add("Extraire les blocs identiques dans une méthode ou une classe partagée.");
        }

        String summary = findings.isEmpty()
                ? "Aucune duplication significative entre les fichiers de production."
                : "%d bloc(s) dupliqué(s) relevé(s) dans les fichiers de production.".formatted(findings.size());

        return new CriterionResult(ID, descriptor().label(), score, maxScore,
                summary, List.copyOf(strengths), List.copyOf(weaknesses), List.copyOf(recommendations),
                List.copyOf(findings), true);
    }

    /** Découpe un fichier en fenêtres normalisées et les range par empreinte. */
    private void index(Path root, ProjectFile file, Map<String, List<Occurrence>> blocks) {
        var content = SafeFiles.readText(root, root.resolve(file.path()), maxFileSizeBytes);
        if (content.isEmpty()) {
            return; // illisible : on passe, sans faire échouer le critère
        }
        List<Line> lines = normalize(content.get());
        for (int i = 0; i + WINDOW <= lines.size(); i++) {
            StringBuilder key = new StringBuilder();
            for (int j = i; j < i + WINDOW; j++) {
                key.append(lines.get(j).text()).append('\n');
            }
            blocks.computeIfAbsent(key.toString(), k -> new ArrayList<>())
                    .add(new Occurrence(file.path(), lines.get(i).number(), lines.get(i + WINDOW - 1).number()));
        }
    }

    /**
     * Transforme les empreintes partagées en signalements, au plus un par paire de fichiers.
     *
     * <p>Deux blocs qui se chevauchent produisent plusieurs empreintes identiques ; sans ce
     * dédoublonnage par paire, une seule duplication génèrerait dix signalements. On garde le
     * premier bloc de chaque paire, ce qui suffit à pointer le problème.
     */
    private static List<Finding> duplications(Map<String, List<Occurrence>> blocks) {
        List<Finding> findings = new ArrayList<>();
        Set<String> reportedPairs = new HashSet<>();
        for (List<Occurrence> occurrences : blocks.values()) {
            if (findings.size() >= MAX_FINDINGS) {
                break;
            }
            if (occurrences.size() < 2) {
                continue;
            }
            Occurrence first = occurrences.get(0);
            Occurrence second = occurrences.get(1);
            if (!reportedPairs.add(pairKey(first.file(), second.file()))) {
                continue;
            }
            findings.add(new Finding(
                    CodeLocation.ofLine(first.file(), first.startLine()),
                    Severity.MEDIUM,
                    "Bloc dupliqué de %d lignes".formatted(WINDOW),
                    "Identique à %s:%d. La duplication double le coût de toute correction future."
                            .formatted(second.file(), second.startLine()),
                    1.0)); // correspondance exacte : confiance totale
        }
        return findings;
    }

    /** Clé d'une paire de fichiers, indépendante de l'ordre. */
    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + '|' + b : b + '|' + a;
    }

    /**
     * Normalise un fichier : retire commentaires, indentation et lignes vides, réduit les espaces.
     *
     * <p>Heuristique volontairement simple. Elle ne distingue pas un {@code //} dans une chaîne
     * de caractères d'un vrai commentaire ; le risque est de rater ou d'inventer une duplication
     * dans ce cas rare, jamais de faire échouer l'analyse.
     */
    private static List<Line> normalize(String content) {
        List<Line> result = new ArrayList<>();
        boolean inBlockComment = false;
        String[] rawLines = content.split("\n", -1);
        for (int index = 0; index < rawLines.length; index++) {
            String raw = rawLines[index];
            StringBuilder kept = new StringBuilder();
            int cursor = 0;
            while (cursor < raw.length()) {
                if (inBlockComment) {
                    int end = raw.indexOf("*/", cursor);
                    if (end < 0) {
                        cursor = raw.length();
                    } else {
                        inBlockComment = false;
                        cursor = end + 2;
                    }
                    continue;
                }
                int blockStart = raw.indexOf("/*", cursor);
                int lineComment = raw.indexOf("//", cursor);
                if (lineComment >= 0 && (blockStart < 0 || lineComment < blockStart)) {
                    kept.append(raw, cursor, lineComment);
                    cursor = raw.length();
                } else if (blockStart >= 0) {
                    kept.append(raw, cursor, blockStart);
                    inBlockComment = true;
                    cursor = blockStart + 2;
                } else {
                    kept.append(raw, cursor, raw.length());
                    cursor = raw.length();
                }
            }
            String normalized = kept.toString().replaceAll("\\s+", " ").trim();
            if (!normalized.isEmpty()) {
                result.add(new Line(normalized, index + 1));
            }
        }
        return result;
    }

    /** Une ligne normalisée et son numéro d'origine (1-indexé). */
    private record Line(String text, int number) {
        private Line {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Une occurrence d'un bloc dans un fichier. */
    private record Occurrence(String file, int startLine, int endLine) {
    }
}
