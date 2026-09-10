package com.reviewerai.verify;

import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectSnapshot;

import java.util.List;

/**
 * Brique Vérification — écarte les signalements non justifiés.
 *
 * <p>Un modèle local de petite taille produit des faux positifs : c'est attendu, et c'est
 * précisément ce que cette brique corrige. Elle est le dernier filtre avant le rapport, et
 * détermine à elle seule si le résultat est utilisable ou si personne ne le lit.
 *
 * <p><b>Patron de conception : Stratégie</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Comment définir une gamme de règles de filtrage, les encapsuler dans des classes
 *       distinctes et rendre leurs instances interchangeables ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Chaque règle est une classe indépendante implémentant cette interface.
 *       {@link CompositeFindingVerifier} les enchaîne, et l'appelant ne connaît que
 *       l'interface.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Le cours présente la stratégie comme une classe abstraite portant un attribut
 *       {@code context}. Ici le contexte est passé en paramètre à chaque appel : les règles
 *       n'ont pas d'état, ce qui les rend utilisables en parallèle sans précaution. Ajouter
 *       une règle ne modifie aucune classe existante (principe ouvert/fermé).</dd>
 * </dl>
 *
 * <p>C'est aussi une mesure de sécurité, pas seulement de qualité : un signalement qui cite un
 * fichier inexistant est le signe que le modèle a inventé — ou qu'il a suivi une consigne
 * glissée dans le code évalué.
 */
@FunctionalInterface
public interface FindingVerifier {

    /**
     * @param findings les signalements bruts, tels que le modèle les a produits
     * @param context  le contexte qui a servi à les produire
     * @param project  le projet évalué, qui fait autorité sur ce qui existe
     * @return les signalements conservés, dans l'ordre reçu
     */
    List<Finding> verify(List<Finding> findings, EvaluationContext context, ProjectSnapshot project);
}
