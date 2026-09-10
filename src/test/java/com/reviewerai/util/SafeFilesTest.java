package com.reviewerai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Tests de la lecture défensive du dépôt analysé. */
class SafeFilesTest {

    @Test
    @DisplayName("le chemin relatif utilise des barres obliques, y compris sous Windows")
    void repoRelativePathUsesForwardSlashes(@TempDir Path root) {
        Path file = root.resolve("src").resolve("main").resolve("A.java");

        assertEquals("src/main/A.java", SafeFiles.toRepoRelative(root, file));
    }

    @Test
    @DisplayName("un fichier hors du dépôt est refusé")
    void rejectsPathOutsideRepo(@TempDir Path root) {
        assertFalse(SafeFiles.isWithin(root, root.resolve("..").resolve("ailleurs.java")));
        assertTrue(SafeFiles.isWithin(root, root.resolve("dedans.java")));
    }

    @Test
    @DisplayName("les fichiers trop gros sont ignorés")
    void skipsOversizedFiles(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("Gros.java"), "x".repeat(5000));
        Files.writeString(root.resolve("Petit.java"), "class A {}");

        var sources = SafeFiles.javaSources(root, 1000);

        assertEquals(1, sources.size());
        assertEquals("Petit.java", sources.getFirst().getFileName().toString());
    }

    @Test
    @DisplayName("les répertoires de build sont ignorés")
    void skipsBuildDirectories(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("target"));
        Files.writeString(root.resolve("target").resolve("Genere.java"), "class G {}");
        Files.writeString(root.resolve("Source.java"), "class S {}");

        var sources = SafeFiles.javaSources(root, 100_000);

        assertEquals(1, sources.size());
        assertEquals("Source.java", sources.getFirst().getFileName().toString());
    }

    @Test
    @DisplayName("readText refuse un fichier hors du dépôt")
    void readTextRejectsEscapingPath(@TempDir Path root, @TempDir Path ailleurs) throws IOException {
        Path secret = ailleurs.resolve("secret.txt");
        Files.writeString(secret, "confidentiel");

        assertTrue(SafeFiles.readText(root, secret, 100_000).isEmpty());
    }

    @Test
    @DisplayName("readText lit un fichier normal du dépôt")
    void readTextReadsRepoFile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("A.java"), "class A {}");

        assertEquals("class A {}", SafeFiles.readText(root, root.resolve("A.java"), 100_000).orElseThrow());
    }
}
