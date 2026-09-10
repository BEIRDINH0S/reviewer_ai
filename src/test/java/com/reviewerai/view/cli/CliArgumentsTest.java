package com.reviewerai.view.cli;

import com.reviewerai.config.ServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests de la lecture des arguments de la ligne de commande. */
class CliArgumentsTest {

    @Test
    @DisplayName("le projet à évaluer est lu")
    void readsProject() {
        var config = CliArguments.parse(new String[]{"--project", "/tmp/demo"});

        assertEquals("/tmp/demo", config.projectSource().toString());
    }

    @Test
    @DisplayName("sans projet, la configuration est refusée")
    void projectIsRequired() {
        assertThrows(IllegalStateException.class, () -> CliArguments.parse(new String[]{"--offline"}));
    }

    @Test
    @DisplayName("les critères se donnent séparés par des virgules")
    void readsCriteriaList() {
        var config = CliArguments.parse(
                new String[]{"--project", ".", "--criteria", "tests, security ,architecture"});

        assertEquals(List.of("tests", "security", "architecture"), config.criterionIds());
    }

    @Test
    @DisplayName("les motifs d'exclusion sont cumulables")
    void excludePatternsAreRepeatable() {
        var config = CliArguments.parse(new String[]{
                "--project", ".", "--exclude", "**/generated/**", "--exclude", "**/*.min.js"});

        assertEquals(2, config.excludePatterns().size());
    }

    @Test
    @DisplayName("une option inconnue est refusée")
    void unknownOptionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--project", ".", "--inconnue"}));
    }

    @Test
    @DisplayName("une option attendant une valeur sans valeur est refusée")
    void missingValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--project"}));
    }

    @Test
    @DisplayName("une valeur numérique mal formée est refusée")
    void malformedNumberIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CliArguments.parse(new String[]{"--project", ".", "--max-tokens", "beaucoup"}));
    }

    @Test
    @DisplayName("les drapeaux sont reconnus")
    void flagsAreRecognised() {
        String[] args = {"--serve", "--offline", "--list-criteria"};

        assertTrue(CliArguments.wantsServe(args));
        assertTrue(CliArguments.isOffline(args));
        assertTrue(CliArguments.wantsCriteriaList(args));
        assertFalse(CliArguments.wantsHelp(args));
    }

    @Test
    @DisplayName("le rapport est un .tex par défaut, un .md si le format est markdown")
    void reportFileFollowsFormat() {
        assertEquals("evaluation.tex", CliArguments.reportFile(new String[]{"--project", "."})
                .getFileName().toString());
        assertEquals("evaluation.md",
                CliArguments.reportFile(new String[]{"--project", ".", "--format", "markdown"})
                        .getFileName().toString());
    }

    @Test
    @DisplayName("--out l'emporte sur le format")
    void explicitOutputWins() {
        assertEquals("mon-rapport.tex",
                CliArguments.reportFile(new String[]{"--out", "mon-rapport.tex", "--format", "markdown"})
                        .getFileName().toString());
    }

    @Test
    @DisplayName("le serveur écoute en local par défaut")
    void serverIsLocalByDefault() {
        var server = CliArguments.parseServer(new String[]{"--serve"});

        assertEquals(ServerConfig.LOOPBACK, server.bindAddress());
        assertEquals(8080, server.port());
    }

    @Test
    @DisplayName("la lecture du serveur ignore les options de l'évaluation")
    void serverParsingSkipsEvaluationOptions() {
        var server = CliArguments.parseServer(new String[]{
                "--serve", "--criteria", "tests", "--project", "/tmp", "--port", "9999"});

        assertEquals(9999, server.port());
    }

    @Test
    @DisplayName("l'aide mentionne les trois provenances de projet")
    void usageMentionsEverySource() {
        String usage = CliArguments.usage();

        assertTrue(usage.contains("répertoire"));
        assertTrue(usage.contains("git"));
        assertTrue(usage.contains(".zip"));
    }
}
