package com.reviewerai.report;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Compile un fichier {@code .tex} en PDF.
 *
 * <p>Fonctionnalité explicitement facultative dans le sujet — « intéressante, mais elle ne
 * doit pas compromettre la sécurité de l'application ». Cette réserve est la raison d'être de
 * l'interface : elle permet de <b>ne pas</b> compiler par défaut, et de rendre le choix
 * visible dans le câblage plutôt que caché dans une méthode.
 *
 * <p>Le risque est réel. Une compilation LaTeX exécute des commandes ; avec {@code \write18}
 * activé, elle exécute des commandes système. Le document à compiler contient du texte issu du
 * modèle, donc du code évalué. Deux protections, cumulatives :
 * <ul>
 *   <li>en amont, {@link LatexEscaper} neutralise toute commande dans le texte ;
 *   <li>en aval, la compilation tourne dans un conteneur jetable, sans réseau, en lecture
 *       seule sauf le répertoire de sortie, avec {@code \write18} désactivé.
 * </ul>
 *
 * <p>{@link #none()} est l'implémentation par défaut : elle ne compile rien. Un projet qui ne
 * livre pas de PDF reste conforme au sujet.
 *
 */
public interface PdfCompiler {

    /**
     * @param texFile le fichier LaTeX à compiler
     * @return le PDF produit, ou {@link Optional#empty()} si la compilation n'a pas eu lieu
     */
    Optional<Path> compile(Path texFile);

    /**
     * Ne compile rien.
     *
     * <p>Objet nul : le contrôleur n'a pas à tester la présence d'un compilateur avant chaque
     * rapport, et le comportement par défaut est le comportement sûr.
     */
    static PdfCompiler none() {
        return texFile -> Optional.empty();
    }
}
