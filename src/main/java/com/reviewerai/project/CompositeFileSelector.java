package com.reviewerai.project;

import com.reviewerai.model.ProjectFile;

import java.util.List;
import java.util.Objects;

/**
 * Applique plusieurs règles de sélection comme s'il n'y en avait qu'une.
 *
 * <p><b>Patron de conception : Composite</b> (structurel)
 * <dl>
 *   <dt>Problème traité</dt>
 *   <dd>Une analyse combine plusieurs règles : celles de la configuration, celles du critère,
 *       celles de sécurité. Comment les appliquer toutes sans que l'appelant ait à savoir
 *       combien il y en a ni dans quel ordre ?</dd>
 *   <dt>Solution</dt>
 *   <dd>Le composite implémente {@link FileSelector}, l'interface même des éléments qu'il
 *       regroupe. L'appelant manipule un sélecteur unique. Un composite pouvant en contenir un
 *       autre, on obtient un arbre de règles là où on n'attendait qu'une liste.</dd>
 *   <dt>Remarques</dt>
 *   <dd>Contrairement au Composite du cours, il n'y a pas de méthode {@code add} : la liste
 *       est fixée à la construction. Une règle de sélection qu'on peut modifier après coup
 *       rendrait une analyse non reproductible, ce que le sujet demande justement d'éviter.</dd>
 * </dl>
 *
 * <p>La combinaison est un ET : toutes les règles doivent accepter. C'est le comportement
 * attendu quand on empile des filtres — chacun ne peut que restreindre davantage.
 */
public final class CompositeFileSelector implements FileSelector {

    private final List<FileSelector> delegates;

    public CompositeFileSelector(List<FileSelector> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNull(delegates, "delegates"));
    }

    /** Combine les règles données ; sans argument, accepte tout. */
    public static FileSelector of(FileSelector... selectors) {
        return selectors.length == 0 ? FileSelector.all() : new CompositeFileSelector(List.of(selectors));
    }

    @Override
    public boolean accepts(ProjectFile file) {
        for (FileSelector delegate : delegates) {
            if (!delegate.accepts(file)) {
                return false; // une seule règle suffit à écarter le fichier
            }
        }
        return true;
    }

    /** Nombre de règles combinées, utile aux tests et à l'affichage de la configuration. */
    public int size() {
        return delegates.size();
    }
}
