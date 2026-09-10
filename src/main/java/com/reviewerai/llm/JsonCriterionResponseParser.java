package com.reviewerai.llm;

import com.reviewerai.criteria.CriterionDescriptor;
import com.reviewerai.model.CriterionResult;
import com.reviewerai.model.ProjectSnapshot;

/**
 * Lit la réponse JSON du modèle et la valide.
 *
 * <p>Implémentation de référence de {@link CriterionResponseParser}, appuyée sur Jackson en
 * mode lecture d'arbre : on ne désérialise jamais directement vers un {@code record}. Une
 * désérialisation automatique accepterait tout ce que le modèle a écrit ; ici chaque champ est
 * lu, contrôlé et normalisé un par un.
 *
 * <p>Sans état, donc partageable entre critères et utilisable en parallèle : le projet évalué
 * arrive en paramètre plutôt que par le constructeur.
 *
 * <p>Le format attendu est celui du sujet :
 * <pre>
 * {
 *   "criterion": "Architecture",
 *   "score": 7,
 *   "maxScore": 10,
 *   "summary": "…",
 *   "strengths": ["…"],
 *   "weaknesses": ["…"],
 *   "recommendations": ["…"],
 *   "findings": [
 *     {"file": "src/…/A.java", "line": 42, "severity": "HIGH", "title": "…",
 *      "explanation": "…", "confidence": 0.8}
 *   ]
 * }
 * </pre>
 *
 */
public final class JsonCriterionResponseParser implements CriterionResponseParser {

    /** Au-delà, on considère que le modèle remplit du vide plutôt qu'il n'analyse. */
    public static final int MAX_ITEMS_PER_LIST = 10;

    /**
     * Marche à suivre pour l'implémentation :
     * <ol>
     *   <li>isoler le premier objet JSON de la réponse — repérer la première {@code &#123;} et
     *       l'accolade fermante correspondante, en tenant compte des chaînes de caractères ;
     *   <li>lire l'arbre avec {@code ObjectMapper.readTree} ; toute exception de Jackson
     *       devient une {@link InvalidResponseException}, jamais une remontée telle quelle ;
     *   <li>note : lire {@code score}, la ramener dans {@code [0, descriptor.maxScore()]} ;
     *       ignorer le {@code maxScore} renvoyé par le modèle, celui du descripteur fait foi ;
     *   <li>listes de texte : ignorer les entrées vides, tronquer les trop longues, et n'en
     *       garder que {@link #MAX_ITEMS_PER_LIST} au plus ;
     *   <li>signalements : ne garder que ceux dont le fichier existe dans {@code project}
     *       (via {@code project.byPath}) ; {@code severity} passe par {@code Severity.parse},
     *       {@code confidence} est ramenée dans {@code [0,1]} ;
     *   <li>construire le {@link CriterionResult} avec l'identifiant et le libellé du
     *       descripteur — jamais avec le nom de critère renvoyé par le modèle.
     * </ol>
     */
    @Override
    public CriterionResult parse(String rawResponse, CriterionDescriptor descriptor, ProjectSnapshot project) {
        throw new UnsupportedOperationException("JsonCriterionResponseParser : à implémenter");
    }
}
