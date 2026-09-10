package com.reviewerai.history;

import com.reviewerai.model.EvaluationResult;

import java.nio.file.Path;
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
 * du projet évalué, jamais un prompt, jamais un secret. Le répertoire d'historique est créé
 * avec les droits par défaut de l'utilisateur, et n'est jamais servi par le serveur web.
 */
public final class JsonFileAnalysisHistory implements AnalysisHistory {

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
        throw new UnsupportedOperationException("JsonFileAnalysisHistory.record : à implémenter");
    }

    @Override
    public List<HistoryEntry> list() {
        throw new UnsupportedOperationException("JsonFileAnalysisHistory.list : à implémenter");
    }

    @Override
    public Optional<EvaluationResult> find(String id) {
        throw new UnsupportedOperationException("JsonFileAnalysisHistory.find : à implémenter");
    }
}
