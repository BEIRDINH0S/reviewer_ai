/**
 * AI Project Reviewer — évalue automatiquement un projet Java et produit un rapport LaTeX.
 *
 * <p>L'application charge un projet (répertoire, dépôt git ou archive), en dresse
 * l'inventaire, l'évalue selon des critères notés — les uns déterministes, les autres confiés
 * à un modèle de langage local — puis produit un rapport structuré.
 *
 * <p>Architecture Modèle-Vue-Contrôleur. Les dépendances vont dans un seul sens :
 * <b>Vue → Contrôleur → Service → Briques → Modèle</b>. Le modèle ne connaît personne, la vue
 * ne connaît aucune brique.
 *
 * <p>Les briques, chacune derrière une interface :
 * <ul>
 *   <li>{@link com.reviewerai.project} — charger le projet et choisir les fichiers ;
 *   <li>{@link com.reviewerai.criteria} — les critères d'évaluation ;
 *   <li>{@link com.reviewerai.context} — décider ce qui part au modèle ;
 *   <li>{@link com.reviewerai.llm} — interroger le modèle, avec résilience et traçabilité ;
 *   <li>{@link com.reviewerai.verify} — écarter ce que le modèle a inventé ;
 *   <li>{@link com.reviewerai.report} — produire le document ;
 *   <li>{@link com.reviewerai.history} — conserver les évaluations passées.
 * </ul>
 *
 * <p><b>Sécurité</b> : le projet évalué est du code non fiable. On le lit, on ne l'exécute
 * jamais — ni son système de construction, ni ses scripts. Le modèle ne dispose d'aucun outil,
 * et sa réponse est validée puis échappée selon le format de sortie.
 */
package com.reviewerai;
