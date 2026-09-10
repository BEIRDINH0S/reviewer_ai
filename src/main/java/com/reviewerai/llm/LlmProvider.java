package com.reviewerai.llm;

/**
 * Accès à un modèle de langage. Du texte entre, du texte sort.
 *
 * <p><b>Patron de conception : Stratégie</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet exige que l'architecture permette de remplacer un fournisseur de modèle par
 *       un autre, et interdit que l'architecture dépende d'un modèle particulier. Comment
 *       interroger « un modèle » sans nommer Ollama, Mistral ou DeepSeek dans le métier ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une interface à deux méthodes, sans rien qui trahisse un fournisseur : ni URL, ni
 *       en-tête HTTP, ni format JSON propre à une API. Chaque fournisseur a sa classe, et le
 *       reste du projet ne connaît que cette interface.</dd>
 *   <dt>Remarques</dt>
 *   <dd>C'est aussi le point d'accroche des décorateurs : {@link RetryingLlmProvider} et
 *       {@link CountingLlmProvider} implémentent cette même interface et en enveloppent une
 *       autre. La résilience et la traçabilité s'ajoutent donc sans qu'aucun fournisseur ne
 *       les réimplémente.</dd>
 * </dl>
 *
 * <p><b>Sécurité</b> — deux règles non négociables pour toute implémentation :
 * <ul>
 *   <li>le modèle ne dispose d'<b>aucun outil</b> : pas d'accès au shell, au disque ni au
 *       réseau. La seule chose qu'il puisse faire est renvoyer du texte. Une injection de
 *       prompt réussie n'obtient donc rien de plus qu'une mauvaise note ;
 *   <li>aucune clé ni jeton n'est écrit dans le code. Une implémentation qui en aurait besoin
 *       les lit dans l'environnement, et ne les journalise jamais.
 * </ul>
 *
 * <p>Répondre à la question « comment remplacer votre LLM ? » : écrire une classe qui
 * implémente cette interface, et la nommer dans {@code EvaluationServiceFactory}. Rien
 * d'autre ne bouge.
 */
public interface LlmProvider {

    /**
     * Interroge le modèle.
     *
     * @param request la requête
     * @return la réponse brute, à valider avant usage
     * @throws LlmException si l'appel échoue
     */
    LlmResponse ask(LlmRequest request);

    /** Nom du modèle interrogé, tel qu'il doit figurer dans le rapport. */
    String modelName();

    /**
     * Vrai si le fournisseur interroge réellement un modèle.
     *
     * <p>Permet au rapport de dire « hors ligne » plutôt que d'afficher un nom de modèle qui
     * n'a jamais été appelé. Une évaluation sans LLM reste une évaluation valide — les
     * critères déterministes fonctionnent — mais le lecteur doit le savoir.
     */
    default boolean isLive() {
        return true;
    }
}
