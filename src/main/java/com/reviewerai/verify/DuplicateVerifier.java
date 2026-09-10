package com.reviewerai.verify;

import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Supprime les signalements qui répètent le même problème au même endroit.
 *
 * <p>Un petit modèle reformule volontiers deux fois la même remarque dans une seule réponse.
 * On compare l'emplacement et le titre normalisé, pas l'explication, qui varie toujours un peu.
 */
public final class DuplicateVerifier implements FindingVerifier {

    @Override
    public List<Finding> verify(List<Finding> findings, EvaluationContext context, ProjectSnapshot project) {
        Set<String> seen = new HashSet<>();
        List<Finding> kept = new ArrayList<>();
        for (Finding f : findings) {
            String key = f.location().filePath() + "|" + f.location().line() + "|" + normalise(f.title());
            if (seen.add(key)) {
                kept.add(f);
            }
        }
        return List.copyOf(kept);
    }

    private static String normalise(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
