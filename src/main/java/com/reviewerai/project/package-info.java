/**
 * Brique Chargement — importe le projet à évaluer et en dresse l'inventaire.
 *
 * <p>Répond à deux exigences du sujet : importer depuis une archive, un répertoire ou un dépôt
 * git, et permettre de définir des règles d'inclusion ou d'exclusion de fichiers.
 *
 * <p>Deux stratégies s'y croisent, et c'est ce qui rend le package extensible dans les deux
 * directions :
 * <ul>
 *   <li>{@link com.reviewerai.project.ProjectLoader} — d'où vient le projet. Une
 *       implémentation par provenance, choisies par
 *       {@link com.reviewerai.project.ProjectLoaderFactory} ;
 *   <li>{@link com.reviewerai.project.FileSelector} — ce qu'on garde. Une implémentation par
 *       règle, combinées par {@link com.reviewerai.project.CompositeFileSelector}.
 * </ul>
 *
 * <p>{@link com.reviewerai.project.FileClassifier} tranche la question « qu'est-ce que ce
 * fichier ? » une fois pour toutes, à partir du chemin seul.
 *
 * <p><b>Sécurité</b> : c'est la frontière du système. Tout ce qui entre ici vient d'un tiers.
 * Aucun code n'est exécuté, aucun système de construction n'est lancé, les liens symboliques
 * ne sont pas suivis, et l'extraction d'archive refuse les chemins qui s'échappent comme les
 * contenus démesurés.
 *
 */
package com.reviewerai.project;
