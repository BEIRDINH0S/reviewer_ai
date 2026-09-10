package com.reviewerai.project;

import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;

/**
 * Décide si un fichier entre dans le périmètre d'une analyse.
 *
 * <p><b>Patron de conception : Stratégie</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet exige que l'architecture permette de définir des règles d'inclusion et
 *       d'exclusion de fichiers, sans figer lesquelles. Comment laisser la règle varier —
 *       selon le critère évalué, selon la configuration, selon ce que coche l'utilisateur —
 *       sans que le chargement du projet ni les critères aient à connaître ces variantes ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une interface à une seule méthode. Chaque règle est une classe indépendante
 *       ({@link GlobFileSelector}, {@link KindFileSelector}), et
 *       {@link CompositeFileSelector} les combine. L'appelant ne manipule qu'un
 *       {@code FileSelector}, qu'il y en ait un ou dix derrière.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Le cours présente la stratégie comme une classe abstraite portant un attribut
 *       {@code context}. Ici les règles sont sans état : tout ce dont elles ont besoin arrive
 *       en paramètre, ce qui les rend utilisables en parallèle sans précaution. Ajouter une
 *       règle ne modifie aucune classe existante (principe ouvert/fermé).</dd>
 * </dl>
 *
 * <p>Deux usages distincts, volontairement servis par la même interface :
 * <ul>
 *   <li>au <b>chargement</b>, pour décider ce qui entre dans l'inventaire du projet ;
 *   <li>par <b>critère</b>, pour décider ce qui part au modèle — un critère « qualité des
 *       tests » ne veut voir que les tests.
 * </ul>
 */
@FunctionalInterface
public interface FileSelector {

    /**
     * @param file le fichier candidat
     * @return {@code true} s'il doit être retenu
     */
    boolean accepts(ProjectFile file);

    /** Accepte tout. Objet nul, pour éviter de tester {@code null} chez l'appelant. */
    static FileSelector all() {
        return file -> true;
    }

    /** N'accepte rien. Utile dans les tests, et comme élément neutre d'une composition. */
    static FileSelector none() {
        return file -> false;
    }

    /** N'accepte que les fichiers dont le contenu a du sens pour un humain. */
    static FileSelector textualOnly() {
        return file -> file.kind().isTextual();
    }

    /** N'accepte que les natures données. */
    static FileSelector kinds(FileKind... kinds) {
        return KindFileSelector.only(kinds);
    }

    /** Combinaison ET avec une autre règle : les deux doivent accepter. */
    default FileSelector and(FileSelector other) {
        return file -> this.accepts(file) && other.accepts(file);
    }

    /** Combinaison OU avec une autre règle : l'une des deux suffit. */
    default FileSelector or(FileSelector other) {
        return file -> this.accepts(file) || other.accepts(file);
    }

    /** Inverse la règle : ce qui était accepté est rejeté, et réciproquement. */
    default FileSelector negate() {
        return file -> !this.accepts(file);
    }
}
