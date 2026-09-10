package com.reviewerai.verify;

import com.reviewerai.model.CodeExcerpt;
import com.reviewerai.model.CodeLocation;
import com.reviewerai.model.EvaluationContext;
import com.reviewerai.model.FileKind;
import com.reviewerai.model.Finding;
import com.reviewerai.model.ProjectFile;
import com.reviewerai.model.ProjectRef;
import com.reviewerai.model.ProjectSnapshot;
import com.reviewerai.model.Severity;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests des règles qui écartent ce que le modèle a inventé. */
class VerifiersTest {

    private static final String SHOWN = "src/main/java/A.java";
    private static final String NOT_SHOWN = "src/main/java/B.java";

    private static ProjectSnapshot project() {
        var ref = ProjectRef.of("demo", Path.of("/tmp/demo"), SourceKind.DIRECTORY);
        return new ProjectSnapshot(ref, List.of(
                new ProjectFile(SHOWN, FileKind.JAVA_MAIN, 1000, 100),
                new ProjectFile(NOT_SHOWN, FileKind.JAVA_MAIN, 1000, 100)));
    }

    private static EvaluationContext context() {
        return new EvaluationContext("architecture", project().project(),
                List.of(CodeExcerpt.wholeFile(SHOWN, "class A {}", "test")), "", 100);
    }

    private static Finding finding(String file, int line, String title, double confidence) {
        return new Finding(new CodeLocation(file, "", line), Severity.MEDIUM, title, "explication", confidence);
    }

    @Test
    @DisplayName("un signalement citant un fichier inexistant est écarté")
    void unknownFileIsRejected() {
        var kept = new KnownLocationVerifier().verify(
                List.of(finding("src/main/java/Invente.java", 5, "x", 0.9)), context(), project());

        assertTrue(kept.isEmpty());
    }

    @Test
    @DisplayName("un signalement citant un fichier non montré au modèle est écarté")
    void fileOutsideContextIsRejected() {
        var kept = new KnownLocationVerifier().verify(
                List.of(finding(NOT_SHOWN, 5, "x", 0.9)), context(), project());

        assertTrue(kept.isEmpty(), "le modèle n'a pas vu ce fichier : il a extrapolé");
    }

    @Test
    @DisplayName("un signalement sans fichier est conservé")
    void projectWideFindingIsKept() {
        var kept = new KnownLocationVerifier().verify(
                List.of(Finding.projectWide(Severity.HIGH, "couplage", "explication", 0.9)),
                context(), project());

        assertEquals(1, kept.size());
    }

    @Test
    @DisplayName("une ligne hors du fichier est effacée, sans écarter le signalement")
    void outOfRangeLineIsClearedNotRejected() {
        var kept = new KnownLocationVerifier().verify(
                List.of(finding(SHOWN, 9999, "x", 0.9)), context(), project());

        assertEquals(1, kept.size(), "le problème peut être réel même si la ligne est fausse");
        assertFalse(kept.getFirst().location().hasLine());
    }

    @Test
    @DisplayName("les signalements en dessous du seuil de confiance sont écartés")
    void lowConfidenceIsRejected() {
        var kept = new ConfidenceVerifier(0.5).verify(
                List.of(finding(SHOWN, 1, "sûr", 0.9), finding(SHOWN, 2, "douteux", 0.2)),
                context(), project());

        assertEquals(1, kept.size());
        assertEquals("sûr", kept.getFirst().title());
    }

    @Test
    @DisplayName("deux signalements identiques au même endroit sont fusionnés")
    void duplicatesAreRemoved() {
        var kept = new DuplicateVerifier().verify(
                List.of(finding(SHOWN, 42, "Ressource non fermée", 0.9),
                        finding(SHOWN, 42, "ressource non fermée !", 0.8)),
                context(), project());

        assertEquals(1, kept.size(), "la comparaison doit ignorer la casse et la ponctuation");
    }

    @Test
    @DisplayName("le même titre à deux endroits différents est conservé deux fois")
    void sameTitleAtDifferentPlacesIsKept() {
        var kept = new DuplicateVerifier().verify(
                List.of(finding(SHOWN, 10, "Ressource non fermée", 0.9),
                        finding(SHOWN, 80, "Ressource non fermée", 0.9)),
                context(), project());

        assertEquals(2, kept.size());
    }

    @Test
    @DisplayName("le composite enchaîne les règles dans l'ordre")
    void compositeChainsRules() {
        var verifier = CompositeFindingVerifier.of(
                new KnownLocationVerifier(), new DuplicateVerifier(), new ConfidenceVerifier(0.5));

        var kept = verifier.verify(List.of(
                finding(SHOWN, 10, "vrai problème", 0.9),
                finding(SHOWN, 10, "vrai problème", 0.9),
                finding(SHOWN, 20, "peu sûr", 0.1),
                finding("inconnu.java", 30, "inventé", 1.0)), context(), project());

        assertEquals(1, kept.size());
        assertEquals("vrai problème", kept.getFirst().title());
    }

    @Test
    @DisplayName("un composite sans signalement à filtrer ne fait rien")
    void emptyInputIsHandled() {
        var verifier = CompositeFindingVerifier.of(new DuplicateVerifier());

        assertTrue(verifier.verify(List.of(), context(), project()).isEmpty());
    }
}
