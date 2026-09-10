package com.reviewerai.project;

import com.reviewerai.model.FileKind;
import com.reviewerai.model.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du chargement de projet.
 *
 * <p>Tout se passe dans un {@code @TempDir} : ces tests ne dépendent d'aucun projet
 * préexistant sur la machine, ni d'un accès réseau.
 */
class ProjectLoaderTest {

    @TempDir
    Path tempDir;

    private Path sampleProject() throws IOException {
        Path root = tempDir.resolve("mon-projet");
        Files.createDirectories(root.resolve("src/main/java/com/x"));
        Files.createDirectories(root.resolve("src/test/java/com/x"));
        Files.writeString(root.resolve("src/main/java/com/x/A.java"), "class A {\n  void run() {}\n}\n");
        Files.writeString(root.resolve("src/test/java/com/x/ATest.java"), "class ATest {}\n");
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        Files.writeString(root.resolve("README.md"), "# Mon projet\n");
        return root;
    }

    @Test
    @DisplayName("un répertoire est chargé et ses fichiers classés")
    void loadsDirectoryAndClassifiesFiles() throws IOException {
        var snapshot = DirectoryProjectLoader.standard().load(sampleProject(), FileSelector.all());

        assertEquals("mon-projet", snapshot.project().name());
        assertEquals(SourceKind.DIRECTORY, snapshot.project().origin());
        assertEquals(1, snapshot.ofKind(FileKind.JAVA_MAIN).size());
        assertEquals(1, snapshot.ofKind(FileKind.JAVA_TEST).size());
        assertEquals(1, snapshot.ofKind(FileKind.BUILD).size());
    }

    @Test
    @DisplayName("les chemins sont relatifs et séparés par des barres obliques")
    void pathsAreRelativeAndNormalised() throws IOException {
        var snapshot = DirectoryProjectLoader.standard().load(sampleProject(), FileSelector.all());

        assertTrue(snapshot.byPath("src/main/java/com/x/A.java").isPresent(),
                "sous Windows, un séparateur antislash casserait la correspondance");
    }

    @Test
    @DisplayName("les règles de sélection sont appliquées au chargement")
    void selectorIsAppliedWhileLoading() throws IOException {
        var snapshot = DirectoryProjectLoader.standard()
                .load(sampleProject(), KindFileSelector.only(FileKind.JAVA_MAIN));

        assertEquals(1, snapshot.files().size());
    }

    @Test
    @DisplayName("un répertoire sans rien d'analysable est refusé")
    void emptyDirectoryIsRejected() throws IOException {
        Path empty = Files.createDirectory(tempDir.resolve("vide"));

        assertThrows(ProjectLoader.ProjectLoadException.class,
                () -> DirectoryProjectLoader.standard().load(empty, FileSelector.all()));
    }

    @Test
    @DisplayName("un chemin qui n'est pas un répertoire est refusé")
    void nonDirectoryIsRejected() throws IOException {
        Path file = Files.writeString(tempDir.resolve("fichier.txt"), "x");

        assertThrows(ProjectLoader.ProjectLoadException.class,
                () -> DirectoryProjectLoader.standard().load(file, FileSelector.all()));
    }

    @Test
    @DisplayName("une archive est extraite puis chargée")
    void loadsArchive() throws IOException {
        Path archive = tempDir.resolve("projet.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("projet/pom.xml"));
            zip.write("<project/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("projet/src/main/java/A.java"));
            zip.write("class A {}".getBytes());
            zip.closeEntry();
        }

        var snapshot = ArchiveProjectLoader.standard().load(archive, FileSelector.all());

        assertEquals(SourceKind.ARCHIVE, snapshot.project().origin());
        assertEquals(2, snapshot.files().size());
        assertTrue(snapshot.byPath("pom.xml").isPresent(),
                "le répertoire racine unique de l'archive doit être élidé");
    }

    @Test
    @DisplayName("une archive dont une entrée sort du répertoire cible est refusée")
    void rejectsZipSlip() throws IOException {
        Path archive = tempDir.resolve("piegee.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("../../evade.txt"));
            zip.write("piege".getBytes());
            zip.closeEntry();
        }

        assertThrows(ProjectLoader.ProjectLoadException.class,
                () -> ArchiveProjectLoader.standard().load(archive, FileSelector.all()));
        assertFalse(Files.exists(tempDir.getParent().resolve("evade.txt")),
                "aucun fichier ne doit avoir été écrit hors du répertoire d'extraction");
    }

    @Test
    @DisplayName("la fabrique reconnaît un répertoire et une archive")
    void factoryPicksTheRightLoader() throws IOException {
        var factory = ProjectLoaderFactory.standard();

        assertEquals(SourceKind.DIRECTORY, factory.originOf(sampleProject()));

        Path archive = tempDir.resolve("a.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("x.java"));
            zip.closeEntry();
        }
        assertEquals(SourceKind.ARCHIVE, factory.originOf(archive));
    }

    @Test
    @DisplayName("une source non reconnue donne un message explicite")
    void unknownSourceIsExplained() {
        var exception = assertThrows(ProjectLoader.ProjectLoadException.class,
                () -> ProjectLoaderFactory.standard().loaderFor(tempDir.resolve("absent.tar.gz")));

        assertTrue(exception.getMessage().contains("non reconnue"));
    }
}
