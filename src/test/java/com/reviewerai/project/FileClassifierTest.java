package com.reviewerai.project;

import com.reviewerai.model.FileKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests du classement des fichiers par leur seul chemin. */
class FileClassifierTest {

    @Test
    @DisplayName("un source sous src/main/java est du Java de production")
    void mainJavaIsProduction() {
        assertEquals(FileKind.JAVA_MAIN, FileClassifier.classify("src/main/java/com/x/A.java"));
    }

    @Test
    @DisplayName("un source sous src/test/java est un test")
    void testJavaIsTest() {
        assertEquals(FileKind.JAVA_TEST, FileClassifier.classify("src/test/java/com/x/ATest.java"));
    }

    @Test
    @DisplayName("un fichier nommé ...Test.java est un test même hors de src/test")
    void namingAloneIdentifiesATest() {
        assertEquals(FileKind.JAVA_TEST, FileClassifier.classify("autre/AppTest.java"));
    }

    @Test
    @DisplayName("les descripteurs Maven et Gradle sont reconnus")
    void buildDescriptorsAreRecognised() {
        assertEquals(FileKind.BUILD, FileClassifier.classify("pom.xml"));
        assertEquals(FileKind.BUILD, FileClassifier.classify("build.gradle.kts"));
    }

    @Test
    @DisplayName("un Dockerfile est reconnu, avec ou sans suffixe")
    void dockerFilesAreRecognised() {
        assertEquals(FileKind.DOCKER, FileClassifier.classify("Dockerfile"));
        assertEquals(FileKind.DOCKER, FileClassifier.classify("docker/Dockerfile.ci"));
        assertEquals(FileKind.DOCKER, FileClassifier.classify("docker-compose.yml"));
    }

    @Test
    @DisplayName("les scripts sont reconnus quel que soit le système")
    void scriptsAreRecognised() {
        assertEquals(FileKind.SCRIPT, FileClassifier.classify("docker/mvn.sh"));
        assertEquals(FileKind.SCRIPT, FileClassifier.classify("build.bat"));
    }

    @Test
    @DisplayName("la documentation est reconnue")
    void documentationIsRecognised() {
        assertEquals(FileKind.DOCUMENTATION, FileClassifier.classify("README.md"));
        assertEquals(FileKind.DOCUMENTATION, FileClassifier.classify("docs/rapport.tex"));
    }

    @Test
    @DisplayName("pom.xml est un descripteur de construction, pas une configuration")
    void buildWinsOverConfiguration() {
        // pom.xml se termine par .xml : sans priorité explicite, il serait classé CONFIG.
        assertEquals(FileKind.BUILD, FileClassifier.classify("pom.xml"));
    }

    @Test
    @DisplayName("un chemin vide ou inconnu ne fait pas échouer le classement")
    void unknownPathsAreTolerated() {
        assertEquals(FileKind.OTHER, FileClassifier.classify(""));
        assertEquals(FileKind.OTHER, FileClassifier.classify(null));
        assertEquals(FileKind.OTHER, FileClassifier.classify("logo.png"));
    }
}
