package com.reviewerai.history;

import com.reviewerai.model.EvaluationResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Historique conservé en mémoire, perdu à l'arrêt du programme.
 *
 * <p>Sert aux tests et au serveur web lancé pour une démonstration : on veut voir la liste des
 * analyses de la session, sans écrire quoi que ce soit sur le disque.
 *
 * <p>Synchronisé, car le serveur web enregistre depuis le fil d'analyse et lit depuis les fils
 * qui répondent aux requêtes HTTP.
 */
public final class InMemoryAnalysisHistory implements AnalysisHistory {

    private final Map<String, EvaluationResult> results = new LinkedHashMap<>();

    @Override
    public synchronized String record(EvaluationResult result) {
        String id = "analyse-" + (results.size() + 1);
        results.put(id, result);
        return id;
    }

    @Override
    public synchronized List<HistoryEntry> list() {
        List<HistoryEntry> entries = new ArrayList<>();
        results.forEach((id, result) -> entries.add(HistoryEntry.of(id, result)));
        entries.sort(Comparator.comparing(HistoryEntry::analysedAt).reversed());
        return List.copyOf(entries);
    }

    @Override
    public synchronized Optional<EvaluationResult> find(String id) {
        return Optional.ofNullable(results.get(id));
    }

    /** Nombre d'analyses conservées. */
    public synchronized int size() {
        return results.size();
    }
}
