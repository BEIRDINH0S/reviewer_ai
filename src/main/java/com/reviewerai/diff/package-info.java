/**
 * Brique Diff — repère les méthodes modifiées entre deux commits.
 *
 * <p>Première étape de l'analyse : elle détermine ce qui sera relu. Le diff git donne des
 * plages de lignes ; on les croise avec les déclarations de méthodes trouvées par le parseur
 * pour remonter au niveau de la méthode.
 *
 */
package com.reviewerai.diff;
