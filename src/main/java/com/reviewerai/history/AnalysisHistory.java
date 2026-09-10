package com.reviewerai.history;

import com.reviewerai.model.EvaluationResult;

import java.util.List;
import java.util.Optional;

/**
 * Conserve les évaluations passées.
 *
 * <p>Répond à deux exigences du sujet : garder un historique des analyses effectuées, et
 * retenir pour chacune de quoi comprendre comment elle s'est déroulée — critères exécutés,
 * modèle utilisé, durée, nombre d'appels, incidents rencontrés.
 *
 * <p>L'interface est délibérément minimale : trois opérations, aucune notion de base de
 * données. Elle permet aussi bien une implémentation en mémoire pour les tests qu'une
 * implémentation sur disque en production, et laisserait passer une base SQL le jour où le
 * volume l'exigerait.
 *
 * <p><b>Ce qui ne doit jamais être enregistré</b> : le sujet est explicite, et c'est un point
 * d'attention permanent pour toute implémentation — aucune clé d'API, aucun mot de passe,
 * aucun secret, aucun contenu de fichier du projet évalué. On garde des notes, des durées et
 * des compteurs ; pas le code d'autrui.
 */
public interface AnalysisHistory {

    /**
     * Enregistre une évaluation.
     *
     * @param result l'évaluation à conserver
     * @return l'identifiant attribué
     */
    String record(EvaluationResult result);

    /** Les évaluations passées, de la plus récente à la plus ancienne. */
    List<HistoryEntry> list();

    /** Une évaluation complète, par son identifiant. */
    Optional<EvaluationResult> find(String id);

    /**
     * Historique qui n'enregistre rien.
     *
     * <p>Objet nul : le service n'a pas à tester la présence d'un historique avant chaque
     * analyse, et le mode ligne de commande ponctuel n'a aucune raison de laisser des traces.
     */
    static AnalysisHistory none() {
        return new AnalysisHistory() {
            @Override
            public String record(EvaluationResult result) {
                return "";
            }

            @Override
            public List<HistoryEntry> list() {
                return List.of();
            }

            @Override
            public Optional<EvaluationResult> find(String id) {
                return Optional.empty();
            }
        };
    }
}
