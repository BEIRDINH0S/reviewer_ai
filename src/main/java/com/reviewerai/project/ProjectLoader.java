package com.reviewerai.project;

import com.reviewerai.model.ProjectSnapshot;

import java.nio.file.Path;

/**
 * Charge un projet à évaluer et en dresse l'inventaire.
 *
 * <p><b>Patron de conception : Stratégie</b> (comportemental)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Le sujet demande d'importer un projet depuis une archive, un répertoire local ou un
 *       dépôt git. Les trois donnent le même résultat — un {@link ProjectSnapshot} — mais par
 *       des chemins totalement différents. Comment ajouter demain un quatrième mode (une URL,
 *       un dépôt distant) sans toucher au reste ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Une interface commune, une implémentation par provenance, et
 *       {@link ProjectLoaderFactory} qui choisit la bonne. Le service ne connaît que
 *       l'interface : il ignore d'où vient le projet qu'il évalue.</dd>
 *   <dt>Remarques</dt>
 *   <dd>{@link #supports} permet à la fabrique d'interroger les implémentations plutôt que de
 *       contenir une cascade de {@code if} sur les extensions de fichier. Ajouter une
 *       provenance reste ainsi une pure addition.</dd>
 * </dl>
 *
 * <p><b>Sécurité</b> : le projet chargé est du code non fiable. Aucune implémentation ne doit
 * l'exécuter, ni lancer son système de construction, ni suivre ses liens symboliques. Tout
 * accès disque passe par {@code SafeFiles}.
 */
public interface ProjectLoader {

    /**
     * @param source la source, telle que l'utilisateur l'a désignée
     * @return {@code true} si cette implémentation sait la traiter
     */
    boolean supports(Path source);

    /**
     * Charge le projet et dresse l'inventaire de ses fichiers.
     *
     * @param source   la source à charger
     * @param selector les fichiers à retenir dans l'inventaire
     * @return l'inventaire du projet
     * @throws ProjectLoadException si la source est illisible, absente ou refusée
     */
    ProjectSnapshot load(Path source, FileSelector selector);

    /** Échec de chargement, sans détail technique exposé à l'utilisateur. */
    class ProjectLoadException extends RuntimeException {
        public ProjectLoadException(String message) {
            super(message);
        }

        public ProjectLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
