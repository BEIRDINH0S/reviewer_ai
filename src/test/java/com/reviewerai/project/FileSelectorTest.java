package com.reviewerai.project;

import com.reviewerai.model.FileKind;
import com.reviewerai.model.ProjectFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests des règles d'inclusion et d'exclusion de fichiers. */
class FileSelectorTest {

    private static ProjectFile file(String path, FileKind kind) {
        return new ProjectFile(path, kind, 100, 10);
    }

    private static final ProjectFile MAIN = file("src/main/java/com/x/A.java", FileKind.JAVA_MAIN);
    private static final ProjectFile TEST = file("src/test/java/com/x/ATest.java", FileKind.JAVA_TEST);
    private static final ProjectFile GENERATED = file("src/main/java/generated/G.java", FileKind.JAVA_MAIN);

    @Test
    @DisplayName("la règle par nature ne retient que les natures demandées")
    void kindSelectorFiltersByKind() {
        var selector = KindFileSelector.only(FileKind.JAVA_TEST);

        assertTrue(selector.accepts(TEST));
        assertFalse(selector.accepts(MAIN));
    }

    @Test
    @DisplayName("sans motif d'inclusion, tout est retenu")
    void emptyIncludesMeansEverything() {
        var selector = GlobFileSelector.of(List.of(), List.of());

        assertTrue(selector.accepts(MAIN));
        assertTrue(selector.accepts(TEST));
    }

    @Test
    @DisplayName("un motif d'exclusion écarte les fichiers correspondants")
    void excludePatternRemovesMatches() {
        var selector = GlobFileSelector.excluding("**/generated/**");

        assertFalse(selector.accepts(GENERATED));
        assertTrue(selector.accepts(MAIN));
    }

    @Test
    @DisplayName("l'exclusion l'emporte sur l'inclusion")
    void excludeWinsOverInclude() {
        var selector = GlobFileSelector.of(List.of("**/*.java"), List.of("**/generated/**"));

        assertTrue(selector.accepts(MAIN), "inclus par le motif Java");
        assertFalse(selector.accepts(GENERATED), "exclu malgré le motif Java");
    }

    @Test
    @DisplayName("le composite n'accepte que ce que toutes les règles acceptent")
    void compositeRequiresEveryRule() {
        var selector = CompositeFileSelector.of(
                KindFileSelector.only(FileKind.JAVA_MAIN),
                GlobFileSelector.excluding("**/generated/**"));

        assertTrue(selector.accepts(MAIN));
        assertFalse(selector.accepts(GENERATED), "écarté par la seconde règle");
        assertFalse(selector.accepts(TEST), "écarté par la première règle");
    }

    @Test
    @DisplayName("un composite sans règle accepte tout")
    void emptyCompositeAcceptsEverything() {
        assertTrue(CompositeFileSelector.of().accepts(MAIN));
    }

    @Test
    @DisplayName("la règle « textuel seulement » écarte les binaires")
    void textualOnlySkipsBinaries() {
        var selector = FileSelector.textualOnly();

        assertTrue(selector.accepts(MAIN));
        assertFalse(selector.accepts(file("logo.png", FileKind.OTHER)));
    }

    @Test
    @DisplayName("les combinateurs et, ou et négation fonctionnent")
    void combinatorsWork() {
        var javaMain = KindFileSelector.only(FileKind.JAVA_MAIN);
        var javaTest = KindFileSelector.only(FileKind.JAVA_TEST);

        assertTrue(javaMain.or(javaTest).accepts(TEST));
        assertFalse(javaMain.and(javaTest).accepts(MAIN));
        assertTrue(javaMain.negate().accepts(TEST));
    }
}
