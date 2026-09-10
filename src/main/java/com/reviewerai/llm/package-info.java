/**
 * Brique Modèle — tout ce qui touche aux modèles de langage.
 *
 * <p>Répond à trois exigences du sujet, chacune par un mécanisme distinct :
 * <ul>
 *   <li><b>abstraction du fournisseur</b> — {@link com.reviewerai.llm.LlmProvider} ne connaît
 *       ni URL, ni format JSON, ni bibliothèque. Changer de modèle, c'est écrire une classe et
 *       la nommer dans la fabrique ;
 *   <li><b>résilience</b> — {@link com.reviewerai.llm.RetryingLlmProvider} réessaie ce qui
 *       peut l'être et abandonne proprement le reste, sans qu'aucun fournisseur n'ait à s'en
 *       occuper ;
 *   <li><b>traçabilité</b> — {@link com.reviewerai.llm.CountingLlmProvider} compte les appels
 *       et mesure les durées, sans jamais journaliser le contenu des prompts.
 * </ul>
 *
 * <p>Les deux derniers sont des décorateurs : ils implémentent la même interface que ce qu'ils
 * enveloppent, se composent dans n'importe quel ordre, et se retirent d'une ligne.
 *
 * <p>La frontière de confiance passe par
 * {@link com.reviewerai.llm.CriterionResponseParser} : au-dessus, tout est validé ; en
 * dessous, tout est suspect. Le modèle a lu du code d'un projet tiers, qui peut contenir une
 * injection de prompt — sa réponse ne vaut donc pas mieux que ce code.
 *
 * <p>Le modèle ne dispose d'aucun outil. Il reçoit du texte, il renvoie du texte. C'est ce qui
 * limite l'effet d'une injection réussie à une note erronée.
 *
 */
package com.reviewerai.llm;
