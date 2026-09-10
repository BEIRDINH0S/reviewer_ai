package com.reviewerai.diff;

import com.reviewerai.model.ChangedMethod;

import java.nio.file.Path;
import java.util.List;

/**
 * Brique Diff — trouve les méthodes modifiées entre deux commits.
 *
 *
 * <p>Contrat : lecture seule du repo. Aucune commande git externe, aucun build,
 * pas d'initialisation des submodules (cf. section sécurité).
 */
public interface DiffAnalyzer {

    /**
     * @param repo       racine du repo git à analyser
     * @param baseCommit commit de départ (ex. la base de la PR)
     * @param headCommit commit d'arrivée (ex. la tête de la PR)
     * @return les méthodes ajoutées ou modifiées, sans doublon
     * @throws DiffException si le repo ou les commits sont illisibles
     */
    List<ChangedMethod> findChangedMethods(Path repo, String baseCommit, String headCommit);

    /** Erreur non récupérable de la brique Diff. */
    class DiffException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DiffException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
