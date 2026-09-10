package com.reviewerai.verify;

import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectSnapshot;

import java.util.List;

/**
 * Écarte les signalements dont la confiance annoncée est trop faible.
 *
 * <p>Règle la plus simple et la plus efficace : relever le seuil est le premier levier quand
 * le rapport contient trop de bruit.
 */
public final class ConfidenceVerifier implements FindingVerifier {

    private final double minConfidence;

    public ConfidenceVerifier(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    @Override
    public List<Finding> verify(List<Finding> findings, EvaluationContext context, ProjectSnapshot project) {
        return findings.stream()
                .filter(f -> f.confidence() >= minConfidence)
                .toList();
    }
}
