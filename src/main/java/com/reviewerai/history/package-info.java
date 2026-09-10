/**
 * Historique et traçabilité des analyses.
 *
 * <p>Répond à deux exigences du sujet : conserver un historique des évaluations effectuées, et
 * retenir pour chacune de quoi comprendre comment elle s'est déroulée — critères exécutés,
 * modèle utilisé, durée, nombre d'appels, incidents.
 *
 * <p>Trois implémentations pour trois usages :
 * {@link com.reviewerai.history.AnalysisHistory#none()} pour une analyse ponctuelle qui ne
 * doit rien laisser, {@link com.reviewerai.history.InMemoryAnalysisHistory} pour les tests et
 * les démonstrations, {@link com.reviewerai.history.JsonFileAnalysisHistory} pour la
 * persistance réelle.
 *
 * <p><b>Ce qui n'est jamais enregistré</b> — le sujet est explicite : aucune clé d'API, aucun
 * mot de passe, aucun secret, et aucun contenu de fichier du projet évalué. L'historique garde
 * des notes, des durées et des compteurs ; jamais le code d'autrui, jamais un prompt.
 *
 */
package com.reviewerai.history;
